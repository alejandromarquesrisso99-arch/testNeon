package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import com.district9.neonsteps.ui.Neon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Pooled spark particles: electrical sparks from a sign, or the glitter a golden koi sheds. */
internal class Sparks(private val frame: SceneFrame, private val capacity: Int = 200) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val age = FloatArray(capacity) { 1f }
    private val life = FloatArray(capacity) { 1f }
    private val gravity = FloatArray(capacity)
    private val color = IntArray(capacity)
    private var cursor = 0
    private val rng = Rng(404)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    /**
     * @param speed px/s at launch; [up] biases the spray upward like sparks off a shorted tube.
     */
    fun emit(px: Float, py: Float, count: Int, c: Int, speed: Float, g: Float, lifetime: Float, up: Boolean = true) {
        repeat(count) {
            val i = cursor
            cursor = (cursor + 1) % capacity
            val a = if (up) -PI.toFloat() / 2f + rng.range(-1.2f, 1.2f) else rng.next() * 2f * PI.toFloat()
            val v = speed * rng.range(0.35f, 1f)
            x[i] = px
            y[i] = py
            vx[i] = cos(a) * v
            vy[i] = sin(a) * v
            age[i] = 0f
            life[i] = lifetime * rng.range(0.6f, 1.2f)
            gravity[i] = g
            color[i] = c
        }
    }

    fun update(dt: Float) {
        val damp = exp(-1.4f * dt)
        for (i in 0 until capacity) {
            if (age[i] >= life[i]) continue
            age[i] += dt
            vx[i] *= damp
            vy[i] = vy[i] * damp + gravity[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    fun draw(canvas: Canvas, dx: Float) {
        for (i in 0 until capacity) {
            if (age[i] >= life[i]) continue
            val u = age[i] / life[i]
            val twinkle = 0.6f + 0.4f * hash((age[i] * 30f).toInt(), i)
            val a = (1f - u).pow(1.2f) * twinkle
            paint.strokeWidth = frame.s(5f)
            paint.color = Neon.alpha(color[i], 0.25f * a)
            val x0 = x[i] + dx
            val x1 = x0 - vx[i] * 0.03f
            val y1 = y[i] - vy[i] * 0.03f
            canvas.drawLine(x0, y[i], x1, y1, paint)
            paint.strokeWidth = frame.s(2f)
            paint.color = Neon.alpha(Neon.mix(color[i], 0xFFFFFFFF.toInt(), 0.5f), a)
            canvas.drawLine(x0, y[i], x1, y1, paint)
        }
    }
}
