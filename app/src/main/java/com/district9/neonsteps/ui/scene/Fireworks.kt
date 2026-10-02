package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import com.district9.neonsteps.ui.Neon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Celebration fireworks over the far skyline, for the rest of the day once the goal is met.
 * Shells launch from behind the towers, so the city stands in silhouette against them.
 * Everything is pooled: no allocation per frame.
 */
internal class Fireworks(private val frame: SceneFrame, private val sprites: Sprites) {

    private class Shell {
        var active = false
        var x = 0f
        var y = 0f
        var vy = 0f
        var targetY = 0f
        var type = 0
        var color = 0
        var color2 = 0
    }

    private class Burst(val capacity: Int) {
        var active = false
        var type = 0
        var color = 0
        var color2 = 0
        var cx = 0f
        var cy = 0f
        var age = 0f
        var life = 1f
        var drag = 1.6f
        var gravity = 0f
        var count = 0
        val x = FloatArray(capacity)
        val y = FloatArray(capacity)
        val vx = FloatArray(capacity)
        val vy = FloatArray(capacity)
        val lines = FloatArray(capacity * 4)
        val lines2 = FloatArray(capacity * 4)
    }

    private val shells = Array(8) { Shell() }
    private val bursts = Array(10) { Burst(110) }
    private val rng = Rng(2026)

    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private val palette = intArrayOf(Neon.MAGENTA, Neon.CYAN, Neon.YELLOW, Neon.VIOLET, 0xFFFF6BB5.toInt(), 0xFFFFFFFF.toInt())
    private val gold = 0xFFFFD36B.toInt()

    private val launchY = frame.y(940f)
    // Burst in the open sky between the title block and the rooftops.
    private val highest = frame.height * 0.17f
    private val lowest = maxOf(frame.y(520f), highest + frame.s(160f))

    /** While true, shells keep launching; switching off lets the sky empty out naturally. */
    var active = false
        set(value) {
            if (value && !field) nextLaunch = time + 0.3f
            field = value
        }

    private var time = 0f
    private var nextLaunch = 0f

    /** An opening salvo, for the moment the goal falls. */
    fun salvo() {
        repeat(4) { i -> launch(frame.width * (0.18f + 0.21f * i) + frame.s(rng.range(-40f, 40f))) }
        nextLaunch = time + 1.4f
    }

    val hasSomethingToDraw: Boolean
        get() = active || shells.any { it.active } || bursts.any { it.active }

    private fun launch(x: Float = rng.range(frame.width * 0.08f, frame.width * 0.92f)) {
        val shell = shells.firstOrNull { !it.active } ?: return
        shell.active = true
        shell.x = x
        shell.y = launchY
        shell.targetY = rng.range(highest, lowest)
        shell.vy = -frame.s(rng.range(620f, 820f))
        val roll = rng.next()
        shell.type = when {
            roll < 0.55f -> PEONY
            roll < 0.8f -> RING
            else -> WILLOW
        }
        shell.color = palette[rng.int(0, palette.size)]
        shell.color2 = if (rng.chance(0.4f)) palette[rng.int(0, palette.size)] else shell.color
    }

    private fun explode(shell: Shell) {
        shell.active = false
        val b = bursts.firstOrNull { !it.active } ?: bursts.maxByOrNull { it.age } ?: return
        b.active = true
        b.type = shell.type
        b.color = if (shell.type == WILLOW) gold else shell.color
        b.color2 = if (shell.type == WILLOW) gold else shell.color2
        b.cx = shell.x
        b.cy = shell.y
        b.age = 0f
        val size = rng.range(0.8f, 1.3f)
        val speed = frame.s(390f) * size
        when (shell.type) {
            WILLOW -> {
                b.count = 70
                b.life = rng.range(2.6f, 3.2f)
                b.drag = 1.1f
                b.gravity = frame.s(95f)
            }
            RING -> {
                b.count = 56
                b.life = rng.range(1.5f, 1.9f)
                b.drag = 1.7f
                b.gravity = frame.s(60f)
            }
            else -> {
                b.count = 100
                b.life = rng.range(1.6f, 2.2f)
                b.drag = 1.6f
                b.gravity = frame.s(70f)
            }
        }
        // A ring is seen at a random tilt: squash it vertically.
        val tilt = rng.range(0.35f, 1f)
        for (i in 0 until b.count) {
            val a: Float
            val v: Float
            if (b.type == RING) {
                a = i / b.count.toFloat() * 2f * PI.toFloat()
                v = speed
            } else {
                a = rng.next() * 2f * PI.toFloat()
                // sqrt fills the disc evenly, like a sphere seen from afar.
                v = speed * (0.35f + 0.65f * sqrt(rng.next()))
            }
            b.x[i] = b.cx
            b.y[i] = b.cy
            b.vx[i] = cos(a) * v
            b.vy[i] = sin(a) * v * (if (b.type == RING) tilt else 1f)
        }
    }

    fun update(dt: Float) {
        time += dt
        if (active && time >= nextLaunch) {
            launch()
            if (rng.chance(0.3f)) launch() // now and then two go up together
            nextLaunch = time + rng.range(0.45f, 1.3f)
        }
        for (s in shells) {
            if (!s.active) continue
            s.y += s.vy * dt
            s.vy *= exp(-0.5f * dt)
            if (s.y <= s.targetY) explode(s)
        }
        for (b in bursts) {
            if (!b.active) continue
            b.age += dt
            if (b.age >= b.life) {
                b.active = false
                continue
            }
            val damp = exp(-b.drag * dt)
            for (i in 0 until b.count) {
                b.vx[i] *= damp
                b.vy[i] = b.vy[i] * damp + b.gravity * dt
                b.x[i] += b.vx[i] * dt
                b.y[i] += b.vy[i] * dt
            }
        }
    }

    /** Drawn in the sky, before the skyline, so towers hide the launches. */
    fun draw(canvas: Canvas, dx: Float) {
        for (s in shells) {
            if (!s.active) continue
            trail.color = Neon.alpha(Neon.mix(s.color, 0xFFFFFFFF.toInt(), 0.5f), 0.8f)
            trail.strokeWidth = frame.s(2.2f)
            canvas.drawLine(s.x + dx, s.y, s.x + dx, s.y - s.vy * 0.07f, trail)
            sprites.drawBlob(canvas, s.x + dx, s.y, frame.s(10f), frame.s(10f), s.color, 0.8f)
        }
        for (b in bursts) {
            if (!b.active) continue
            val u = b.age / b.life
            // The flash lights up the smog around the shell.
            if (b.age < 0.4f) {
                val f = 1f - b.age / 0.4f
                sprites.drawBlob(canvas, b.cx + dx, b.cy, frame.s(320f), frame.s(240f), b.color, 0.3f * f)
                if (b.age < 0.1f) {
                    val g = 1f - b.age / 0.1f
                    sprites.drawBlob(canvas, b.cx + dx, b.cy, frame.s(22f), frame.s(22f), 0xFFFFFFFF.toInt(), 0.85f * g)
                }
            }
            val fade = (1f - u).pow(if (b.type == WILLOW) 0.8f else 1.4f)
            // Willows crackle as they die.
            val crackle = if (b.type == WILLOW && u > 0.55f) 0.55f + 0.45f * hash(floor(b.age * 24f).toInt(), b.count) else 1f
            var n1 = 0
            var n2 = 0
            val streak = if (b.type == WILLOW) 0.12f else 0.06f
            for (i in 0 until b.count) {
                val x0 = b.x[i] + dx
                val y0 = b.y[i]
                val x1 = x0 - b.vx[i] * streak
                val y1 = y0 - b.vy[i] * streak
                if (i % 2 == 0 || b.color2 == b.color) {
                    b.lines[n1++] = x0; b.lines[n1++] = y0; b.lines[n1++] = x1; b.lines[n1++] = y1
                } else {
                    b.lines2[n2++] = x0; b.lines2[n2++] = y0; b.lines2[n2++] = x1; b.lines2[n2++] = y1
                }
            }
            drawSparks(canvas, b.lines, n1, b.color, fade * crackle, b.type)
            if (n2 > 0) drawSparks(canvas, b.lines2, n2, b.color2, fade * crackle, b.type)
        }
    }

    private fun drawSparks(canvas: Canvas, lines: FloatArray, n: Int, color: Int, alpha: Float, type: Int) {
        if (n == 0 || alpha <= 0.01f) return
        spark.color = Neon.alpha(color, 0.3f * alpha)
        spark.strokeWidth = frame.s(if (type == WILLOW) 5f else 8f)
        canvas.drawLines(lines, 0, n, spark)
        spark.color = Neon.alpha(Neon.mix(color, 0xFFFFFFFF.toInt(), 0.35f), alpha)
        spark.strokeWidth = frame.s(if (type == WILLOW) 2f else 3f)
        canvas.drawLines(lines, 0, n, spark)
    }

    private companion object {
        const val PEONY = 0
        const val RING = 1
        const val WILLOW = 2
    }
}
