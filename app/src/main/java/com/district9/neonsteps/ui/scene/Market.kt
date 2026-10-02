package com.district9.neonsteps.ui.scene

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.district9.neonsteps.ui.Neon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Market stalls under striped awnings, their string lights and steam, and umbrella-carrying pedestrians. */
internal class Market(private val frame: SceneFrame, private val sprites: Sprites, margin: Float) {

    private class StallSpec(
        val l: Float, val r: Float,
        val stripeA: Int, val stripeB: Int, val accent: Int,
        val bulbColors: IntArray, val steam: FloatArray,
    )

    private val specs = listOf(
        StallSpec(-24f, 116f, 0xFFC2185B.toInt(), 0xFF3A0F6E.toInt(), Neon.MAGENTA,
            intArrayOf(Neon.YELLOW, Neon.MAGENTA, Neon.CYAN), floatArrayOf()),
        StallSpec(300f, 546f, 0xFF4B3CC4.toInt(), 0xFF1E1250.toInt(), Neon.CYAN,
            intArrayOf(Neon.CYAN, Neon.YELLOW, Neon.MAGENTA, 0xFFFFFFFF.toInt()), floatArrayOf(372f, 468f)),
        StallSpec(790f, 1040f, 0xFFD4B417.toInt(), 0xFF3A1A6A.toInt(), Neon.YELLOW,
            intArrayOf(Neon.YELLOW, Neon.MAGENTA, Neon.CYAN, 0xFFFFFFFF.toInt()), floatArrayOf(880f, 968f)),
    )

    private val awningTop = frame.y(1258f)
    private val awningBottom = frame.y(1292f)
    private val ground = frame.y(1352f)

    val bitmap: Bitmap
    val bitmapLeft = -margin
    val bitmapTop = frame.y(1225f)

    // String lights.
    private val bulbX: FloatArray
    private val bulbY: FloatArray
    private val bulbColor: IntArray
    private val bulbPhase: FloatArray

    // Steam particles.
    private val steamSources: FloatArray
    private val maxSteam = 90
    private val sx = FloatArray(maxSteam)
    private val sy = FloatArray(maxSteam)
    private val svx = FloatArray(maxSteam)
    private val svy = FloatArray(maxSteam)
    private val sAge = FloatArray(maxSteam) { 1f }
    private val sLife = FloatArray(maxSteam) { 1f }
    private var steamAccumulator = 0f
    private var steamCursor = 0

    private val rng = Rng(9)

    init {
        val w = (frame.width + 2 * margin).toInt().coerceAtLeast(1)
        val h = (ground + frame.s(10f) - bitmapTop).toInt().coerceAtLeast(1)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.translate(-bitmapLeft, -bitmapTop)
        for (spec in specs) bakeStall(c, spec)

        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val cs = ArrayList<Int>()
        for (spec in specs) {
            val l = frame.x(spec.l) + frame.s(14f)
            val r = frame.x(spec.r) - frame.s(14f)
            val n = ((r - l) / frame.s(30f)).toInt().coerceAtLeast(2)
            for (i in 0..n) {
                val u = i / n.toFloat()
                xs += l + (r - l) * u
                ys += awningBottom + frame.s(10f) + frame.s(7f) * sin(PI.toFloat() * u)
                cs += spec.bulbColors[i % spec.bulbColors.size]
            }
        }
        bulbX = xs.toFloatArray()
        bulbY = ys.toFloatArray()
        bulbColor = cs.toIntArray()
        bulbPhase = FloatArray(bulbX.size) { rng.next() * 10f }
        steamSources = specs.flatMap { s -> s.steam.map { frame.x(it) } }.toFloatArray()
    }

    private fun bakeStall(c: Canvas, spec: StallSpec) {
        val l = frame.x(spec.l)
        val r = frame.x(spec.r)
        val interior = Paint().apply {
            shader = LinearGradient(
                0f, awningBottom, 0f, ground,
                Neon.alpha(Neon.mix(spec.accent, 0xFFFFB070.toInt(), 0.55f), 0.55f),
                Neon.alpha(Neon.mix(spec.accent, 0xFF2A0A3A.toInt(), 0.6f), 0.25f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(l + frame.s(8f), awningBottom, r - frame.s(8f), ground, interior)

        // Vendor and pots in silhouette.
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0312.toInt() }
        val vx = l + (r - l) * 0.62f
        c.drawCircle(vx, awningBottom + frame.s(16f), frame.s(8f), dark)
        c.drawRoundRect(RectF(vx - frame.s(15f), awningBottom + frame.s(24f), vx + frame.s(15f), ground), frame.s(8f), frame.s(8f), dark)
        for (sxRef in spec.steam) {
            val px = frame.x(sxRef)
            c.drawRoundRect(RectF(px - frame.s(16f), ground - frame.s(44f), px + frame.s(16f), ground - frame.s(24f)), frame.s(4f), frame.s(4f), dark)
        }

        // Counter with a lit edge.
        val counter = Paint().apply { color = 0xFF0B0316.toInt() }
        c.drawRect(l + frame.s(4f), ground - frame.s(26f), r - frame.s(4f), ground, counter)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Neon.alpha(spec.accent, 0.85f)
            strokeWidth = frame.s(2f)
        }
        c.drawLine(l + frame.s(4f), ground - frame.s(26f), r - frame.s(4f), ground - frame.s(26f), edge)

        // Posts.
        val post = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A0B2A.toInt()
            strokeWidth = frame.s(4f)
        }
        c.drawLine(l + frame.s(7f), awningTop, l + frame.s(7f), ground, post)
        c.drawLine(r - frame.s(7f), awningTop, r - frame.s(7f), ground, post)

        // Striped, scalloped awning, shaded darker toward its top.
        val stripes = ((r - l) / frame.s(24f)).toInt().coerceAtLeast(3)
        val sw = (r - l) / stripes
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG)
        val save = c.saveLayer(l - sw, awningTop - frame.s(4f), r + sw, awningBottom + sw, null)
        for (i in 0 until stripes) {
            stripe.color = if (i % 2 == 0) spec.stripeA else spec.stripeB
            val x0 = l + i * sw
            val path = Path().apply {
                moveTo(x0 + sw * 0.06f, awningTop)
                lineTo(x0 + sw * 1.06f, awningTop)
                lineTo(x0 + sw, awningBottom)
                lineTo(x0, awningBottom)
                close()
            }
            c.drawPath(path, stripe)
            c.drawCircle(x0 + sw / 2f, awningBottom, sw / 2f, stripe)
        }
        val shade = Paint().apply {
            shader = LinearGradient(
                0f, awningTop, 0f, awningBottom + sw / 2,
                0xB0050110.toInt(), 0x10050110,
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(l - sw, awningTop - frame.s(4f), r + sw, awningBottom + sw, shade)
        c.restoreToCount(save)

        // Kerb glow below the stall.
        val kerb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.accent
            strokeWidth = frame.s(2.5f)
        }
        val kerbGlow = Paint(kerb).apply {
            strokeWidth = frame.s(7f)
            alpha = 120
            maskFilter = BlurMaskFilter(frame.s(8f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawLine(l + frame.s(22f), ground + frame.s(2f), r - frame.s(22f), ground + frame.s(2f), kerbGlow)
        c.drawLine(l + frame.s(22f), ground + frame.s(2f), r - frame.s(22f), ground + frame.s(2f), kerb)
    }

    fun update(dt: Float) {
        steamAccumulator += dt * 5f * steamSources.size
        while (steamAccumulator >= 1f && steamSources.isNotEmpty()) {
            steamAccumulator -= 1f
            val i = steamCursor
            steamCursor = (steamCursor + 1) % maxSteam
            val src = steamSources[rng.int(0, steamSources.size)]
            sx[i] = src + frame.s(rng.range(-10f, 10f))
            sy[i] = ground - frame.s(46f)
            svx[i] = frame.s(rng.range(-14f, 3f))
            svy[i] = -frame.s(rng.range(26f, 44f))
            sAge[i] = 0f
            sLife[i] = rng.range(2.0f, 3.4f)
        }
        for (i in 0 until maxSteam) {
            if (sAge[i] >= sLife[i]) continue
            sAge[i] += dt
            sx[i] += svx[i] * dt
            sy[i] += svy[i] * dt
            svy[i] *= 1f - 0.25f * dt
        }
    }

    private val shade = Paint()

    fun drawStalls(canvas: Canvas, t: Float, dx: Float, blackoutT: Float = -1f) {
        canvas.drawBitmap(bitmap, bitmapLeft + dx, bitmapTop, null)
        if (blackoutT >= 0f) {
            // Counters and awnings lose their light with the grid.
            shade.color = Neon.alpha(0xFF040010.toInt(), 0.75f * (1f - Blackout.power(blackoutT, 0.45f)))
            canvas.drawRect(bitmapLeft + dx, bitmapTop, bitmapLeft + dx + bitmap.width, bitmapTop + bitmap.height, shade)
        }
        drawBulbs(canvas, t, dx, 1f, blackoutT)
    }

    fun drawBulbs(canvas: Canvas, t: Float, dx: Float, alpha: Float, blackoutT: Float = -1f) {
        for (i in bulbX.indices) {
            // During a blackout's restore, the string lights come back in a run.
            val grid = if (blackoutT >= 0f) Blackout.power(blackoutT, 0.25f + 0.4f * i / bulbX.size) else 1f
            if (grid <= 0f) continue
            val p = bulbPhase[i]
            // Most bulbs twinkle gently; a few blink out entirely now and then.
            val blink = if (hash((t * 2f + p).toInt(), i) > 0.93f) 0.15f else 1f
            val a = (0.72f + 0.28f * sin(t * 2.3f + p)) * blink * alpha * grid
            sprites.drawBlob(canvas, bulbX[i] + dx, bulbY[i], frame.s(16f), frame.s(16f), bulbColor[i], 0.55f * a)
            sprites.drawBlob(canvas, bulbX[i] + dx, bulbY[i], frame.s(4.5f), frame.s(4.5f), Neon.mix(bulbColor[i], 0xFFFFFFFF.toInt(), 0.6f), a)
        }
    }

    fun drawSteam(canvas: Canvas, dx: Float) {
        for (i in 0 until maxSteam) {
            if (sAge[i] >= sLife[i]) continue
            val u = sAge[i] / sLife[i]
            val r = frame.s(10f) + frame.s(34f) * u
            sprites.drawBlob(canvas, sx[i] + dx, sy[i], r, r * 0.8f, 0xFFD9C9FF.toInt(), 0.16f * sin(PI.toFloat() * u))
        }
    }

    val groundY: Float get() = ground
}

/** People walking the kerb under umbrellas whose shafts glow like tubes. */
/** @param panMargin extra room past the screen edges, so walkers don't pop in when the street is panned. */
internal class Pedestrians(private val frame: SceneFrame, private val sprites: Sprites, private val panMargin: Float) {
    private val count = 3
    private val x = floatArrayOf(frame.x(560f), frame.x(650f), frame.x(180f))
    private val dir = floatArrayOf(1f, -1f, 1f)
    private val speed = floatArrayOf(frame.s(34f), frame.s(28f), frame.s(40f))
    private val phase = floatArrayOf(0f, 1.7f, 3.1f)
    private val color = intArrayOf(Neon.CYAN, Neon.MAGENTA, Neon.VIOLET)
    private val feet = frame.y(1350f)

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0414.toInt() }
    private val leg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B0414.toInt()
        strokeWidth = frame.s(4.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val canopy = Path()
    private val rect = RectF()

    fun update(dt: Float, width: Int) {
        for (i in 0 until count) {
            x[i] += dir[i] * speed[i] * dt
            phase[i] += dt * speed[i] / frame.s(9f)
            val pad = frame.s(90f) + panMargin
            if (x[i] > width + pad) x[i] = -pad
            if (x[i] < -pad) x[i] = width + pad
        }
    }

    fun draw(canvas: Canvas, dx: Float, alpha: Float = 1f) {
        for (i in 0 until count) drawOne(canvas, x[i] + dx, phase[i], dir[i], color[i], alpha)
    }

    private fun drawOne(canvas: Canvas, fx: Float, ph: Float, d: Float, c: Int, alpha: Float) {
        val swing = sin(ph) * frame.s(7f)
        val bob = abs(sin(ph)) * frame.s(2f)
        val hipY = feet - frame.s(30f) - bob

        sprites.drawBlob(canvas, fx, feet - frame.s(58f), frame.s(40f), frame.s(36f), c, 0.22f * alpha)
        body.alpha = (255 * alpha).toInt()
        leg.alpha = body.alpha
        canvas.drawLine(fx, hipY, fx + swing, feet, leg)
        canvas.drawLine(fx, hipY, fx - swing, feet, leg)
        rect.set(fx - frame.s(9f), feet - frame.s(58f) - bob, fx + frame.s(9f), hipY + frame.s(4f))
        canvas.drawRoundRect(rect, frame.s(6f), frame.s(6f), body)
        canvas.drawCircle(fx, feet - frame.s(64f) - bob, frame.s(6.5f), body)

        // Umbrella: translucent canopy, glowing rim, glowing shaft.
        val ux = fx + d * frame.s(3f)
        val cy = feet - frame.s(84f) - bob
        val rx = frame.s(32f)
        val ry = frame.s(15f)
        canopy.reset()
        rect.set(ux - rx, cy - ry, ux + rx, cy + ry)
        canopy.arcTo(rect, 180f, 180f, true)
        val scallops = 4
        val sw = 2 * rx / scallops
        for (k in scallops - 1 downTo 0) {
            rect.set(ux - rx + k * sw, cy - sw * 0.25f, ux - rx + (k + 1) * sw, cy + sw * 0.25f)
            canopy.arcTo(rect, 0f, 180f, false) // scallops hang below the rim
        }
        canopy.close()
        fill.color = Neon.alpha(c, 0.18f * alpha)
        canvas.drawPath(canopy, fill)
        tube.color = Neon.alpha(c, 0.22f * alpha)
        tube.strokeWidth = frame.s(7f)
        canvas.drawPath(canopy, tube)
        tube.color = Neon.alpha(c, 0.95f * alpha)
        tube.strokeWidth = frame.s(2.2f)
        canvas.drawPath(canopy, tube)

        val handY = feet - frame.s(46f) - bob
        val topY = cy - ry
        tube.color = Neon.alpha(c, 0.25f * alpha)
        tube.strokeWidth = frame.s(7f)
        canvas.drawLine(ux, topY, ux, handY, tube)
        tube.color = Neon.alpha(c, alpha)
        tube.strokeWidth = frame.s(2.6f)
        canvas.drawLine(ux, topY, ux, handY, tube)
        tube.color = Neon.alpha(0xFFFFFFFF.toInt(), 0.85f * alpha)
        tube.strokeWidth = frame.s(1f)
        canvas.drawLine(ux, topY, ux, handY, tube)
    }
}
