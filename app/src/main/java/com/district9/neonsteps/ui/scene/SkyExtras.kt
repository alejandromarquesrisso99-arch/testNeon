package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.district9.neonsteps.ui.Neon
import kotlin.math.sin

/** Unlockable skies: an aurora rippling over District 9, and a full moon behind the smog. */
internal class SkyExtras(private val frame: SceneFrame, private val sprites: Sprites) {
    var aurora = false
    var moon = false

    private val ray = Paint().apply { strokeWidth = frame.s(6f) }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crater = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auroraColors = intArrayOf(0xFF5CFFC8.toInt(), 0xFF3DD6FF.toInt(), 0xFF7DFFB0.toInt())

    // Both live in the open sky above the towers, behind the big step count.
    private val bandTop = frame.height * 0.035f
    private val base = FloatArray(3) { k -> bandTop + frame.s(170f + 45f * k) }

    // Curtains are brightest along their lower hem and fade up into violet.
    private val shaders = Array(3) { k ->
        LinearGradient(
            0f, base[k] - frame.s(230f), 0f, base[k] + frame.s(40f),
            intArrayOf(0x00000000, Neon.alpha(0xFFB28CFF.toInt(), 0.12f), Neon.alpha(auroraColors[k], 0.6f), 0x00000000),
            floatArrayOf(0f, 0.45f, 0.84f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /** Drawn right after the sky gradient, so smog drifts in front. */
    fun draw(canvas: Canvas, t: Float, dx: Float, daylight: Float) {
        val night = 1f - 0.75f * daylight
        if (moon) drawMoon(canvas, dx, night)
        if (aurora) drawAurora(canvas, t, dx, night)
    }

    private fun drawMoon(canvas: Canvas, dx: Float, night: Float) {
        val cx = frame.width * 0.84f + dx
        val cy = frame.height * 0.085f
        val r = frame.s(44f)
        sprites.drawBlob(canvas, cx, cy, r * 4.2f, r * 4.2f, 0xFFB9A6FF.toInt(), 0.32f * night)
        disc.color = Neon.alpha(0xFFF2E9D0.toInt(), 0.92f * night)
        canvas.drawCircle(cx, cy, r, disc)
        crater.color = Neon.alpha(0xFF8C7FA6.toInt(), 0.22f * night)
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.2f, r * 0.22f, crater)
        canvas.drawCircle(cx + r * 0.25f, cy + r * 0.3f, r * 0.16f, crater)
        canvas.drawCircle(cx + r * 0.38f, cy - r * 0.35f, r * 0.1f, crater)
        canvas.drawCircle(cx - r * 0.05f, cy + r * 0.05f, r * 0.08f, crater)
    }

    private fun drawAurora(canvas: Canvas, t: Float, dx: Float, night: Float) {
        val w = frame.width.toFloat()
        val step = frame.s(5f)
        for (k in 0 until 3) {
            ray.shader = shaders[k]
            val breathe = 0.6f + 0.4f * noise1(t * 0.3f + k * 5f, k)
            var x = -step + (dx % step)
            while (x <= w + step) {
                val u = x - dx // fixed to the sky, so it pans with it
                val hem = base[k] + frame.s(22f) * sin(u / frame.s(170f) + t * (0.25f + 0.07f * k) + k * 1.7f)
                val length = frame.s(90f) + frame.s(120f) * noise1(u / frame.s(70f) + t * 0.12f + k * 9f, k)
                // Vertical striations shimmer along the curtain.
                val shimmer = 0.3f + 0.7f * noise1(u / frame.s(16f) + t * 0.7f + k * 3f, k + 7)
                ray.alpha = (255 * night * breathe * shimmer).toInt().coerceIn(0, 255)
                canvas.drawLine(x, hem - length, x, hem + frame.s(4f), ray)
                x += step
            }
        }
    }
}
