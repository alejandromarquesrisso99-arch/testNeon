package com.district9.neonsteps

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.ui.scene.SceneView
import org.junit.After
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
