package com.district9.neonsteps.ui.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
 * Walking speeds it up: the koi feeds on footsteps. Tapped three times, it slips the beam
 * and roams the sky as a golden koi for a minute, shedding glitter.
 */
internal class Hologram(
    private val frame: SceneFrame,
    mono: Typeface,
    private val projX: Float,
    private val projY: Float,
    private val sprites: Sprites,
) {
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
        strokeJoin = Paint.Join.ROUND
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
    private var kick = 0f
    private var glitchUntil = 0f
    private var glitchDx = 0f
    private val rng = Rng(1000)
    private var nextGlitch = 3f

    private val coneTopY = frame.y(525f)
    private val coneLeft = frame.x(342f)
    private val coneRight = frame.x(762f)

    // Where the koi is (before parallax) and how fast it's moving sideways.
    private var kx = beamX(0f)
    private var ky = beamY(0f)
    private var kvx = -1f

    // 0 = projected cyan koi in its beam … 1 = golden koi loose in the sky.
    private var gold = 0f
    private var goldenUntil = -1f
    private var wildPhase = 0f
    private var glitterDebt = 0f
    private val skyTop = frame.height * 0.2f
    private val skyBottom = maxOf(frame.y(600f), skyTop + frame.s(120f))

    // Ramen easter egg: the projector swaps the koi for a giant bowl.
    private var ramenUntil = -1f
    private var ramen = 0f
    private var bowlVisible = false
    private val bowl = Path()
    private val swirl = Path()
    private val steam = Path()
    private val broth = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    // The koi's colours: cyan by default, or an unlocked style.
    var koiMain = Neon.CYAN
    var koiChroma = Neon.MAGENTA
    var koiCore = 0xFFE6FDFF.toInt()

    /** Streak perk: a smaller magenta koi that shares the beam. */
    var companion = false
    private var k2x = beamX(2.6f)
    private var k2y = beamY(2.6f)
    private var k2vx = 1f

    /** Golden glitter trail, in sky coordinates. */
    val glitter = Sparks(frame, 160)

    /** The koi's on-screen hit box (parallax included), refreshed every frame. */
    val koiRect = RectF()

    /** Out of the beam, or still swimming back to it. */
    fun isLoose(t: Float): Boolean = t < goldenUntil || gold > 0f

    /** How much the koi has left the beam (0..1), for draw ordering. */
    val freedom: Float get() = gold

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

    private fun beamX(tr: Float) = frame.x(655f) + frame.s(62f) * sin(tr)
    private fun beamY(tr: Float) = frame.y(405f) + frame.s(24f) * sin(tr * 2f + 0.8f)

    /** A tap on the koi: it glitches and darts. */
    fun poke(t: Float) {
        glitchUntil = t + 0.18f
        glitchDx = frame.s(rng.range(-14f, 14f))
        kick = 2.2f
    }

    /** Project a bowl of ramen instead of the koi for [seconds] (tapping again tops it up). */
    fun serveRamen(t: Float, seconds: Float) {
        ramenUntil = t + seconds
    }

    fun isServingRamen(t: Float): Boolean = t < ramenUntil

    /** Let the koi out of the beam for [seconds]. */
    fun release(t: Float, seconds: Float) {
        goldenUntil = t + seconds
    }

    /** @param activity 0 when standing still, 1 at a brisk walk. */
    fun update(t: Float, dt: Float, activity: Float) {
        kick = (kick - dt * 2.5f).coerceAtLeast(0f)
        speed += ((1f + 1.6f * activity) - speed) * (dt * 1.5f).coerceAtMost(1f)
        val pace = speed + kick
        val freeTarget = if (t < goldenUntil) 1f else 0f
        gold += (freeTarget - gold).coerceIn(-dt / 1.8f, dt / 1.8f)
        swimPhase += dt * 1.1f * pace * (1f + 1.3f * gold)
        travel += dt * 0.32f * pace
        if (gold > 0f) wildPhase += dt * 0.5f * pace

        // Blend from the beam orbit to a Lissajous loop across the whole sky.
        val e = smoothstep(0f, 1f, gold)
        val wx = frame.width / 2f + frame.width * 0.36f * sin(wildPhase * 0.9f + 0.6f)
        val wy = (skyTop + skyBottom) / 2f + (skyBottom - skyTop) / 2f * sin(wildPhase * 1.7f)
        val nx = lerp(beamX(travel), wx, e)
        val ny = lerp(beamY(travel), wy, e)
        if (dt > 0f) kvx += ((nx - kx) / dt - kvx) * (dt * 8f).coerceAtMost(1f)
        kx = nx
        ky = ny

        // The companion circles the other way round, higher up the beam.
        val c2 = -travel * 1.15f + 2.6f
        val n2x = frame.x(650f) + frame.s(85f) * sin(c2)
        val n2y = frame.y(322f) + frame.s(18f) * sin(c2 * 2f + 0.3f)
        if (dt > 0f) k2vx += ((n2x - k2x) / dt - k2vx) * (dt * 8f).coerceAtMost(1f)
        k2x = n2x
        k2y = n2y

        if (gold > 0.15f) {
            glitterDebt += dt * 40f * gold
            val dir = if (kvx < 0f) 1f else -1f // the tail trails behind the swim direction
            while (glitterDebt >= 1f) {
                glitterDebt -= 1f
                glitter.emit(kx + dir * length * 0.45f, ky, 1, if (rng.chance(0.3f)) 0xFFFFFFFF.toInt() else GOLD, frame.s(40f), frame.s(60f), 1.4f, up = false)
            }
        }
        glitter.update(dt)

        // The swap glitches in and out, like a channel change.
        ramen += ((if (t < ramenUntil) 1f else 0f) - ramen).coerceIn(-dt / 0.6f, dt / 0.6f)
        bowlVisible = when {
            ramen <= 0f -> false
            ramen >= 1f -> true
            else -> hash(kotlin.math.floor(t * 16f).toInt(), 61) < ramen
        }

        if (t >= nextGlitch) {
            glitchUntil = t + rng.range(0.05f, 0.16f)
            glitchDx = frame.s(rng.range(-9f, 9f))
            nextGlitch = t + rng.range(2.5f, 7f)
        }
    }

    private val bowlX = frame.x(655f)
    private fun bowlY(t: Float) = frame.y(395f) + frame.s(6f) * sin(t * 1.3f)

    /** The ramen bowl hologram: bowl, broth, egg, nori, a spinning narutomaki, noodles and steam. */
    fun drawRamen(canvas: Canvas, t: Float, dx: Float, power: Float) {
        if (!bowlVisible) return
        val f = flicker(t) * power
        if (f <= 0.01f) return
        val r = frame.s(80f)
        val bx = bowlX + dx
        val by = bowlY(t)

        // Broth.
        oval.set(bx - 0.86f * r, by - 0.2f * r, bx + 0.86f * r, by + 0.2f * r)
        broth.color = Neon.alpha(0xFFFFB347.toInt(), 0.2f * f)
        canvas.drawOval(oval, broth)

        bowl.reset()
        oval.set(bx - r, by - 0.28f * r, bx + r, by + 0.28f * r)
        bowl.addOval(oval, Path.Direction.CW)
        bowl.moveTo(bx - r, by)
        bowl.cubicTo(bx - r, by + 0.75f * r, bx - 0.45f * r, by + 0.95f * r, bx, by + 0.95f * r)
        bowl.cubicTo(bx + 0.45f * r, by + 0.95f * r, bx + r, by + 0.75f * r, bx + r, by)
        bowl.moveTo(bx - 0.3f * r, by + 0.93f * r)
        bowl.lineTo(bx - 0.26f * r, by + 1.08f * r)
        bowl.lineTo(bx + 0.26f * r, by + 1.08f * r)
        bowl.lineTo(bx + 0.3f * r, by + 0.93f * r)
        // A painted band around the bowl.
        bowl.moveTo(bx - 0.92f * r, by + 0.3f * r)
        bowl.quadTo(bx, by + 0.66f * r, bx + 0.92f * r, by + 0.3f * r)
        // Nori standing at the back.
        bowl.moveTo(bx - 0.62f * r, by - 0.1f * r)
        bowl.lineTo(bx - 0.68f * r, by - 0.62f * r)
        bowl.lineTo(bx - 0.36f * r, by - 0.66f * r)
        bowl.lineTo(bx - 0.3f * r, by - 0.14f * r)
        // Egg, halved.
        oval.set(bx - 0.56f * r, by - 0.1f * r, bx - 0.2f * r, by + 0.06f * r)
        bowl.addOval(oval, Path.Direction.CW)
        // Chopsticks lifting noodles.
        bowl.moveTo(bx + 1.02f * r, by - 1.3f * r)
        bowl.lineTo(bx + 0.02f * r, by - 0.56f * r)
        bowl.moveTo(bx + 1.1f * r, by - 1.2f * r)
        bowl.lineTo(bx + 0.1f * r, by - 0.5f * r)
        for (k in 0 until 4) {
            val nx = bx + (0.04f + k * 0.045f) * r
            bowl.moveTo(nx, by - 0.53f * r)
            for (step in 1..6) {
                val v = step / 6f
                bowl.lineTo(nx + 0.05f * r * sin(t * 3f + k + v * 6f), by - 0.53f * r + 0.5f * r * v)
            }
        }

        // The narutomaki: a fishcake slice with its pink swirl, spinning.
        val nx = bx + 0.4f * r
        val ny = by - 0.02f * r
        oval.set(nx - 0.22f * r, ny - 0.1f * r, nx + 0.22f * r, ny + 0.1f * r)
        bowl.addOval(oval, Path.Direction.CW)
        swirl.reset()
        val spin = t * 2.6f
        for (i in 0..40) {
            val th = i / 40f * 4f * PI.toFloat()
            val rr = 0.19f * r * th / (4f * PI.toFloat())
            val px = nx + rr * kotlin.math.cos(th + spin)
            val py = ny + rr * sin(th + spin) * 0.45f
            if (i == 0) swirl.moveTo(px, py) else swirl.lineTo(px, py)
        }

        // Steam curling up.
        steam.reset()
        for (k in 0 until 3) {
            val sx = bx + (-0.3f + k * 0.28f) * r
            steam.moveTo(sx, by - 0.22f * r)
            for (step in 1..8) {
                val v = step / 8f
                steam.lineTo(sx + 0.1f * r * sin(t * 2f + k * 2f + v * 5f), by - 0.22f * r - 0.95f * r * v)
            }
        }

        wire.style = Paint.Style.STROKE
        // Glow, chromatic split, core — the same treatment as the koi.
        strokePath(canvas, bowl, frame.s(6f), Neon.alpha(Neon.CYAN, 0.12f * f), 0f)
        strokePath(canvas, bowl, frame.s(1.8f), Neon.alpha(Neon.MAGENTA, 0.6f * f), frame.s(3f))
        strokePath(canvas, bowl, frame.s(1.8f), Neon.alpha(Neon.CYAN, 0.9f * f), -frame.s(1.5f))
        strokePath(canvas, bowl, frame.s(0.9f), Neon.alpha(0xFFE6FDFF.toInt(), 0.7f * f), 0f)
        strokePath(canvas, swirl, frame.s(5f), Neon.alpha(0xFFFF6BB5.toInt(), 0.3f * f), 0f)
        strokePath(canvas, swirl, frame.s(2f), Neon.alpha(0xFFFF8CC6.toInt(), f), 0f)
        strokePath(canvas, steam, frame.s(2f), Neon.alpha(0xFFE6FDFF.toInt(), 0.35f * f), 0f)
        // Yolk.
        broth.color = Neon.alpha(Neon.YELLOW, 0.75f * f)
        canvas.drawCircle(bx - 0.38f * r, by - 0.02f * r, 0.055f * r, broth)
    }

    private fun strokePath(canvas: Canvas, p: Path, width: Float, color: Int, ox: Float) {
        wire.strokeWidth = width
        wire.color = color
        canvas.save()
        canvas.translate(ox, 0f)
        canvas.drawPath(p, wire)
        canvas.restore()
    }

    /** Refresh the tap target for the current parallax offset [dx] (same placement as [drawKoi]). */
    fun updateHitBox(dx: Float) {
        val x = kx + lerp(dx, dx * 0.2f, gold)
        koiRect.set(x - length * 0.55f, ky - length * 0.3f, x + length * 0.55f, ky + length * 0.3f)
    }

    private fun flicker(t: Float) = if (t < glitchUntil) 0.55f else 0.86f + 0.14f * noise1(t * 9f, 7)

    /** Projection cone, caption, the beam feeding the koi and the projector itself. */
    fun drawProjection(canvas: Canvas, t: Float, dx: Float, power: Float) {
        val f = flicker(t) * power
        canvas.save()
        canvas.translate(dx, 0f)
        conePaint.alpha = (255 * f).toInt()
        canvas.drawPath(conePath, conePaint)
        coneEdge.alpha = (90 * f).toInt()
        canvas.drawLine(coneLeft, coneTopY, coneRight, coneTopY, coneEdge)
        captionPaint.alpha = (140 * f).toInt()
        val caption = when {
            bowlVisible -> "一楽 · RAMEN 24H"
            gold > 0.5f -> "KOI-61 · SEÑAL PERDIDA"
            else -> "KOI-61 · NIGHT 1000"
        }
        canvas.drawText(caption, frame.x(376f), coneTopY + frame.s(40f), captionPaint)

        // The beam feeds whatever is projected: the koi, or the bowl while ramen is served.
        val beamAlpha = f * if (bowlVisible) 1f else 1f - smoothstep(0f, 0.6f, gold)
        if (beamAlpha > 0.01f) {
            val tx = if (bowlVisible) bowlX else kx
            val ty = if (bowlVisible) bowlY(t) + frame.s(70f) else ky + frame.s(30f)
            beamPath.reset()
            beamPath.moveTo(projX - frame.s(5f), projY)
            beamPath.lineTo(tx - length * 0.42f, ty)
            beamPath.lineTo(tx + length * 0.42f, ty)
            beamPath.lineTo(projX + frame.s(5f), projY)
            beamPath.close()
            beamPaint.alpha = (255 * beamAlpha).toInt()
            canvas.drawPath(beamPath, beamPaint)
        }

        canvas.drawRect(projX - frame.s(14f), projY - frame.s(3f), projX + frame.s(14f), projY + frame.s(10f), projectorPaint)
        wire.strokeWidth = frame.s(3f)
        wire.color = Neon.alpha(Neon.CYAN, 0.9f * f)
        canvas.drawLine(projX - frame.s(8f), projY - frame.s(2f), projX + frame.s(8f), projY - frame.s(2f), wire)
        canvas.restore()
    }

    /**
     * The koi itself. A blackout kills the projection ([power]), but a golden koi has slipped
     * the projector and keeps shining.
     */
    fun drawKoi(canvas: Canvas, t: Float, dx: Float, power: Float) {
        // Loose in the sky it follows the far parallax, not Tower 61's.
        val x = kx + lerp(dx, dx * 0.2f, gold) + if (t < glitchUntil) glitchDx else 0f
        val f = flicker(t) * maxOf(power, smoothstep(0.3f, 1f, gold))
        glitter.draw(canvas, lerp(dx, dx * 0.2f, gold))
        if (f <= 0.01f) return
        // While ramen is on, the bowl takes the koi's place in the beam (a loose koi stays out).
        if (bowlVisible && gold < 0.5f) return

        val f2 = flicker(t + 0.37f) * power
        if (companion && !bowlVisible && f2 > 0.01f) {
            val face2 = -k2vx / (abs(k2vx) + frame.s(25f))
            val sq2 = (0.16f + 0.84f * abs(face2).pow(0.3f)) * if (face2 >= 0f) 1f else -1f
            buildWireframe(k2x + dx, k2y, sq2, length * 0.78f)
            drawLines(canvas, frame.s(5f), Neon.alpha(Neon.MAGENTA, 0.10f * f2), 0f, 0f)
            drawLines(canvas, frame.s(1.6f), Neon.alpha(Neon.CYAN, 0.55f * f2), frame.s(3f), frame.s(1.2f))
            drawLines(canvas, frame.s(1.6f), Neon.alpha(Neon.MAGENTA, 0.9f * f2), -frame.s(1.5f), 0f)
            drawLines(canvas, frame.s(0.8f), Neon.alpha(0xFFFFE6F3.toInt(), 0.7f * f2), -frame.s(1f), 0f)
        }

        if (gold > 0f) sprites.drawBlob(canvas, x, ky, length * 0.9f, length * 0.55f, GOLD, 0.22f * gold * f)

        // Facing follows the swim direction; near the turn the koi is seen head-on.
        val facing = -kvx / (abs(kvx) + frame.s(25f))
        val squash = (0.16f + 0.84f * abs(facing).pow(0.3f)) * if (facing >= 0f) 1f else -1f
        buildWireframe(x, ky, squash, length)

        val main = Neon.mix(koiMain, GOLD, gold)
        val chroma = Neon.mix(koiChroma, 0xFFFF7A2E.toInt(), gold)
        val core = Neon.mix(koiCore, 0xFFFFF6DC.toInt(), gold)
        drawLines(canvas, frame.s(6f), Neon.alpha(main, (0.10f + 0.12f * gold) * f), 0f, 0f)
        drawLines(canvas, frame.s(1.8f), Neon.alpha(chroma, 0.65f * f), frame.s(3.5f), frame.s(1.5f))
        drawLines(canvas, frame.s(1.8f), Neon.alpha(main, 0.9f * f), -frame.s(2f), 0f)
        drawLines(canvas, frame.s(0.9f), Neon.alpha(core, 0.75f * f), -frame.s(1f), 0f)
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

    private fun buildWireframe(cx: Float, cy: Float, sx: Float, len: Float) {
        lineCount = 0
        val half = len / 2f
        val bodyEnd = 0.8f
        for (i in 0 until stations) {
            val u = i / (stations - 1f) * bodyEnd
            val sway = 0.11f * u.pow(1.5f) * sin(2f * PI.toFloat() * (u * 0.85f - swimPhase * 0.5f))
            val x = cx + (u * len - half) * sx
            val yc = cy + sway * len
            val h = profile(u) * len
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
        val sway = 0.06f * sin(2f * PI.toFloat() * (0.9f - swimPhase * 0.5f)) * len
        val tipX = tx + 0.24f * len * sx
        val upperX = tipX + 0.03f * len * sx
        val upperY = ty - 0.16f * len + sway
        val lowerY = ty + 0.14f * len + sway
        val notchX = tx + 0.13f * len * sx
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
        line(top[d0 * 2], top[d0 * 2 + 1], top[d1 * 2] - 0.02f * len * sx, top[d1 * 2 + 1] - 0.12f * len)
        line(top[d1 * 2] - 0.02f * len * sx, top[d1 * 2 + 1] - 0.12f * len, top[d1 * 2], top[d1 * 2 + 1])
        // Pectoral fin.
        val p = 3
        val fin = 0.5f + 0.5f * sin(swimPhase * 3f)
        line(bottom[p * 2], bottom[p * 2 + 1], bottom[p * 2] + 0.1f * len * sx, bottom[p * 2 + 1] + (0.07f + 0.04f * fin) * len)
        line(bottom[p * 2] + 0.1f * len * sx, bottom[p * 2 + 1] + (0.07f + 0.04f * fin) * len, bottom[(p + 1) * 2], bottom[(p + 1) * 2 + 1])
        // Barbels and eye.
        val nx = mid[0]
        val ny = mid[1]
        line(nx, ny, nx - 0.07f * len * sx, ny + 0.06f * len)
        line(nx + 0.02f * len * sx, ny + 0.01f * len, nx - 0.05f * len * sx, ny + 0.1f * len)
        val ex = nx + 0.09f * len * sx
        val ey = ny - 0.045f * len
        val er = 0.018f * len
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

    private companion object {
        const val GOLD = 0xFFFFC94A.toInt()
    }
}
