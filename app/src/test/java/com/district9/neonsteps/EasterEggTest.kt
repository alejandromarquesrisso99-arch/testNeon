package com.district9.neonsteps

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.util.CityClock
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
        CityClock.fixed = java.time.LocalTime.of(22, 30)
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
    fun tearDown() {
        StepRepository.resetForTests()
        CityClock.fixed = null
    }

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
            val l = scene.eggTargets()!!.hotelL
            tap(l.centerX(), l.centerY())
        }
        assertTrue(found.isEmpty())
        val l = scene.eggTargets()!!.hotelL
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
            val koi = scene.eggTargets()!!.koi
            tap(koi.centerX(), koi.centerY())
        }
        assertEquals(listOf(EasterEgg.GOLDEN_KOI), found)
        advance(4f)
        capture("egg_koi.png")
    }

    @Test
    fun threeTapsOnRamenCallTheShadowClones() {
        repeat(3) {
            val sign = scene.eggTargets()!!.ramenSign
            tap(sign.centerX(), sign.centerY())
        }
        assertEquals(listOf(EasterEgg.KAGE_BUNSHIN), found)
        // A big poof, then the clones' patter, felt as media vibration.
        assertTrue(shadowOf(activity.getSystemService(VibratorManager::class.java).defaultVibrator).isVibrating)
        advance(0.9f)
        capture("egg_ninja_dash.png")
        advance(1.8f)
        capture("egg_kage_bunshin.png")
    }

    @Test
    fun tappingTheRamenStandProjectsABowl() {
        val stall = scene.eggTargets()!!.ramenStall
        tap(stall.centerX(), stall.centerY())
        assertEquals(listOf(EasterEgg.RAMEN_HOLOGRAM), found)
        // Tapping again just keeps the bowl coming; it's not a new discovery.
        tap(stall.centerX(), stall.centerY())
        assertEquals(1, found.size)
        advance(1.2f)
        capture("egg_ramen.png")
    }

    @Test
    fun caloriesAreCountedInRamenBowls() {
        assertEquals("0,0 CUENCOS", com.district9.neonsteps.util.Format.ramenBowls(0, 0.04))
        assertEquals("1,0 CUENCO", com.district9.neonsteps.util.Format.ramenBowls(13_750, 0.04))
        assertEquals("0,6 CUENCOS", com.district9.neonsteps.util.Format.ramenBowls(8_000, 0.04))
    }

    @Test
    fun longPressOnTheStepsCutsThePower() {
        val header = activity.findViewById<View>(R.id.header)
        val x = header.width / 2f
        val y = header.top + header.height * 0.45f
        touch(MotionEvent.ACTION_DOWN, x, y)
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS)
        // The phone buzzes as the grid dies, as media vibration (not muted by "vibrate on touch").
        val vibrator = shadowOf(activity.getSystemService(VibratorManager::class.java).defaultVibrator)
        assertTrue(vibrator.isVibrating)
        assertEquals(
            VibrationAttributes.USAGE_MEDIA,
            (vibrator.vibrationAttributesFromLastVibration as VibrationAttributes).usage,
        )
        touch(MotionEvent.ACTION_UP, x, y)
        assertEquals(listOf(EasterEgg.BLACKOUT), found)
        advance(2.5f)
        capture("egg_blackout.png")
        advance(4.2f)
        capture("egg_blackout_restore.png")
    }
}
