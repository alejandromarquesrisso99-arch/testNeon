package com.district9.neonsteps

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.district9.neonsteps.data.MissionKind
import com.district9.neonsteps.data.MissionState
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.util.CityClock
import com.district9.neonsteps.ui.scene.SceneView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Renders the real activity on the JVM with Robolectric's native graphics and writes PNGs to
 * app/build/screenshots, so the scene can be eyeballed without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest {

    @Before
    fun setUp() {
        CityClock.fixed = java.time.LocalTime.of(22, 30)
        StepRepository.resetForTests()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
        val prefs = app.getSharedPreferences("neon_steps", 0).edit()
        val today = LocalDate.now()
        val past = intArrayOf(5_210, 9_480, 7_020, 11_204, 3_890, 8_610)
        past.forEachIndexed { i, steps -> prefs.putInt("day_" + today.minusDays((past.size - i).toLong()), steps) }
        prefs.commit()
        seedMission(MissionKind.BEAT_YESTERDAY, 8_611, 24, "rain_cyan")
    }

    /** Today's mission, planted so the card doesn't depend on the date the tests run. */
    private fun seedMission(kind: MissionKind, target: Int, deadline: Int, reward: String, unlocked: Set<String> = emptySet(), worn: Map<String, String> = emptyMap()) {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("neon_steps", 0).edit()
            .putString("mission_day", LocalDate.now().toString())
            .putString("mission_kind", kind.name)
            .putInt("mission_target", target)
            .putInt("mission_deadline", deadline)
            .putString("mission_reward", reward)
            .putString("mission_state", MissionState.ACTIVE.name)
            .putStringSet("styles_unlocked", unlocked)
        worn.forEach { (slot, id) -> prefs.putString("style_$slot", id) }
        prefs.commit()
    }

    @After
    fun tearDown() {
        StepRepository.resetForTests()
        CityClock.fixed = null
    }

    @Test
    fun mainScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(6_482)
        render(activity, "main.png", seconds = 6f)
    }

    @Test
    fun historyOpen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(6_482)
        activity.findViewById<View>(R.id.btn_history).performClick()
        render(activity, "history.png", seconds = 2f)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun shortScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(9_215)
        render(activity, "short.png", seconds = 4f, h = 1920)
    }

    @Test
    fun permissionMissing() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        render(activity, "permission.png", seconds = 3f)
    }

    @Test
    fun pannedByDrag() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(6_482)
        val root = activity.findViewById<View>(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        val scene = activity.findViewById<SceneView>(R.id.scene)
        scene.advance(1f / 30f)
        // Drag right-to-left across the street and hold the finger down while capturing.
        val t0 = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, dt: Long) =
            MotionEvent.obtain(t0, t0 + dt, action, x, 1200f, 0).also { scene.dispatchTouchEvent(it); it.recycle() }
        touch(MotionEvent.ACTION_DOWN, 900f, 0)
        touch(MotionEvent.ACTION_MOVE, 860f, 16)
        touch(MotionEvent.ACTION_MOVE, 660f, 32)
        touch(MotionEvent.ACTION_MOVE, 500f, 48)
        render(activity, "panned.png", seconds = 1f)
    }

    @Test
    fun goalReached() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val repo = StepRepository.get(activity)
        repo.addSteps(8_350)
        // The missed celebration replays shortly after the app opens.
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(repo.goalCelebratedToday())
        render(activity, "goal.png", seconds = 2.6f)
    }

    @Test
    fun thirtyDayStreakGrowsTheCity() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("neon_steps", 0).edit()
        val today = LocalDate.now()
        for (back in 1..29) prefs.putInt("day_" + today.minusDays(back.toLong()), 9_000 + back * 37)
        prefs.commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(8_120)
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(StepRepository.get(activity).streak() == 30)
        render(activity, "streak.png", seconds = 13f)
    }

    @Test
    fun profilePanel() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).apply {
            addSteps(6_482)
            heightCm = 178
            weightKg = 74
        }
        activity.findViewById<View>(R.id.hud).performClick()
        render(activity, "profile.png", seconds = 1f)
    }

    @Test
    fun timesOfDay() {
        for ((h, m) in listOf(3 to 0, 6 to 40, 12 to 0, 19 to 10, 22 to 30)) {
            CityClock.fixed = java.time.LocalTime.of(h, m)
            StepRepository.resetForTests()
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            StepRepository.get(activity).addSteps(4_210)
            render(activity, "time_%02d%02d.png".format(h, m), seconds = 5f)
        }
    }

    @Test
    fun missionDoneWithStyles() {
        // Three styles already worn; today's mission pays the aurora.
        seedMission(
            MissionKind.STEPS_BY_HOUR, 4_000, 14, "sky_aurora",
            unlocked = setOf("rain_cyan", "koi_sakura", "sky_moon", "rain_pink", "koi_emerald"),
            worn = mapOf("rain" to "rain_cyan", "koi" to "koi_sakura", "sky" to "sky_moon"),
        )
        CityClock.fixed = java.time.LocalTime.of(12, 5)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val repo = StepRepository.get(activity)
        repo.addSteps(4_120)
        assertTrue(repo.evaluateMission() == MissionState.DONE)
        assertTrue(repo.style(com.district9.neonsteps.data.Cosmetics.Slot.SKY)?.id == "sky_aurora")
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(repo.missionCelebrated())
        CityClock.fixed = java.time.LocalTime.of(22, 30)
        render(activity, "mission_done.png", seconds = 2.2f)
    }

    @Test
    fun moonAndGoldenRain() {
        seedMission(
            MissionKind.BRISK_MINUTES, 15, 24, "rain_gold",
            unlocked = setOf("rain_cyan", "koi_sakura", "sky_moon", "rain_pink", "koi_emerald", "sky_aurora"),
            worn = mapOf("rain" to "rain_pink", "koi" to "koi_emerald", "sky" to "sky_moon"),
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(3_150)
        render(activity, "style_moon.png", seconds = 5f)
    }

    @Test
    fun missionFailed() {
        seedMission(MissionKind.MORNING_STEPS, 1_500, 10, "koi_sakura", unlocked = setOf("rain_cyan"))
        CityClock.fixed = java.time.LocalTime.of(10, 30)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(640)
        assertTrue(StepRepository.get(activity).evaluateMission() == MissionState.FAILED)
        render(activity, "mission_failed.png", seconds = 2f)
    }

    @Test
    fun stylesCollection() {
        seedMission(
            MissionKind.DISTANCE, 5_500, 24, "rain_gold",
            unlocked = setOf("rain_cyan", "koi_sakura", "sky_moon", "rain_pink", "koi_emerald"),
            worn = mapOf("rain" to "rain_pink", "koi" to "koi_sakura"),
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(5_020)
        activity.findViewById<View>(R.id.mission).performClick()
        render(activity, "styles.png", seconds = 1f)
        // ▶ on the koi row swaps to the next unlocked koi.
        val styles = activity.findViewById<com.district9.neonsteps.ui.hud.StyleView>(R.id.styles)
        val koiNext = (0 until styles.childCount).map { styles.getChildAt(it) }
            .filter { it.contentDescription?.contains("KOI") == true }
            .last()
        koiNext.performClick()
        assertTrue(StepRepository.get(activity).style(com.district9.neonsteps.data.Cosmetics.Slot.KOI)?.id == "koi_emerald")
        koiNext.performClick()
        assertTrue(StepRepository.get(activity).style(com.district9.neonsteps.data.Cosmetics.Slot.KOI) == null)
    }

    @Test
    fun launcherIcon() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(activity: MainActivity, name: String, seconds: Float, h: Int = 2340) {
        val root = activity.findViewById<View>(R.id.root)
        val w = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val scene = activity.findViewById<SceneView>(R.id.scene)
        val frames = (seconds * 30).toInt()
        repeat(frames) { scene.advance(1f / 30f) }
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
