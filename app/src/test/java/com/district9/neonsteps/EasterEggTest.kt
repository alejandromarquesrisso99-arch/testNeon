package com.district9.neonsteps

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.ui.scene.EasterEgg
import com.district9.neonsteps.ui.scene.SceneView
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.concurrent.TimeUnit

/** Finds each easter egg the way a user would, and captures what it looks like. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class EasterEggTest {
    private lateinit var activity: MainActivity
    private lateinit var root: View
    private lateinit var scene: SceneView
    private val found = mutableListOf<EasterEgg>()
    private var clock = 0L

    @Before
    fun setUp() {
        StepRepository.resetForTests()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        StepRepository.get(activity).addSteps(5_310)
        root = activity.findViewById(R.id.root)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 2340)
        scene = activity.findViewById(R.id.scene)
        val app = scene.onEasterEgg
        scene.onEasterEgg = { found += it; app?.invoke(it) }
        advance(1f)
        clock = SystemClock.uptimeMillis()
    }

    @After
    fun tearDown() = StepRepository.resetForTests()

    private fun advance(seconds: Float) = repeat((seconds * 30).toInt()) { scene.advance(1f / 30f) }

    private fun touch(action: Int, x: Float, y: Float) {
        clock += 40
        MotionEvent.obtain(clock - 40, clock, action, x, y, 0).also { scene.dispatchTouchEvent(it); it.recycle() }
    }

    private fun tap(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
        advance(0.2f)
    }

    private fun capture(name: String) {
        ShadowLooper.idleMainLooper()
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun fiveTapsFixTheHotelL() {
        repeat(4) {
            val l = scene.eggTargets()!!.first
            tap(l.centerX(), l.centerY())
        }
        assertTrue(found.isEmpty())
        val l = scene.eggTargets()!!.first
        tap(l.centerX(), l.centerY())
        assertEquals(listOf(EasterEgg.HOTEL_FIXED), found)
        assertTrue(StepRepository.get(activity).hotelFixedToday())
        advance(0.3f)
        capture("egg_hotel_sparks.png")
        advance(2f)
        capture("egg_hotel_fixed.png")
    }

    @Test
    fun threeTapsFreeTheGoldenKoi() {
        repeat(3) {
            val koi = scene.eggTargets()!!.second
            tap(koi.centerX(), koi.centerY())
        }
        assertEquals(listOf(EasterEgg.GOLDEN_KOI), found)
        advance(4f)
        capture("egg_koi.png")
    }

    @Test
    fun longPressOnTheStepsCutsThePower() {
        val header = activity.findViewById<View>(R.id.header)
        val x = header.width / 2f
        val y = header.top + header.height * 0.45f
        touch(MotionEvent.ACTION_DOWN, x, y)
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS)
        touch(MotionEvent.ACTION_UP, x, y)
        assertEquals(listOf(EasterEgg.BLACKOUT), found)
        advance(2.5f)
        capture("egg_blackout.png")
        advance(4.2f)
        capture("egg_blackout_restore.png")
    }
}
