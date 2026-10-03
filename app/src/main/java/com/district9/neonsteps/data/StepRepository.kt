package com.district9.neonsteps.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.district9.neonsteps.util.CityClock
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** Where today's steps come from; shown in the UI so the user knows how reliable the count is. */
enum class SensorMode { NONE, STEP_COUNTER, STEP_DETECTOR, ACCELEROMETER }

enum class RainMode { AUTO, DRIZZLE, RAIN, DOWNPOUR }

data class DayEntry(val date: LocalDate, val steps: Int, val goal: Int = 0) {
    val metGoal: Boolean get() = goal in 1..steps
}

/**
 * Single source of truth for step data, shared in-process by the foreground service
 * (which feeds it sensor readings) and the activity (which observes it).
 *
 * All methods must be called on the main thread: sensor callbacks are delivered there
 * because listeners are registered without a handler.
 */
class StepRepository internal constructor(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val hourNow: () -> Int = { CityClock.now().hour },
) {
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory working copy; persisted with a short debounce so walking doesn't hammer disk.
    private var day: LocalDate = today()
    private var stepsToday: Int = prefs.getInt(dayKey(day), 0)
    private var lastCounter: Long = prefs.getLong(KEY_LAST_COUNTER, -1L)
    private var lastBoot: Int = prefs.getInt(KEY_LAST_BOOT, -1)
    private var dirty = false

    /** Seconds walked today at a good pace (≥ [BRISK_CADENCE] steps/min), for brisk-walk missions. */
    private var briskSeconds = prefs.getFloat(briskKey(day), 0f)
    private var lastStepAt = 0L

    /** Recent step increments, used for live cadence (steps / minute). */
    private val recentTimes = LongArray(CADENCE_SAMPLES)
    private val recentSteps = IntArray(CADENCE_SAMPLES)
    private var recentHead = 0
    private var recentCount = 0

    var sensorMode: SensorMode = SensorMode.NONE
        set(value) {
            if (field != value) {
                field = value
                notifyListeners()
            }
        }

    var goal: Int
        get() = prefs.getInt(KEY_GOAL, DEFAULT_GOAL)
        set(value) {
            // Each day remembers the goal it was played against, so streaks survive goal changes.
            prefs.edit().putInt(KEY_GOAL, value).putInt(goalKey(today()), value).apply()
            notifyListeners()
        }

    /** Height in cm, or 0 when not set. Drives the stride length. */
    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT, 0)
        set(value) {
            prefs.edit().putInt(KEY_HEIGHT, value).apply()
            notifyListeners()
        }

    /** Weight in kg, or 0 when not set. Drives the energy per step. */
    var weightKg: Int
        get() = prefs.getInt(KEY_WEIGHT, 0)
        set(value) {
            prefs.edit().putInt(KEY_WEIGHT, value).apply()
            notifyListeners()
        }

    /** Walking stride: about 41.4 % of height, or an average stride when height isn't set. */
    val strideMeters: Double
        get() = heightCm.takeIf { it > 0 }?.let { it * STRIDE_PER_CM } ?: DEFAULT_STRIDE_METERS

    /** Gross walking cost of ~0.75 kcal per kg per km, spread over the stride. */
    val kcalPerStep: Double
        get() = KCAL_PER_KG_KM * (weightKg.takeIf { it > 0 } ?: DEFAULT_WEIGHT_KG) * strideMeters / 1000.0

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
            notifyListeners()
        }

    var rainMode: RainMode
        get() = RainMode.entries.getOrElse(prefs.getInt(KEY_RAIN, 0)) { RainMode.AUTO }
        set(value) {
            prefs.edit().putInt(KEY_RAIN, value.ordinal).apply()
            notifyListeners()
        }

    fun interface Listener {
        fun onStepsChanged()
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Steps for the current calendar day; rolls over at local midnight even without new readings. */
    fun stepsToday(): Int {
        rollOverIfNeeded()
        return stepsToday
    }

    /**
     * Feed a reading from [android.hardware.Sensor.TYPE_STEP_COUNTER], which reports the
     * cumulative steps since the device booted.
     *
     * @param bootCount value of `Settings.Global.BOOT_COUNT`, or -1 if unavailable. A change
     *   means the hardware counter restarted from zero, so the whole reading is new steps.
     */
    fun onStepCounter(counter: Long, bootCount: Int) {
        val delta = when {
            lastCounter < 0 -> 0L // first reading ever: only establishes the baseline
            bootCount >= 0 && lastBoot >= 0 && bootCount != lastBoot -> counter
            counter < lastCounter -> counter // counter reset without a boot-count change
            else -> counter - lastCounter
        }
        lastCounter = counter
        lastBoot = bootCount
        dirty = true
        if (delta > 0) {
            addSteps(delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        } else {
            schedulePersist()
        }
    }

    /** Add steps detected one by one (step detector or accelerometer fallback). */
    fun addSteps(count: Int) {
        if (count <= 0) return
        rollOverIfNeeded()
        stepsToday += count
        recordCadenceSample(count)
        val now = clock()
        if (lastStepAt > 0L && cadence() >= BRISK_CADENCE) {
            briskSeconds += ((now - lastStepAt) / 1000f).coerceAtMost(10f)
        }
        lastStepAt = now
        dirty = true
        schedulePersist()
        notifyListeners()
    }

    /** Live walking cadence in steps per minute, decaying to 0 a few seconds after the last step. */
    fun cadence(): Int {
        val now = clock()
        var steps = 0
        var oldest = now
        var oldestSteps = 0
        for (i in 0 until recentCount) {
            val idx = (recentHead - 1 - i + CADENCE_SAMPLES) % CADENCE_SAMPLES
            val t = recentTimes[idx]
            if (now - t > CADENCE_WINDOW_MS) break
            steps += recentSteps[idx]
            oldest = t
            oldestSteps = recentSteps[idx]
        }
        // The oldest sample's steps happened before its timestamp, so they fall outside the span.
        steps -= oldestSteps
        if (steps <= 0) return 0
        val newest = recentTimes[(recentHead - 1 + CADENCE_SAMPLES) % CADENCE_SAMPLES]
        if (now - newest > CADENCE_IDLE_MS) return 0
        // Never divide by less than 10 s so a single batched reading doesn't spike the value.
        val spanMs = (now - oldest).coerceAtLeast(10_000L)
        return (steps * 60_000L / spanMs).toInt().coerceAtMost(250)
    }

    /** Today's goal is met; it stays met until midnight unless the goal is raised. */
    fun goalReachedToday(): Boolean = stepsToday() >= goal

    /** Whether today's "goal reached" notification has already been sent (once a day). */
    fun goalNotifiedToday(): Boolean = prefs.getString(KEY_GOAL_NOTIFIED, null) == today().toString()

    fun markGoalNotified() {
        prefs.edit().putString(KEY_GOAL_NOTIFIED, today().toString()).apply()
    }

    /** Whether the in-app celebration has played today (it replays on the next open if missed). */
    fun goalCelebratedToday(): Boolean = prefs.getString(KEY_GOAL_CELEBRATED, null) == today().toString()

    fun markGoalCelebrated() {
        prefs.edit().putString(KEY_GOAL_CELEBRATED, today().toString()).apply()
    }

    /** Easter egg: someone fixed the HOTEL's "L" today. It breaks again at midnight. */
    fun hotelFixedToday(): Boolean = prefs.getString(KEY_HOTEL_FIXED, null) == today().toString()

    fun hotelEverFixed(): Boolean = prefs.contains(KEY_HOTEL_FIXED)

    fun markHotelFixed() {
        prefs.edit().putString(KEY_HOTEL_FIXED, today().toString()).apply()
        notifyListeners()
    }

    // --- Daily missions --------------------------------------------------------------------

    /** Today's mission, generated (and remembered) on first ask. */
    fun mission(): Mission {
        rollOverIfNeeded()
        if (prefs.getString(KEY_MISSION_DAY, null) == day.toString()) {
            runCatching {
                return Mission(
                    day,
                    MissionKind.valueOf(prefs.getString(KEY_MISSION_KIND, null)!!),
                    prefs.getInt(KEY_MISSION_TARGET, 0),
                    prefs.getInt(KEY_MISSION_DEADLINE, 24),
                    prefs.getString(KEY_MISSION_REWARD, null)!!,
                )
            }
        }
        val m = generateMission(day)
        prefs.edit()
            .putString(KEY_MISSION_DAY, day.toString())
            .putString(KEY_MISSION_KIND, m.kind.name)
            .putInt(KEY_MISSION_TARGET, m.target)
            .putInt(KEY_MISSION_DEADLINE, m.deadlineHour)
            .putString(KEY_MISSION_REWARD, m.reward)
            .putString(KEY_MISSION_STATE, MissionState.ACTIVE.name)
            .apply()
        return m
    }

    /** Different every day (never the same kind twice running), sized to the user's goal and yesterday. */
    private fun generateMission(d: LocalDate): Mission {
        val rnd = java.util.Random(d.toEpochDay() * 7919 + 61)
        val yesterday = prefs.getInt(dayKey(d.minusDays(1)), 0)
        val lastKind = prefs.getString(KEY_MISSION_KIND, null)
        val kinds = MissionKind.entries.filter { k ->
            k.name != lastKind && !(k == MissionKind.BEAT_YESTERDAY && yesterday < 2_000)
        }
        val kind = kinds[rnd.nextInt(kinds.size)]
        val g = goal
        fun round500(x: Double) = (Math.round(x / 500.0) * 500).toInt().coerceAtLeast(500)
        val (target, deadline) = when (kind) {
            MissionKind.STEPS_BY_HOUR -> round500(g * 0.5) to 14
            MissionKind.MORNING_STEPS -> 1_500 to 10
            MissionKind.BEAT_YESTERDAY -> yesterday + 1 to 24
            MissionKind.BRISK_MINUTES -> 15 to 24
            MissionKind.DISTANCE -> (Math.round(g * strideMeters * 0.9 / 500.0) * 500).toInt().coerceAtLeast(1_000) to 24
            MissionKind.GOAL_BY_HOUR -> g to 20
            MissionKind.STRETCH -> round500(g * 1.25) to 24
        }
        val unlocked = unlockedStyles()
        val reward = Cosmetics.all.firstOrNull { it.id !in unlocked }?.id ?: Cosmetics.FIREWORKS
        return Mission(d, kind, target, deadline, reward)
    }

    /** Progress toward [m], in its own units (steps, minutes or metres). */
    fun missionProgress(m: Mission): Int = when (m.kind) {
        MissionKind.BRISK_MINUTES -> (briskSeconds / 60f).toInt()
        MissionKind.DISTANCE -> (stepsToday * strideMeters).toInt()
        else -> stepsToday
    }

    /**
     * Settles today's mission: done once the target is reached (unlocking and wearing its
     * reward), failed once its deadline passes. Safe to call often, from anywhere.
     */
    fun evaluateMission(): MissionState {
        val m = mission()
        val stored = runCatching { MissionState.valueOf(prefs.getString(KEY_MISSION_STATE, null)!!) }.getOrDefault(MissionState.ACTIVE)
        if (stored != MissionState.ACTIVE) return stored
        val state = when {
            missionProgress(m) >= m.target -> MissionState.DONE
            hourNow() >= m.deadlineHour -> MissionState.FAILED
            else -> MissionState.ACTIVE
        }
        if (state != MissionState.ACTIVE) {
            val edit = prefs.edit().putString(KEY_MISSION_STATE, state.name)
            val item = Cosmetics.byId(m.reward)
            if (state == MissionState.DONE && item != null) {
                edit.putStringSet(KEY_UNLOCKED, unlockedStyles() + item.id).putString(styleKey(item.slot), item.id)
            }
            edit.apply()
            notifyListeners()
        }
        return state
    }

    fun missionAnnounced(): Boolean = prefs.getString(KEY_MISSION_ANNOUNCED, null) == today().toString()
    fun markMissionAnnounced() = prefs.edit().putString(KEY_MISSION_ANNOUNCED, today().toString()).apply()
    fun missionDoneNotified(): Boolean = prefs.getString(KEY_MISSION_NOTIFIED, null) == today().toString()
    fun markMissionDoneNotified() = prefs.edit().putString(KEY_MISSION_NOTIFIED, today().toString()).apply()
    fun missionCelebrated(): Boolean = prefs.getString(KEY_MISSION_CELEBRATED, null) == today().toString()
    fun markMissionCelebrated() = prefs.edit().putString(KEY_MISSION_CELEBRATED, today().toString()).apply()

    // --- Styles (mission rewards) -----------------------------------------------------------

    fun unlockedStyles(): Set<String> = prefs.getStringSet(KEY_UNLOCKED, null)?.toSet() ?: emptySet()

    /** The style worn in [slot], or null for the classic look. */
    fun style(slot: Cosmetics.Slot): Cosmetics.Item? =
        prefs.getString(styleKey(slot), null)?.takeIf { it in unlockedStyles() }?.let(Cosmetics::byId)

    fun setStyle(slot: Cosmetics.Slot, id: String?) {
        prefs.edit().putString(styleKey(slot), id).apply()
        notifyListeners()
    }

    /** The last [days] days, oldest first, ending with today. */
    fun history(days: Int): List<DayEntry> {
        rollOverIfNeeded()
        val end = day
        return (days - 1 downTo 0).map { back -> entry(end.minusDays(back.toLong())) }
    }

    private fun entry(d: LocalDate): DayEntry {
        val steps = if (d == day) stepsToday else prefs.getInt(dayKey(d), 0)
        val goal = if (d == day) goal else prefs.getInt(goalKey(d), goal)
        return DayEntry(d, steps, goal)
    }

    /**
     * Consecutive days that met their goal, ending today if it's already met, otherwise
     * yesterday: a streak stays alive until the day you skip is over.
     */
    fun streak(): Int {
        rollOverIfNeeded()
        var n = if (stepsToday >= goal) 1 else 0
        var d = day.minusDays(1)
        while (n < KEEP_DAYS && entry(d).metGoal) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** A streak that ended yesterday (yesterday missed its goal), for the newswire; 0 if none. */
    fun lostStreak(): Int {
        rollOverIfNeeded()
        if (stepsToday >= goal || entry(day.minusDays(1)).metGoal) return 0
        var n = 0
        var d = day.minusDays(2)
        while (n < KEEP_DAYS && entry(d).metGoal) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** Write any pending changes immediately (e.g. when the service is destroyed). */
    fun flush() {
        mainHandler.removeCallbacks(persistRunnable)
        persist()
    }

    private fun rollOverIfNeeded() {
        val now = today()
        if (now != day) {
            persist()
            day = now
            stepsToday = prefs.getInt(dayKey(now), 0)
            briskSeconds = prefs.getFloat(briskKey(now), 0f)
            lastStepAt = 0L
            recentCount = 0
            pruneOldDays(now)
            notifyListeners()
        }
    }

    private fun recordCadenceSample(count: Int) {
        recentTimes[recentHead] = clock()
        recentSteps[recentHead] = count
        recentHead = (recentHead + 1) % CADENCE_SAMPLES
        if (recentCount < CADENCE_SAMPLES) recentCount++
    }

    private val persistRunnable = Runnable { persist() }

    private fun schedulePersist() {
        mainHandler.removeCallbacks(persistRunnable)
        mainHandler.postDelayed(persistRunnable, PERSIST_DELAY_MS)
    }

    private fun persist() {
        if (!dirty) return
        dirty = false
        prefs.edit()
            .putInt(dayKey(day), stepsToday)
            .putInt(goalKey(day), goal)
            .putFloat(briskKey(day), briskSeconds)
            .putLong(KEY_LAST_COUNTER, lastCounter)
            .putInt(KEY_LAST_BOOT, lastBoot)
            .apply()
    }

    private fun pruneOldDays(now: LocalDate) {
        val cutoff = now.minusDays(KEEP_DAYS.toLong())
        val stale = prefs.all.keys.filter { key ->
            val prefix = when {
                key.startsWith(DAY_PREFIX) -> DAY_PREFIX
                key.startsWith(GOAL_PREFIX) -> GOAL_PREFIX
                key.startsWith(BRISK_PREFIX) -> BRISK_PREFIX
                else -> return@filter false
            }
            runCatching { LocalDate.parse(key.removePrefix(prefix)) }.getOrNull()?.isBefore(cutoff) == true
        }
        if (stale.isNotEmpty()) {
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    private fun notifyListeners() {
        for (listener in listeners) listener.onStepsChanged()
    }

    companion object {
        const val DEFAULT_GOAL = 8_000
        val GOAL_OPTIONS = intArrayOf(4_000, 6_000, 8_000, 10_000, 12_000, 15_000, 20_000)

        /** Used until the user sets height and weight. */
        const val DEFAULT_STRIDE_METERS = 0.75
        const val DEFAULT_WEIGHT_KG = 70
        private const val STRIDE_PER_CM = 0.00414
        private const val KCAL_PER_KG_KM = 0.75

        private const val PREFS = "neon_steps"
        private const val DAY_PREFIX = "day_"
        private const val KEY_LAST_COUNTER = "last_counter"
        private const val KEY_LAST_BOOT = "last_boot"
        private const val KEY_GOAL = "goal"
        private const val KEY_RAIN = "rain_mode"
        private const val KEY_GOAL_NOTIFIED = "goal_notified_on"
        private const val KEY_GOAL_CELEBRATED = "goal_celebrated_on"
        private const val KEY_HOTEL_FIXED = "hotel_fixed_on"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_SOUND = "sound_on"
        private const val GOAL_PREFIX = "daygoal_"
        private const val BRISK_PREFIX = "brisk_"
        private const val KEY_MISSION_DAY = "mission_day"
        private const val KEY_MISSION_KIND = "mission_kind"
        private const val KEY_MISSION_TARGET = "mission_target"
        private const val KEY_MISSION_DEADLINE = "mission_deadline"
        private const val KEY_MISSION_REWARD = "mission_reward"
        private const val KEY_MISSION_STATE = "mission_state"
        private const val KEY_MISSION_ANNOUNCED = "mission_announced_on"
        private const val KEY_MISSION_NOTIFIED = "mission_notified_on"
        private const val KEY_MISSION_CELEBRATED = "mission_celebrated_on"
        private const val KEY_UNLOCKED = "styles_unlocked"
        private const val BRISK_CADENCE = 100
        private const val KEEP_DAYS = 60
        private const val PERSIST_DELAY_MS = 3_000L
        private const val CADENCE_SAMPLES = 128
        private const val CADENCE_WINDOW_MS = 60_000L
        private const val CADENCE_IDLE_MS = 8_000L

        private fun dayKey(date: LocalDate) = DAY_PREFIX + date
        private fun goalKey(date: LocalDate) = GOAL_PREFIX + date
        private fun briskKey(date: LocalDate) = BRISK_PREFIX + date
        private fun styleKey(slot: Cosmetics.Slot) = "style_" + slot.name.lowercase()

        @Volatile
        private var instance: StepRepository? = null

        /** Drops the singleton so tests start from a fresh application's preferences. */
        internal fun resetForTests() {
            instance = null
        }

        fun get(context: Context): StepRepository =
            instance ?: synchronized(this) {
                instance ?: StepRepository(
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
