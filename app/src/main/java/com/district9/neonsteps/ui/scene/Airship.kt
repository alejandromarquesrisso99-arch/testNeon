package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.district9.neonsteps.ui.Neon

/** Streak perk: an advertising airship drifting over the skyline with your streak on its screen. */
internal class Airship(private val frame: SceneFrame, private val sprites: Sprites, mono: Typeface) {
    var message = ""

    private val rx = frame.s(150f)
    private val ry = frame.s(36f)
    private val y = maxOf(frame.y(330f), frame.height * 0.24f)
    private var x = -rx * 1.5f
    private var waitUntil = 0f
    private var time = 0f
    private var scroll = 0f

    private val hull = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF130926.toInt() }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = frame.s(1.6f)
        color = Neon.alpha(Neon.VIOLET, 0.65f)
    }
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF05010F.toInt() }
    private val screenEdge = Paint(rim).apply { color = Neon.alpha(Neon.CYAN, 0.8f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mono
        textSize = frame.s(22f)
        letterSpacing = 0.12f
    }
    private val beam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val screenRect = RectF()
    private val fin = Path()

    fun update(dt: Float) {
        time += dt
        scroll += dt * frame.s(46f)
        if (time < waitUntil) return
        x += frame.s(34f) * dt
        if (x - rx * 1.5f > frame.width) {
            x = -rx * 1.5f
            waitUntil = time + 18f // a gap between passes
        }
    }

    fun draw(canvas: Canvas, dx: Float) {
        if (time < waitUntil) return
        val cx = x + dx
        // A faint searchlight sweeping the rooftops.
        beam.color = Neon.alpha(0xFFBFD9FF.toInt(), 0.06f)
        fin.reset()
        fin.moveTo(cx - frame.s(8f), y + ry + frame.s(10f))
        fin.lineTo(cx - frame.s(70f) + frame.s(40f) * kotlin.math.sin(time * 0.6f), y + frame.s(320f))
        fin.lineTo(cx + frame.s(70f) + frame.s(40f) * kotlin.math.sin(time * 0.6f), y + frame.s(320f))
        fin.lineTo(cx + frame.s(8f), y + ry + frame.s(10f))
        fin.close()
        canvas.drawPath(fin, beam)

        // Tail fins, hull, gondola.
        fin.reset()
        fin.moveTo(cx - rx * 0.8f, y)
        fin.lineTo(cx - rx * 1.12f, y - ry * 1.2f)
        fin.lineTo(cx - rx * 1.02f, y)
        fin.lineTo(cx - rx * 1.12f, y + ry * 1.2f)
        fin.close()
        canvas.drawPath(fin, hull)
        canvas.drawPath(fin, rim)
        oval.set(cx - rx, y - ry, cx + rx, y + ry)
        canvas.drawOval(oval, hull)
        canvas.drawOval(oval, rim)
        oval.set(cx - frame.s(26f), y + ry - frame.s(4f), cx + frame.s(26f), y + ry + frame.s(12f))
        canvas.drawRoundRect(oval, frame.s(5f), frame.s(5f), hull)
        canvas.drawRoundRect(oval, frame.s(5f), frame.s(5f), rim)

        // Navigation lights.
        val blink = if ((time * 1.4f).toInt() % 2 == 0) 0.9f else 0.25f
        sprites.drawBlob(canvas, cx - rx * 0.98f, y, frame.s(12f), frame.s(12f), 0xFFFF3B3B.toInt(), blink)
        sprites.drawBlob(canvas, cx + rx * 0.98f, y, frame.s(12f), frame.s(12f), 0xFF3BFF8A.toInt(), 1.15f - blink)

        // The screen, with the message scrolling across it.
        screenRect.set(cx - rx * 0.62f, y - ry * 0.5f, cx + rx * 0.62f, y + ry * 0.5f)
        canvas.drawRoundRect(screenRect, frame.s(4f), frame.s(4f), screen)
        sprites.drawBlob(canvas, screenRect.centerX(), screenRect.centerY(), screenRect.width() * 0.7f, ry * 1.4f, Neon.CYAN, 0.12f)
        canvas.save()
        canvas.clipRect(screenRect)
        val gap = frame.s(60f)
        val w = text.measureText(message) + gap
        if (w > gap) {
            var tx = screenRect.left - (scroll % w)
            val ty = screenRect.centerY() + text.textSize * 0.35f
            while (tx < screenRect.right) {
                text.color = Neon.MAGENTA
                canvas.drawText(message, tx + frame.s(1.5f), ty + frame.s(1f), text)
                text.color = Neon.CYAN
                canvas.drawText(message, tx, ty, text)
                tx += w
            }
        }
        canvas.restore()
        canvas.drawRoundRect(screenRect, frame.s(4f), frame.s(4f), screenEdge)
    }
}
