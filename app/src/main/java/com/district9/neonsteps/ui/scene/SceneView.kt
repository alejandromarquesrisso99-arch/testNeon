package com.district9.neonsteps.ui.scene

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.district9.neonsteps.ui.NeonFonts
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/** Hidden things to find in District 9. */
enum class EasterEgg { HOTEL_FIXED, GOLDEN_KOI, BLACKOUT, KAGE_BUNSHIN, RAMEN_HOLOGRAM }

/**
 * Full-screen, live-rendered District 9 street. Drag sideways to pan the city: each layer
 * slides at its own speed, then the camera springs back. A tap calls down lightning, unless
 * it lands on something with a secret. Purely decorative for accessibility.
 */
class SceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private var scene: CityScene? = null
    private var lastFrameNs = 0L
    private var streetLimit = -1f

    // Settings applied to whichever scene instance is current (it's rebuilt on resize).
    private var rain = 0.7f
    private var tilt: Float? = null
    private var activity = 0f
    private var pendingStrike = 0f
    private var celebrating = false
    private var pendingCelebration = false
    private var pendingMission = 0 // 1 a mission salvo, 2 a double one
    private var hotelFixed = false

    /** Told when the user finds an easter egg. */
    var onEasterEgg: ((EasterEgg) -> Unit)? = null

    // Long-pressing the step count (this region, in view coordinates) cuts the power.
    private val blackoutTrigger = RectF()
    private var longPressFired = false
    private val longPress = Runnable {
        val s = scene ?: return@Runnable
        if (dragging || s.blackoutRunning) return@Runnable
        longPressFired = true
        buzzBlackout()
        s.blackout()
        onEasterEgg?.invoke(EasterEgg.BLACKOUT)
    }

    // Drag-to-pan state, kept here so it survives the scene being rebuilt.
    private var pan = 0f
    private var panVelocity = 0f
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var tracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setRainIntensity(value: Float) {
        rain = value
        scene?.setRainIntensity(value)
    }

    fun setTilt(x: Float) {
        tilt = x
        scene?.setTilt(x)
    }

    fun setActivity(value: Float) {
        activity = value
        scene?.activity = value
    }

    /**
     * The grid dying, felt in the hand: a heavy thunk, two sputters and a fading hum, timed to
     * the 0.8 s brownout. It's part of the show, like a game's rumble, so it's sent as media
     * vibration: the system's "vibrate on touch" switch (which silently mutes
     * performHapticFeedback) doesn't apply.
     */
    private fun buzzBlackout() = buzz(BLACKOUT_TIMINGS, BLACKOUT_AMPLITUDES)

    private fun buzz(timings: LongArray, amplitudes: IntArray) {
        val vibrator = vibrator()
        if (vibrator == null || !vibrator.hasVibrator()) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private var streak = 0
    private var audio: SceneAudio? = null
    private var hour = 22f
    private var style: (CityScene) -> Unit = {}

    /** Local time as a fractional hour (e.g. 19.5 = 19:30); the city follows it. */
    fun setHour(value: Float) {
        hour = value
        scene?.hour = value
    }

    private var styleKey: List<Any?>? = null

    /** Unlocked styles to show: rain colour, koi colours (main, chroma, core) or null, sky extras. */
    fun setStyle(rainColor: Int, koi: IntArray?, aurora: Boolean, moon: Boolean) {
        val key = listOf(rainColor, koi?.toList(), aurora, moon)
        if (key == styleKey) return
        styleKey = key
        style = { it.setStyle(rainColor, koi, aurora, moon) }
        scene?.let(style)
    }

    /** A mission done: chime and fireworks salvo (a double one if fireworks were the reward). */
    fun missionComplete(big: Boolean) {
        val s = scene
        if (s == null) pendingMission = if (big) 2 else 1 else s.missionComplete(big)
    }

    fun setAudio(value: SceneAudio?) {
        audio = value
        scene?.audio = value
    }

    /** Days in a row meeting the goal: District 9 grows with it. */
    fun setStreak(days: Int) {
        if (days == streak) return
        streak = days
        scene?.streak = days
    }

    /** The HOTEL's "L" stays fixed for the rest of the day once someone fixes it. */
    fun setHotelFixed(value: Boolean) {
        hotelFixed = value
        scene?.hotelFixed = value
    }

    fun setBlackoutTrigger(region: RectF) {
        blackoutTrigger.set(region)
    }

    /** Goal met today: keep Tower 61 lit and fireworks going (survives scene rebuilds). */
    fun setCelebrating(value: Boolean) {
        celebrating = value
        scene?.celebrating = value
    }

    /** Play the goal celebration once; deferred until the scene exists. */
    fun celebrate() {
        celebrating = true
        val s = scene
        if (s == null) pendingCelebration = true else s.celebrate()
    }

    fun strike(strength: Float) {
        val s = scene
        if (s == null) pendingStrike = strength else s.strike(strength)
    }

    /**
     * Y of the top of the HUD overlay. The kerb is placed a little above it so the wet street
     * always shows between the market and the readouts, whatever the screen's aspect ratio.
     */
    fun setStreetLimit(y: Float) {
        if (kotlin.math.abs(y - streetLimit) < 1f) return
        streetLimit = y
        scene = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scene = null
    }

    private fun ensureScene(): CityScene? {
        scene?.let { return it }
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val horizon = if (streetLimit > 0f) (streetLimit - h * 0.075f).coerceIn(h * 0.52f, h * 0.72f) else h * 0.705f
        return CityScene(fonts, w, h, horizon, hour).also { s ->
            style(s)
            s.setRainIntensity(rain)
            tilt?.let(s::setTilt)
            s.activity = activity
            s.celebrating = celebrating
            s.hotelFixed = hotelFixed
            s.streak = streak
            s.audio = audio
            if (pendingCelebration) {
                s.celebrate()
                pendingCelebration = false
            }
            if (pendingStrike > 0f) {
                s.strike(pendingStrike)
                pendingStrike = 0f
            }
            if (pendingMission > 0) {
                s.missionComplete(pendingMission == 2)
                pendingMission = 0
            }
            scene = s
        }
    }

    /** Advance the simulation by [dt] seconds without waiting for vsync (used by tests and previews). */
    internal fun advance(dt: Float) {
        val s = ensureScene() ?: return
        stepPan(s, dt)
        s.update(dt)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
                longPressFired = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (blackoutTrigger.contains(event.x, event.y)) postDelayed(longPress, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (abs(event.y - downY) > touchSlop) removeCallbacks(longPress)
                if (!dragging && abs(event.x - downX) > touchSlop) {
                    removeCallbacks(longPress)
                    dragging = true
                    panVelocity = 0f
                    // Start from the slop boundary so movement past it isn't swallowed.
                    lastX = downX + sign(event.x - downX) * touchSlop
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    dragBy(event.x - lastX)
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (dragging) {
                    tracker?.let {
                        it.addMovement(event)
                        it.computeCurrentVelocity(1000)
                        panVelocity = it.xVelocity
                    }
                } else if (!longPressFired) {
                    when (scene?.tap(event.x, event.y)) {
                        CityScene.Tap.HOTEL_FIXED -> onEasterEgg?.invoke(EasterEgg.HOTEL_FIXED)
                        CityScene.Tap.GOLDEN_KOI -> onEasterEgg?.invoke(EasterEgg.GOLDEN_KOI)
                        CityScene.Tap.KAGE_BUNSHIN -> {
                            buzz(POOF_TIMINGS, POOF_AMPLITUDES)
                            onEasterEgg?.invoke(EasterEgg.KAGE_BUNSHIN)
                        }
                        CityScene.Tap.RAMEN_HOLOGRAM -> onEasterEgg?.invoke(EasterEgg.RAMEN_HOLOGRAM)
                        CityScene.Tap.CONSUMED -> Unit
                        else -> performClick()
                    }
                }
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                endGesture()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun endGesture() {
        dragging = false
        tracker?.recycle()
        tracker = null
    }

    /** Follows the finger 1:1 at the near layer, with rubber-band resistance past the range. */
    private fun dragBy(dx: Float) {
        val range = scene?.panRange ?: return
        val resist = if (abs(pan) > range && sign(dx) == sign(pan)) 0.3f else 1f
        pan = (pan + dx * resist).coerceIn(-range * 1.15f, range * 1.15f)
    }

    /** After release: fling momentum into a critically damped spring back to centre. */
    private fun stepPan(s: CityScene, dt: Float) {
        if (!dragging && dt > 0f) {
            val accel = -SPRING * pan - DAMPING * panVelocity
            panVelocity += accel * dt
            pan += panVelocity * dt
            val limit = s.panRange * 1.15f
            if (abs(pan) > limit) {
                pan = limit * sign(pan)
                panVelocity = 0f
            }
            if (abs(pan) < 0.1f && abs(panVelocity) < 1f) {
                pan = 0f
                panVelocity = 0f
            }
        }
        s.pan = pan
    }

    override fun onDraw(canvas: Canvas) {
        val s = ensureScene() ?: return
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNs = now
        stepPan(s, dt)
        s.update(dt)
        s.draw(canvas)
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastFrameNs = 0L
            postInvalidateOnAnimation()
        }
    }

    /** Current easter-egg hit boxes, for tests: the HOTEL's "L" and the koi. */
    internal fun eggTargets(): EggTargets? = scene?.debugTargets()

    companion object {
        /** The rain's colour with no style unlocked. */
        const val CLASSIC_RAIN = Rain.DEFAULT_COLOR

        private const val LONG_PRESS_MS = 650L

        // Waveform segments (ms) and their strength (0–255): thunk, gap, sputter, gap, sputter, gap, hum.
        private val BLACKOUT_TIMINGS = longArrayOf(0, 90, 120, 40, 90, 45, 150, 265)
        private val BLACKOUT_AMPLITUDES = intArrayOf(0, 255, 0, 150, 0, 110, 0, 55)

        // Kage Bunshin: silence while the ninja dashes out, a big poof, then a patter of small
        // poofs as the clones appear (in step with NinjaSquad's timeline).
        private val POOF_TIMINGS = longArrayOf(0, 1600, 70, 50) + LongArray(18) { if (it % 2 == 0) 25L else 95L }
        private val POOF_AMPLITUDES = intArrayOf(0, 0, 230, 0) + IntArray(18) { if (it % 2 == 0) 120 else 0 }
        private const val SPRING = 6f
        private val DAMPING = 2f * sqrt(SPRING)
    }
}
