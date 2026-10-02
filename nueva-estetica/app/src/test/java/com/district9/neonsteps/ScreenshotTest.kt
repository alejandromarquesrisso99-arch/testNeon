package com.district9.neonsteps

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.district9.neonsteps.data.StepRepository
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
        StepRepository.resetForTests()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS)
        val prefs = app.getSharedPreferences("neon_steps", 0).edit()
        val today = LocalDate.now()
        val past = intArrayOf(5_210, 9_480, 7_020, 11_204, 3_890, 8_610)
        past.forEachIndexed { i, steps -> prefs.putInt("day_" + today.minusDays((past.size - i).toLong()), steps) }
        prefs.commit()
    }

    @After
    fun tearDown() = StepRepository.resetForTests()

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
