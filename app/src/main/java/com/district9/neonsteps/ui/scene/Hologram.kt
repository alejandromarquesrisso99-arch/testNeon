package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.district9.neonsteps.ui.Neon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The wireframe holographic koi swimming above Tower 61 inside its projector beam.
 * Walking speeds it up: the koi feeds on footsteps.
 */
internal class Hologram(private val frame: SceneFrame, mono: Typeface, private val projX: Float, private val projY: Float) {
    private val length = frame.s(190f)

    private val conePath = Path()
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = frame.s(1.5f)
        color = Neon.alpha(Neon.CYAN, 0.35f)
    }
    private val beamPath = Path()
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = mono
        textSize = frame.s(23f)
        letterSpacing = 0.02f
        color = Neon.alpha(Neon.CYAN, 0.55f)
    }
    private val projectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF120626.toInt() }

    private val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val stations = 9
    private val top = FloatArray(stations * 2)
    private val bottom = FloatArray(stations * 2)
    private val mid = FloatArray(stations * 2)
    private val lines = FloatArray(512)
    private var lineCount = 0

    private var swimPhase = 0f
    private var travel = 0f
    private var speed = 1f
    private var glitchUntil = 0f
    private var glitchDx = 0f
    private val rng = Rng(1000)
    private var nextGlitch = 3f

    private val coneTopY = frame.y(525f)
    private val coneLeft = frame.x(342f)
    private val coneRight = frame.x(762f)

    init {
        conePath.moveTo(projX, projY)
        conePath.lineTo(coneLeft, coneTopY)
        conePath.lineTo(coneRight, coneTopY)
        conePath.close()
        conePaint.shader = LinearGradient(
            0f, projY, 0f, coneTopY,
            Neon.alpha(Neon.CYAN, 0.05f), Neon.alpha(0xFF3DA8FF.toInt(), 0.26f),
            Shader.TileMode.CLAMP,
        )
        beamPaint.shader = LinearGradient(
            0f, projY, 0f, frame.y(405f),
            Neon.alpha(Neon.CYAN, 0.30f), Neon.alpha(Neon.CYAN, 0.02f),
            Shader.TileMode.CLAMP,
        )
    }

    /** @param activity 0 when standing still, 1 at a brisk walk. */
    fun update(t: Float, dt: Float, activity: Float) {
        speed += ((1f + 1.6f * activity) - speed) * (dt * 1.5f).coerceAtMost(1f)
        swimPhase += dt * 1.1f * speed
        travel += dt * 0.32f * speed
        if (t >= nextGlitch) {
            glitchUntil = t + rng.range(0.05f, 0.16f)
            glitchDx = frame.s(rng.range(-9f, 9f))
            nextGlitch = t + rng.range(2.5f, 7f)
        }
    }

    fun draw(canvas: Canvas, t: Float, dx: Float) {
        val glitching = t < glitchUntil
        val flick = if (glitching) 0.55f else 0.86f + 0.14f * noise1(t * 9f, 7)

        // The wide projection cone with its caption, then the narrow beam feeding the koi.
        canvas.save()
        canvas.translate(dx, 0f)
        conePaint.alpha = (255 * flick).toInt()
        canvas.drawPath(conePath, conePaint)
        canvas.drawLine(coneLeft, coneTopY, coneRight, coneTopY, coneEdge)
        canvas.drawText("KOI-61 · NIGHT 1000", frame.x(376f), coneTopY + frame.s(40f), captionPaint)

        val cx = frame.x(655f) + frame.s(62f) * sin(travel)
        val cy = frame.y(405f) + frame.s(24f) * sin(travel * 2f + 0.8f)
        val vx = cos(travel)
        beamPath.reset()
        beamPath.moveTo(projX - frame.s(5f), projY)
        beamPath.lineTo(cx - length * 0.42f, cy + frame.s(30f))
        beamPath.lineTo(cx + length * 0.42f, cy + frame.s(30f))
        beamPath.lineTo(projX + frame.s(5f), projY)
        beamPath.close()
        beamPaint.alpha = (255 * flick).toInt()
        canvas.drawPath(beamPath, beamPaint)

        // Projector housing with its lens.
        canvas.drawRect(projX - frame.s(14f), projY - frame.s(3f), projX + frame.s(14f), projY + frame.s(10f), projectorPaint)
        wire.strokeWidth = frame.s(3f)
        wire.color = Neon.alpha(Neon.CYAN, 0.9f * flick)
        canvas.drawLine(projX - frame.s(8f), projY - frame.s(2f), projX + frame.s(8f), projY - frame.s(2f), wire)
        canvas.restore()

        // Facing follows the swim direction; near the turn the koi is seen head-on.
        val facing = -vx
        val squash = (0.16f + 0.84f * abs(facing).pow(0.3f)) * if (facing >= 0f) 1f else -1f
        buildWireframe(cx + dx + (if (glitching) glitchDx else 0f), cy, squash)

        drawLines(canvas, frame.s(6f), Neon.alpha(Neon.CYAN, 0.10f * flick), 0f, 0f)
        drawLines(canvas, frame.s(1.8f), Neon.alpha(Neon.MAGENTA, 0.65f * flick), frame.s(3.5f), frame.s(1.5f))
        drawLines(canvas, frame.s(1.8f), Neon.alpha(Neon.CYAN, 0.9f * flick), -frame.s(2f), 0f)
        drawLines(canvas, frame.s(0.9f), Neon.alpha(0xFFE6FDFF.toInt(), 0.75f * flick), -frame.s(1f), 0f)
    }

    private fun drawLines(canvas: Canvas, width: Float, color: Int, ox: Float, oy: Float) {
        wire.strokeWidth = width
        wire.color = color
        canvas.save()
        canvas.translate(ox, oy)
        canvas.drawLines(lines, 0, lineCount, wire)
        canvas.restore()
    }

    private fun profile(u: Float): Float {
        val body = (u / 0.8f).coerceIn(0f, 1f)
        return (0.22f * sin(PI.toFloat() * body.pow(0.72f))).coerceAtLeast(if (u > 0.05f) 0.045f else 0f)
    }

    private fun buildWireframe(cx: Float, cy: Float, sx: Float) {
        lineCount = 0
        val half = length / 2f
        val bodyEnd = 0.8f
        for (i in 0 until stations) {
            val u = i / (stations - 1f) * bodyEnd
            val sway = 0.11f * u.pow(1.5f) * sin(2f * PI.toFloat() * (u * 0.85f - swimPhase * 0.5f))
            val x = cx + (u * length - half) * sx
            val yc = cy + sway * length
            val h = profile(u) * length
            top[i * 2] = x; top[i * 2 + 1] = yc - h
            bottom[i * 2] = x; bottom[i * 2 + 1] = yc + h * 0.85f
            mid[i * 2] = x; mid[i * 2 + 1] = yc
        }
        for (i in 0 until stations - 1) {
            seg(top, i, top, i + 1)
            seg(bottom, i, bottom, i + 1)
            seg(mid, i, mid, i + 1)
            // Triangulated mesh between the outline and the lateral line.
            seg(top, i, mid, i + 1)
            seg(bottom, i, mid, i + 1)
            if (i > 0 && i % 2 == 0) seg(top, i, bottom, i)
        }
        // Forked tail fin.
        val tailBase = stations - 1
        val tx = top[tailBase * 2]
        val ty = mid[tailBase * 2 + 1]
        val sway = 0.06f * sin(2f * PI.toFloat() * (0.9f - swimPhase * 0.5f)) * length
        val tipX = tx + 0.24f * length * sx
        val upperX = tipX + 0.03f * length * sx
        val upperY = ty - 0.16f * length + sway
        val lowerY = ty + 0.14f * length + sway
        val notchX = tx + 0.13f * length * sx
        val notchY = ty + sway * 0.5f
        line(tx, top[tailBase * 2 + 1], upperX, upperY)
        line(upperX, upperY, notchX, notchY)
        line(notchX, notchY, tipX, lowerY)
        line(tipX, lowerY, tx, bottom[tailBase * 2 + 1])
        line(tx, ty, notchX, notchY)
        line(tx, ty, upperX, upperY)
        line(tx, ty, tipX, lowerY)
        // Dorsal fin.
        val d0 = 3
        val d1 = 6
        line(top[d0 * 2], top[d0 * 2 + 1], top[d1 * 2] - 0.02f * length * sx, top[d1 * 2 + 1] - 0.12f * length)
        line(top[d1 * 2] - 0.02f * length * sx, top[d1 * 2 + 1] - 0.12f * length, top[d1 * 2], top[d1 * 2 + 1])
        // Pectoral fin.
        val p = 3
        val fin = 0.5f + 0.5f * sin(swimPhase * 3f)
        line(bottom[p * 2], bottom[p * 2 + 1], bottom[p * 2] + 0.1f * length * sx, bottom[p * 2 + 1] + (0.07f + 0.04f * fin) * length)
        line(bottom[p * 2] + 0.1f * length * sx, bottom[p * 2 + 1] + (0.07f + 0.04f * fin) * length, bottom[(p + 1) * 2], bottom[(p + 1) * 2 + 1])
        // Barbels and eye.
        val nx = mid[0]
        val ny = mid[1]
        line(nx, ny, nx - 0.07f * length * sx, ny + 0.06f * length)
        line(nx + 0.02f * length * sx, ny + 0.01f * length, nx - 0.05f * length * sx, ny + 0.1f * length)
        val ex = nx + 0.09f * length * sx
        val ey = ny - 0.045f * length
        val er = 0.018f * length
        for (k in 0 until 6) {
            val a0 = k / 6f * 2f * PI.toFloat()
            val a1 = (k + 1) / 6f * 2f * PI.toFloat()
            line(ex + er * cos(a0) * abs(sx), ey + er * sin(a0), ex + er * cos(a1) * abs(sx), ey + er * sin(a1))
        }
    }

    private fun seg(a: FloatArray, i: Int, b: FloatArray, j: Int) = line(a[i * 2], a[i * 2 + 1], b[j * 2], b[j * 2 + 1])

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (lineCount + 4 > lines.size) return
        lines[lineCount++] = x0
        lines[lineCount++] = y0
        lines[lineCount++] = x1
        lines[lineCount++] = y1
    }
}
