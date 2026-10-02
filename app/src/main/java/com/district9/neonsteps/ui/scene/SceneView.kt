package com.district9.neonsteps.ui.scene

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.district9.neonsteps.ui.NeonFonts
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Full-screen, live-rendered District 9 street. Drag sideways to pan the city: each layer
 * slides at its own speed, then the camera springs back. A tap calls down lightning.
 * Purely decorative for accessibility.
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

    // Drag-to-pan state, kept here so it survives the scene being rebuilt.
    private var pan = 0f
    private var panVelocity = 0f
    private var dragging = false
    private var downX = 0f
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
        return CityScene(fonts, w, h, horizon).also { s ->
            s.setRainIntensity(rain)
            tilt?.let(s::setTilt)
            s.activity = activity
            s.celebrating = celebrating
            if (pendingCelebration) {
                s.celebrate()
                pendingCelebration = false
            }
            if (pendingStrike > 0f) {
                s.strike(pendingStrike)
                pendingStrike = 0f
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
                lastX = event.x
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (!dragging && abs(event.x - downX) > touchSlop) {
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
                if (dragging) {
                    tracker?.let {
                        it.addMovement(event)
                        it.computeCurrentVelocity(1000)
                        panVelocity = it.xVelocity
                    }
                } else {
                    performClick()
                }
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> endGesture()
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

    private companion object {
        const val SPRING = 6f
        val DAMPING = 2f * sqrt(SPRING)
    }
}
