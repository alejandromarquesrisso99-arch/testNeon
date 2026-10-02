package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.pow

/**
 * Dense diagonal rain in three depths. Drops that land on the street leave rings
 * and, close to the viewer, a little spray.
 */
internal class Rain(private val frame: SceneFrame) {
    private class Depth(
        val max: Int,
        val speed: Float,
        val length: Float,
        val width: Float,
        val alpha: Int,
        val landMin: Float,
        val landMax: Float,
    ) {
        val x = FloatArray(max)
        val y = FloatArray(max)
        val v = FloatArray(max)
        val len = FloatArray(max)
        val land = FloatArray(max)
        val lines = FloatArray(max * 4)
    }

    private val w = frame.width.toFloat()
    private val h = frame.height.toFloat()
    private val horizon = frame.horizon
    private val slope = -0.17f // dx per dy: wind from the right

    private val depths = listOf(
        Depth(150, frame.s(1150f), frame.s(26f), frame.s(1.1f), 60, horizon - frame.s(420f), horizon + frame.s(30f)),
        Depth(130, frame.s(1600f), frame.s(42f), frame.s(1.5f), 95, horizon, horizon + (h - horizon) * 0.45f),
        Depth(70, frame.s(2200f), frame.s(70f), frame.s(2.1f), 120, horizon + (h - horizon) * 0.25f, h),
    )
    private val paints = depths.map { d ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD2C4FF.toInt()
            alpha = d.alpha
            strokeWidth = d.width
            strokeCap = Paint.Cap.ROUND
        }
    }

    private val maxRings = 140
    private val rx = FloatArray(maxRings)
    private val ry = FloatArray(maxRings)
    private val rAge = FloatArray(maxRings) { 1f }
    private val rLife = FloatArray(maxRings) { 1f }
    private val rSize = FloatArray(maxRings)
    private var ringCursor = 0

    private val maxSpray = 90
    private val px = FloatArray(maxSpray)
    private val py = FloatArray(maxSpray)
    private val pvx = FloatArray(maxSpray)
    private val pvy = FloatArray(maxSpray)
    private val pAge = FloatArray(maxSpray) { 1f }
    private var sprayCursor = 0
    private val sprayPts = FloatArray(maxSpray * 2)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = frame.s(1.3f)
        color = 0xFFCDBFFF.toInt()
    }
    private val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCE4DAFF.toInt()
        strokeWidth = frame.s(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()
    private val rng = Rng(42)

    /** 0..1, eased toward from the UI's rain mode. */
    var intensity = 0.7f

    init {
        for (d in depths) for (i in 0 until d.max) respawn(d, i, initial = true)
    }

    private fun respawn(d: Depth, i: Int, initial: Boolean) {
        d.len[i] = d.length * rng.range(0.7f, 1.3f)
        d.v[i] = d.speed * rng.range(0.85f, 1.15f)
        d.land[i] = rng.range(d.landMin, d.landMax)
        d.y[i] = if (initial) rng.range(-d.len[i], d.land[i]) else rng.range(-h * 0.25f, -d.len[i])
        // Spawn across a widened band so the slant still covers the right-hand edge.
        d.x[i] = rng.range(0f, w + (d.land[i] + h * 0.25f) * -slope)
    }

    fun update(dt: Float) {
        for ((k, d) in depths.withIndex()) {
            for (i in 0 until d.max) {
                d.y[i] += d.v[i] * dt
                d.x[i] += d.v[i] * slope * dt
                if (d.y[i] >= d.land[i]) {
                    if (d.land[i] > horizon && rng.next() < intensity + 0.15f) splash(d.x[i], d.land[i], k)
                    respawn(d, i, initial = false)
                }
            }
        }
        for (i in 0 until maxRings) if (rAge[i] < rLife[i]) rAge[i] += dt
        val g = frame.s(900f)
        for (i in 0 until maxSpray) {
            if (pAge[i] >= SPRAY_LIFE) continue
            pAge[i] += dt
            pvy[i] += g * dt
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
        }
    }

    private fun splash(x: Float, y: Float, depth: Int) {
        val near = ((y - horizon) / (h - horizon)).coerceIn(0f, 1f)
        val i = ringCursor
        ringCursor = (ringCursor + 1) % maxRings
        rx[i] = x
        ry[i] = y
        rAge[i] = 0f
        rLife[i] = rng.range(0.4f, 0.7f)
        rSize[i] = frame.s(8f) + frame.s(22f) * near
        if (depth == 2) {
            repeat(2) {
                val j = sprayCursor
                sprayCursor = (sprayCursor + 1) % maxSpray
                px[j] = x
                py[j] = y
                pvx[j] = frame.s(rng.range(-90f, 90f))
                pvy[j] = -frame.s(rng.range(120f, 260f)) * (0.5f + near)
                pAge[j] = 0f
            }
        }
    }

    /** Ripple rings and spray on the street; drawn under the falling rain. */
    fun drawSplashes(canvas: Canvas) {
        for (i in 0 until maxRings) {
            if (rAge[i] >= rLife[i]) continue
            val u = rAge[i] / rLife[i]
            val r = rSize[i] * (0.15f + 0.85f * u.pow(0.6f))
            ringPaint.alpha = (150 * (1f - u).pow(1.5f)).toInt()
            oval.set(rx[i] - r, ry[i] - r * 0.28f, rx[i] + r, ry[i] + r * 0.28f)
            canvas.drawOval(oval, ringPaint)
        }
        var n = 0
        for (i in 0 until maxSpray) {
            if (pAge[i] >= SPRAY_LIFE) continue
            sprayPts[n++] = px[i]
            sprayPts[n++] = py[i]
        }
        if (n > 0) canvas.drawPoints(sprayPts, 0, n, sprayPaint)
    }

    fun drawDrops(canvas: Canvas) {
        for ((k, d) in depths.withIndex()) {
            val active = (d.max * (0.25f + 0.75f * intensity)).toInt().coerceIn(0, d.max)
            var n = 0
            for (i in 0 until active) {
                val headX = d.x[i]
                val headY = d.y[i]
                d.lines[n++] = headX
                d.lines[n++] = headY
                d.lines[n++] = headX - slope * d.len[i]
                d.lines[n++] = headY - d.len[i]
            }
            canvas.drawLines(d.lines, 0, n, paints[k])
        }
    }

    private companion object {
        const val SPRAY_LIFE = 0.35f
    }
}
