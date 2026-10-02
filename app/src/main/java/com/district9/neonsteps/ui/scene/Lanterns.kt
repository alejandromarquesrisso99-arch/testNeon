package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.district9.neonsteps.ui.Neon
import kotlin.math.sin

/** Streak perk: paper lanterns drifting up from the market into the rain. */
internal class Lanterns(private val frame: SceneFrame, private val sprites: Sprites) {
    private val count = 12
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val vy = FloatArray(count)
    private val phase = FloatArray(count)
    private val size = FloatArray(count)
    private val warm = FloatArray(count)
    private val rng = Rng(5)
    private val bottom = frame.y(1300f)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rib = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = frame.s(1.4f) }
    private val rect = RectF()

    init {
        for (i in 0 until count) respawn(i, initial = true)
    }

    private fun respawn(i: Int, initial: Boolean) {
        x[i] = rng.range(0f, frame.width.toFloat())
        y[i] = if (initial) rng.range(frame.height * 0.15f, bottom) else bottom + frame.s(rng.range(0f, 60f))
        vy[i] = frame.s(rng.range(14f, 30f))
        phase[i] = rng.next() * 6f
        size[i] = rng.range(0.7f, 1.15f)
        warm[i] = rng.next()
    }

    fun update(dt: Float) {
        for (i in 0 until count) {
            y[i] -= vy[i] * dt
            x[i] += frame.s(-6f) * dt // the same wind that slants the rain
            phase[i] += dt
            if (y[i] < frame.height * 0.08f) respawn(i, initial = false)
        }
    }

    fun draw(canvas: Canvas, dx: Float) {
        for (i in 0 until count) {
            val s = size[i] * frame.s(1f)
            val cx = x[i] + dx + sin(phase[i] * 0.8f) * 6f * s
            val cy = y[i]
            // Fade in as they leave the stalls, out as they climb away.
            val fade = smoothstep(frame.height * 0.08f, frame.height * 0.3f, cy) * (1f - smoothstep(bottom - frame.s(40f), bottom, cy))
            if (fade <= 0.01f) continue
            val flick = 0.85f + 0.15f * noise1(phase[i] * 6f, i)
            val color = Neon.mix(0xFFFF4E2A.toInt(), 0xFFFFB347.toInt(), warm[i])
            sprites.drawBlob(canvas, cx, cy, 34f * s, 34f * s, color, 0.35f * fade * flick)
            rect.set(cx - 8f * s, cy - 10f * s, cx + 8f * s, cy + 10f * s)
            body.color = Neon.alpha(color, 0.9f * fade)
            canvas.drawRoundRect(rect, 7f * s, 8f * s, body)
            sprites.drawBlob(canvas, cx, cy, 6f * s, 7f * s, 0xFFFFF1B8.toInt(), 0.9f * fade * flick)
            rib.color = Neon.alpha(0xFF3A0E0A.toInt(), 0.8f * fade)
            canvas.drawLine(cx - 5f * s, cy - 10f * s, cx + 5f * s, cy - 10f * s, rib)
            canvas.drawLine(cx - 5f * s, cy + 10f * s, cx + 5f * s, cy + 10f * s, rib)
        }
    }
}
