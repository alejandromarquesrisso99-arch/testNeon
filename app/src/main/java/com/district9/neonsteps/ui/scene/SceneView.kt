package com.district9.neonsteps.ui.scene

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.district9.neonsteps.ui.NeonFonts

/** Full-screen, live-rendered District 9 street. Purely decorative for accessibility. */
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
            if (pendingStrike > 0f) {
                s.strike(pendingStrike)
                pendingStrike = 0f
            }
            scene = s
        }
    }

    /** Advance the simulation by [dt] seconds without waiting for vsync (used by tests and previews). */
    internal fun advance(dt: Float) {
        ensureScene()?.update(dt)
    }

    override fun onDraw(canvas: Canvas) {
        val s = ensureScene() ?: return
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNs = now
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
}
