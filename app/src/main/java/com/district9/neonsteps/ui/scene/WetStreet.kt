package com.district9.neonsteps.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.district9.neonsteps.ui.Neon
import kotlin.math.pow
import kotlin.math.sin

/**
 * The rain-soaked street: everything above the kerb mirrored into a blurred, rippling
 * reflection. Static scenery is baked once at low resolution and blurred; neon signs are
 * mirrored live so their flicker shows up in the puddles too.
 */
internal class WetStreet(private val frame: SceneFrame) {
    private val horizon = frame.horizon
    private val streetH = (frame.height - horizon).coerceAtLeast(1f)
    private val q = 0.25f

    private val baked: Bitmap = Bitmap.createBitmap(
        (frame.width * q).toInt().coerceAtLeast(1),
        (streetH * q).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )

    private val basePaint = Paint().apply {
        shader = LinearGradient(0f, horizon, 0f, frame.height.toFloat(), 0xFF0B021A.toInt(), 0xFF040010.toInt(), Shader.TileMode.CLAMP)
    }
    private val fadePaint = Paint().apply {
        shader = LinearGradient(
            0f, horizon, 0f, frame.height.toFloat(),
            intArrayOf(0x00050110, 0x18050110, 0x80040010.toInt()),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val kerbPaint = Paint().apply { color = 0xFF07020F.toInt() }
    private val kerbEdge = Paint().apply {
        color = 0x55B48CFF
        strokeWidth = frame.s(1.2f)
    }
    private val reflPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        // Wet asphalt is a good mirror: lift the blurred reflection back toward the source's glow.
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(1.7f, 1.6f, 1.75f, 1f) })
    }
    private val signPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private val strips = 30
    private val stripY = FloatArray(strips + 1) { i -> horizon + streetH * (i / strips.toFloat()).pow(1.25f) }
    private val src = Rect()
    private val dst = RectF()

    private val glints = 26
    private val glintX = FloatArray(glints)
    private val glintY = FloatArray(glints)
    private val glintLen = FloatArray(glints)
    private val glintColor = IntArray(glints)

    init {
        val rng = Rng(77)
        val colors = intArrayOf(Neon.MAGENTA, Neon.CYAN, Neon.YELLOW, 0xFFD8C8FF.toInt())
        for (i in 0 until glints) {
            glintX[i] = rng.range(0f, frame.width.toFloat())
            glintY[i] = horizon + streetH * rng.range(0.08f, 0.95f).pow(1.3f)
            glintLen[i] = frame.s(rng.range(18f, 90f)) * (0.4f + (glintY[i] - horizon) / streetH)
            glintColor[i] = colors[rng.int(0, colors.size)]
        }
    }

    /**
     * Bakes the mirrored, blurred street reflection.
     * @param drawAbove draws the static scene above the horizon in normal screen coordinates.
     */
    fun bake(drawAbove: (Canvas) -> Unit) {
        val c = Canvas(baked)
        c.drawColor(0xFF0B021A.toInt())
        c.save()
        // Mirror around the horizon: a point h px above the kerb lands h px below it.
        c.scale(q, -q)
        c.translate(0f, -horizon)
        drawAbove(c)
        c.restore()
        boxBlur(baked, radiusX = 2, radiusY = 5)
    }

    fun drawGround(canvas: Canvas) {
        canvas.drawRect(0f, horizon, frame.width.toFloat(), frame.height.toFloat(), basePaint)
    }

    /**
     * @param signs mirrored live, so their flicker plays in the puddles.
     * @param dynamic extra live content (e.g. umbrellas) drawn mirrored, unrippled.
     */
    fun drawReflection(canvas: Canvas, t: Float, dx: Float, signs: List<NeonSign>, signDx: Float, dynamic: (Canvas) -> Unit) {
        val w = frame.width.toFloat()
        for (i in 0 until strips) {
            val y0 = stripY[i]
            val y1 = stripY[i + 1]
            val depth = i / strips.toFloat()
            val amp = frame.s(1.5f) + frame.s(8f) * depth
            val off = amp * (sin(t * 1.3f + i * 0.7f) + 0.5f * sin(t * 2.7f + i * 1.9f)) + dx

            src.set(0, ((y0 - horizon) * q).toInt(), baked.width, ((y1 - horizon) * q).toInt().coerceAtLeast(((y0 - horizon) * q).toInt() + 1))
            dst.set(off - frame.s(12f), y0, w + off + frame.s(12f), y1 + 0.5f)
            canvas.drawBitmap(baked, src, dst, reflPaint)

            canvas.save()
            canvas.clipRect(0f, y0, w, y1 + 0.5f)
            canvas.translate(off - dx + signDx, 0f)
            canvas.scale(1f, -1f, 0f, horizon)
            for (sign in signs) {
                val r = sign.reflectionRect
                // Mirrored extent of this sign: skip strips it can't touch.
                val my0 = 2 * horizon - r.bottom
                val my1 = 2 * horizon - r.top
                if (my1 < y0 || my0 > y1) continue
                val a = sign.averageLevel
                if (a < 0.02f) continue
                signPaint.alpha = (a * 190 * (1f - 0.4f * depth)).toInt()
                canvas.drawBitmap(sign.reflection, null, r, signPaint)
            }
            canvas.restore()
        }

        canvas.save()
        canvas.scale(1f, -1f, 0f, horizon)
        canvas.clipRect(0f, horizon - streetH, w, horizon)
        dynamic(canvas)
        canvas.restore()

        // Glints on the wet asphalt.
        for (i in 0 until glints) {
            val a = 0.08f + 0.22f * noise1(t * 1.7f + i * 3.1f, i)
            glintPaint.color = Neon.alpha(glintColor[i], a)
            glintPaint.strokeWidth = frame.s(1.6f)
            val gx = glintX[i] + dx * 0.5f
            canvas.drawLine(gx - glintLen[i] / 2, glintY[i], gx + glintLen[i] / 2, glintY[i], glintPaint)
        }
        canvas.drawRect(0f, horizon, w, frame.height.toFloat(), fadePaint)
    }

    /** The kerb between the market and the road. */
    fun drawKerb(canvas: Canvas) {
        val top = frame.y(1352f)
        canvas.drawRect(0f, top, frame.width.toFloat(), horizon + frame.s(6f), kerbPaint)
        canvas.drawLine(0f, horizon + frame.s(6f), frame.width.toFloat(), horizon + frame.s(6f), kerbEdge)
    }
}
