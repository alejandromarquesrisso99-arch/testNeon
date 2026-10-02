package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.Paint
import com.district9.neonsteps.ui.Neon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Easter egg: a ninja dashes out of the ramen stand, arms swept back, then — poof — the kerb
 * fills with shadow clones that run every which way before vanishing in smoke.
 * Silhouettes only, with an orange rim glow and a glinting headband.
 */
internal class NinjaSquad(private val frame: SceneFrame, private val sprites: Sprites, private val panMargin: Float) {
    private val count = 10
    private val x = FloatArray(count)
    private val dir = FloatArray(count)
    private val speed = FloatArray(count)
    private val phase = FloatArray(count)
    private val spawnAt = FloatArray(count)
    private val vanishAt = FloatArray(count)
    private val alive = BooleanArray(count)
    private val poofed = BooleanArray(count)

    private val maxPuffs = 120
    private val px = FloatArray(maxPuffs)
    private val py = FloatArray(maxPuffs)
    private val pr = FloatArray(maxPuffs)
    private val pAge = FloatArray(maxPuffs) { 1f }
    private val pLife = FloatArray(maxPuffs) { 1f }
    private var puffCursor = 0

    private val rng = Rng(77)
    private var showT = -1f
    private val feet = frame.y(1350f)

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val body = Paint(glow)
    private val headFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(glow)
    private val lines = FloatArray(4 * 16)

    val running: Boolean get() = showT >= 0f

    /** Start the show at the stall's x (screen coords, before parallax). */
    fun start(stallX: Float) {
        showT = 0f
        val w = frame.width.toFloat()
        // The original dashes out of the stall; the clones pop up all along the kerb.
        val slots = FloatArray(count - 1) { w * (0.06f + 0.88f * it / (count - 2f)) }
        for (i in slots.indices.reversed()) {
            val j = rng.int(0, i + 1)
            val tmp = slots[i]; slots[i] = slots[j]; slots[j] = tmp
        }
        for (i in 0 until count) {
            alive[i] = false
            poofed[i] = false
            phase[i] = rng.next() * 6f
            if (i == 0) {
                x[i] = stallX
                dir[i] = -1f
                speed[i] = frame.s(330f)
                spawnAt[i] = ORIGINAL_AT
            } else {
                x[i] = slots[i - 1]
                dir[i] = if (i % 2 == 0) 1f else -1f
                speed[i] = frame.s(rng.range(260f, 380f))
                spawnAt[i] = CLONES_AT + (i - 1) * CLONE_STAGGER
            }
            vanishAt[i] = VANISH_AT + i * 0.22f
        }
    }

    private fun poof(cx: Float, cy: Float, big: Boolean) {
        repeat(if (big) 10 else 7) {
            val i = puffCursor
            puffCursor = (puffCursor + 1) % maxPuffs
            val a = rng.next() * 6.283f
            val d = frame.s(rng.range(4f, if (big) 34f else 24f))
            px[i] = cx + cos(a) * d
            py[i] = cy + sin(a) * d * 0.7f
            pr[i] = frame.s(rng.range(22f, if (big) 46f else 34f))
            pAge[i] = 0f
            pLife[i] = rng.range(0.5f, 0.85f)
        }
    }

    fun update(dt: Float) {
        for (i in 0 until maxPuffs) if (pAge[i] < pLife[i]) pAge[i] += dt
        if (showT < 0f) return
        showT += dt
        val t = showT
        val pad = frame.s(70f) + panMargin
        var anyLeft = false
        for (i in 0 until count) {
            if (!alive[i] && !poofed[i] && t >= spawnAt[i]) {
                alive[i] = true
                // The original pops into the clone jutsu: one big cloud as the clones appear.
                if (i != 0) poof(x[i], feet - frame.s(34f), big = false)
            }
            if (i == 0 && alive[0] && t >= CLONES_AT && t - dt < CLONES_AT) poof(x[0], feet - frame.s(34f), big = true)
            if (alive[i] && t >= vanishAt[i]) {
                alive[i] = false
                poofed[i] = true
                poof(x[i], feet - frame.s(34f), big = false)
            }
            if (alive[i]) {
                x[i] += dir[i] * speed[i] * dt
                phase[i] += dt * speed[i] / frame.s(11f)
                if (x[i] < -pad) x[i] = frame.width + pad
                if (x[i] > frame.width + pad) x[i] = -pad
            }
            if (!poofed[i]) anyLeft = true
        }
        if (!anyLeft && pAge.indices.none { pAge[it] < pLife[it] }) showT = -1f
    }

    fun draw(canvas: Canvas, dx: Float, alpha: Float = 1f) {
        for (i in 0 until count) if (alive[i]) drawNinja(canvas, x[i] + dx, phase[i], dir[i], alpha)
        for (i in 0 until maxPuffs) {
            if (pAge[i] >= pLife[i]) continue
            val u = pAge[i] / pLife[i]
            val r = pr[i] * (0.45f + 0.55f * u)
            sprites.drawBlob(canvas, px[i] + dx, py[i] - frame.s(20f) * u, r, r * 0.85f, 0xFFE9E0FF.toInt(), 0.55f * (1f - u) * alpha)
        }
    }

    private var lineN = 0

    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float) {
        lines[lineN++] = x0; lines[lineN++] = y0; lines[lineN++] = x1; lines[lineN++] = y1
    }

    /** The run: torso pitched forward, both arms swept straight back, headband tails streaming. */
    private fun drawNinja(canvas: Canvas, fx: Float, ph: Float, d: Float, alpha: Float) {
        val u = frame.s(1f)
        val bob = abs(sin(ph)) * 2.5f * u
        val hipY = feet - 30f * u - bob
        val neckX = fx + 15f * d * u
        val neckY = feet - 55f * u - bob
        val headX = fx + 20f * d * u
        val headY = feet - 63f * u - bob

        lineN = 0
        // Legs: thigh and shin, alternating stride.
        for (k in 0..1) {
            val s = sin(ph + k * 3.1416f)
            val kneeX = fx + d * (10f * s + 4f) * u
            val kneeY = hipY + 15f * u - abs(s) * 3f * u
            val footX = kneeX + d * (-6f + 8f * s) * u
            val footY = feet - if (s > 0f) 4f * u * s else 0f
            seg(fx, hipY, kneeX, kneeY)
            seg(kneeX, kneeY, footX, footY)
        }
        // Arms swept straight back.
        val shoulderX = fx + 12f * d * u
        val shoulderY = feet - 51f * u - bob
        seg(shoulderX, shoulderY, shoulderX - 31f * d * u, shoulderY + 3f * u + sin(ph) * u)
        seg(shoulderX, shoulderY + 2f * u, shoulderX - 29f * d * u, shoulderY + 8f * u - sin(ph) * u)
        val limbs = lineN
        // Headband tails streaming behind.
        val knotX = headX - 6f * d * u
        val knotY = headY - 2f * u
        val flap = sin(ph * 1.7f) * 3f * u
        seg(knotX, knotY, knotX - 16f * d * u, knotY - 3f * u + flap)
        seg(knotX, knotY + 1f * u, knotX - 13f * d * u, knotY + 5f * u - flap)
        val tails = lineN - limbs

        // Orange rim glow, then the silhouette on top.
        glow.color = Neon.alpha(ORANGE, 0.35f * alpha)
        glow.strokeWidth = 10f * u
        canvas.drawLines(lines, 0, limbs, glow)
        canvas.drawLine(fx, hipY, neckX, neckY, glow.apply { strokeWidth = 14f * u })
        headFill.color = Neon.alpha(ORANGE, 0.3f * alpha)
        canvas.drawCircle(headX, headY, 10f * u, headFill)

        body.color = Neon.alpha(SILHOUETTE, alpha)
        body.strokeWidth = 5.5f * u
        canvas.drawLines(lines, 0, limbs, body)
        body.strokeWidth = 9f * u
        canvas.drawLine(fx, hipY, neckX, neckY, body)
        headFill.color = Neon.alpha(SILHOUETTE, alpha)
        canvas.drawCircle(headX, headY, 7f * u, headFill)

        band.color = Neon.alpha(0xFF3A5DFF.toInt(), alpha)
        band.strokeWidth = 2.2f * u
        canvas.drawLines(lines, limbs, tails, band)
        // The plate on the forehead catches the neon.
        band.color = Neon.alpha(0xFFDDF6FF.toInt(), alpha)
        band.strokeWidth = 3f * u
        canvas.drawLine(headX - 1f * d * u, headY - 3f * u, headX + 6f * d * u, headY - 3f * u, band)

        // Speed lines trailing the runner.
        band.color = Neon.alpha(ORANGE, 0.45f * alpha)
        band.strokeWidth = 1.6f * u
        for (k in 0 until 3) {
            val ly = feet - (24f + k * 14f) * u
            val lx = fx - d * (28f + k * 6f) * u
            canvas.drawLine(lx, ly, lx - d * (22f - k * 4f) * u, ly, band)
        }
    }

    private companion object {
        const val ORIGINAL_AT = 0.7f
        const val CLONES_AT = 1.6f
        const val CLONE_STAGGER = 0.12f
        const val VANISH_AT = 7.4f
        const val ORANGE = 0xFFFF8A1E.toInt()
        const val SILHOUETTE = 0xFF0B0414.toInt()
    }
}
