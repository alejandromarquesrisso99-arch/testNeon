package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.exp

/** A lightning strike: a jagged bolt behind the towers and a double flash over the whole street. */
internal class Lightning(private val frame: SceneFrame) {
    private val bolt = Path()
    private var age = 99f
    private var power = 0f
    private val rng = Rng(7)

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val flashPaint = Paint()

    val active: Boolean get() = age < 1.2f

    /** @param strength 0..1; goal celebrations strike harder than milestones. */
    fun strike(strength: Float) {
        age = 0f
        power = strength.coerceIn(0.2f, 1f)
        buildBolt()
    }

    private fun buildBolt() {
        bolt.reset()
        var x = frame.width * rng.range(0.15f, 0.85f)
        var y = 0f
        val end = frame.y(rng.range(560f, 820f))
        bolt.moveTo(x, y)
        val branches = ArrayList<Pair<Float, Float>>()
        while (y < end) {
            y += frame.s(rng.range(22f, 60f))
            x += frame.s(rng.range(-46f, 46f))
            bolt.lineTo(x, y)
            if (rng.chance(0.18f)) branches += x to y
        }
        for ((bx, by) in branches) {
            var cx = bx
            var cy = by
            bolt.moveTo(cx, cy)
            val dir = if (rng.chance(0.5f)) -1f else 1f
            repeat(rng.int(2, 5)) {
                cx += dir * frame.s(rng.range(14f, 40f))
                cy += frame.s(rng.range(18f, 40f))
                bolt.lineTo(cx, cy)
            }
        }
    }

    fun update(dt: Float) {
        if (age < 5f) age += dt
    }

    /** Brightness envelope: two quick pulses, then a fading afterglow. */
    private fun flash(): Float {
        if (age > 1.2f) return 0f
        val p1 = exp(-age * 18f)
        val p2 = if (age > 0.16f) exp(-(age - 0.16f) * 10f) * 0.8f else 0f
        return (p1 + p2).coerceAtMost(1f) * power
    }

    /** Sky glow and bolt; drawn before the skyline so towers stand in silhouette. */
    fun drawSky(canvas: Canvas) {
        val f = flash()
        if (f <= 0.01f) return
        flashPaint.color = 0xFFB8A6FF.toInt()
        flashPaint.alpha = (110 * f).toInt()
        canvas.drawRect(0f, 0f, frame.width.toFloat(), frame.horizon, flashPaint)
        if (age < 0.38f) {
            val a = if (age < 0.06f || (age in 0.16f..0.26f)) 1f else 0.35f
            glow.color = 0xFF9F7BFF.toInt()
            glow.alpha = (90 * a * power).toInt()
            glow.strokeWidth = frame.s(12f)
            canvas.drawPath(bolt, glow)
            glow.color = 0xFFE9E1FF.toInt()
            glow.alpha = (255 * a).toInt()
            glow.strokeWidth = frame.s(2.6f)
            canvas.drawPath(bolt, glow)
        }
    }

    /** Whole-frame wash on top of everything in the scene. */
    fun drawOverlay(canvas: Canvas) {
        val f = flash()
        if (f <= 0.01f) return
        flashPaint.color = 0xFFE4DCFF.toInt()
        flashPaint.alpha = (70 * f).toInt()
        canvas.drawRect(0f, 0f, frame.width.toFloat(), frame.height.toFloat(), flashPaint)
    }
}
