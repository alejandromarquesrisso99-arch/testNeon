package com.district9.neonsteps.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/** Where today's steps come from; shown in the UI so the user knows how reliable the count is. */
enum class SensorMode { NONE, STEP_COUNTER, STEP_DETECTOR, ACCELEROMETER }

enum class RainMode { AUTO, DRIZZLE, RAIN, DOWNPOUR }

data class DayEntry(val date: LocalDate, val steps: Int)

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
) {
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory working copy; persisted with a short debounce so walking doesn't hammer disk.
    private var day: LocalDate = today()
    private var stepsToday: Int = prefs.getInt(dayKey(day), 0)
    private var lastCounter: Long = prefs.getLong(KEY_LAST_COUNTER, -1L)
    private var lastBoot: Int = prefs.getInt(KEY_LAST_BOOT, -1)
    private var dirty = false

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
            prefs.edit().putInt(KEY_GOAL, value).apply()
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

    /** The last [days] days, oldest first, ending with today. */
    fun history(days: Int): List<DayEntry> {
        rollOverIfNeeded()
        val end = day
        return (days - 1 downTo 0).map { back ->
            val d = end.minusDays(back.toLong())
            DayEntry(d, if (d == end) stepsToday else prefs.getInt(dayKey(d), 0))
        }
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
            .putLong(KEY_LAST_COUNTER, lastCounter)
            .putInt(KEY_LAST_BOOT, lastBoot)
            .apply()
    }

    private fun pruneOldDays(now: LocalDate) {
        val cutoff = now.minusDays(KEEP_DAYS.toLong())
        val stale = prefs.all.keys.filter { key ->
            key.startsWith(DAY_PREFIX) &&
                runCatching { LocalDate.parse(key.removePrefix(DAY_PREFIX)) }.getOrNull()?.isBefore(cutoff) == true
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

        /** Average walking stride and energy cost; good enough without asking for height/weight. */
        const val STRIDE_METERS = 0.75
        const val KCAL_PER_STEP = 0.04

        private const val PREFS = "neon_steps"
        private const val DAY_PREFIX = "day_"
        private const val KEY_LAST_COUNTER = "last_counter"
        private const val KEY_LAST_BOOT = "last_boot"
        private const val KEY_GOAL = "goal"
        private const val KEY_RAIN = "rain_mode"
        private const val KEEP_DAYS = 60
        private const val PERSIST_DELAY_MS = 3_000L
        private const val CADENCE_SAMPLES = 128
        private const val CADENCE_WINDOW_MS = 60_000L
        private const val CADENCE_IDLE_MS = 8_000L

        private fun dayKey(date: LocalDate) = DAY_PREFIX + date

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
