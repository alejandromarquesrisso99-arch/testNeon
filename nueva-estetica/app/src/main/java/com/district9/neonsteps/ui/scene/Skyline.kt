package com.district9.neonsteps.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.district9.neonsteps.ui.Neon
import kotlin.math.sin

/** A rooftop light that pulses, so the skyline never sits perfectly still. */
internal class Beacon(val x: Float, val y: Float, val color: Int, val period: Float, val phase: Float, val layer: Int)

/**
 * One parallax band of the skyline: building silhouettes and unlit windows are baked into a
 * bitmap; lit windows are drawn live so they can switch on and off.
 */
internal class SkyLayer(
    val parallax: Float,
    val bitmap: Bitmap,
    val bitmapLeft: Float,
    val bitmapTop: Float,
    private val windows: FloatArray, // l, t, r, b per window
    private val windowColor: IntArray,
    private val lit: BooleanArray,
    private val nextToggle: FloatArray,
    private val litAlpha: Float,
    private val rng: Rng,
) {
    private val paint = Paint()
    private val count = lit.size

    fun update(t: Float) {
        for (i in 0 until count) {
            if (t >= nextToggle[i]) {
                lit[i] = !lit[i]
                nextToggle[i] = t + if (lit[i]) rng.range(8f, 70f) else rng.range(4f, 45f)
            }
        }
    }

    /** @param blackoutT seconds into a blackout, or negative: windows then return one by one. */
    fun draw(canvas: Canvas, dx: Float, blackoutT: Float = -1f) {
        canvas.drawBitmap(bitmap, bitmapLeft + dx, bitmapTop, null)
        var lastColor = 0
        val seed = (parallax * 1000).toInt()
        for (i in 0 until count) {
            if (!lit[i]) continue
            if (blackoutT >= 0f && Blackout.power(blackoutT, hash(i, seed)) < 0.5f) continue
            val c = windowColor[i]
            if (c != lastColor) {
                paint.color = Neon.alpha(c, litAlpha)
                lastColor = c
            }
            val o = i * 4
            canvas.drawRect(windows[o] + dx, windows[o + 1], windows[o + 2] + dx, windows[o + 3], paint)
        }
    }

    /** Lit windows only, for baking the street reflection. */
    fun drawLitWindows(canvas: Canvas) {
        for (i in 0 until count) {
            if (!lit[i]) continue
            paint.color = Neon.alpha(windowColor[i], litAlpha)
            val o = i * 4
            canvas.drawRect(windows[o], windows[o + 1], windows[o + 2], windows[o + 3], paint)
        }
    }
}

/**
 * @param maxShift the largest camera offset (px at parallax 1); each layer is generated wide
 *   enough that panning never reveals its edge.
 */
internal class Skyline(private val frame: SceneFrame, private val maxShift: Float, monoBold: Typeface) {
    val layers: List<SkyLayer>
    val beacons = mutableListOf<Beacon>()

    /** Tower 61 roof, where the hologram projector sits (screen coords, before parallax). */
    val projectorX = frame.x(560f)
    val projectorY = frame.y(652f)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = monoBold
        textSize = frame.s(25f)
        letterSpacing = 0.04f
        color = Neon.TEAL
    }
    private val labelGlow = Paint(labelPaint).apply { alpha = 70; strokeWidth = frame.s(4f); style = Paint.Style.STROKE }
    private val rng = Rng(61)

    // Tower 61's facade, recorded while baking so its bands can light up for the goal.
    private var towerL = 0f
    private var towerR = 0f
    private var towerT = 0f
    private var bandTop = 0f
    private var bandCount = 0
    private val bandStep = frame.s(13f)
    private val bandH = frame.s(3f)
    private val towerFill = Paint()
    private val towerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private class Spec(
        val color: Int,
        val haze: Float,
        val minW: Float, val maxW: Float,
        val minTop: Float, val maxTop: Float,
        val win: Float, val winH: Float, val gapX: Float, val gapY: Float,
        val litChance: Float, val litAlpha: Float,
        val parallax: Float,
    )

    private class Block(
        val l: Float, val t: Float, val r: Float,
        val crown: Int = 0, val antenna: Boolean = false, val isTower61: Boolean = false,
    )

    init {
        val specs = listOf(
            Spec(0xFF1D0638.toInt(), 0.42f, 40f, 95f, 380f, 820f, 5f, 7f, 13f, 17f, 0.20f, 0.55f, PARALLAX[0]),
            Spec(0xFF15042F.toInt(), 0.30f, 60f, 135f, 450f, 900f, 7f, 9f, 18f, 22f, 0.25f, 0.70f, PARALLAX[1]),
            Spec(0xFF0D0222.toInt(), 0.18f, 90f, 190f, 540f, 980f, 8f, 11f, 23f, 29f, 0.28f, 0.85f, PARALLAX[2]),
            Spec(Neon.INK, 0.06f, 120f, 230f, 560f, 1000f, 10f, 13f, 30f, 38f, 0.30f, 1f, PARALLAX[3]),
        )
        layers = specs.mapIndexed { index, spec ->
            val margin = marginFor(spec.parallax)
            val blocks = when (index) {
                2 -> proceduralBlocks(spec, margin, avoid = 470f to 650f) + Block(495f, 690f, 625f, crown = 2, isTower61 = true)
                // Hand-placed street front, extended with generated towers for when you pan.
                3 -> proceduralBlocks(spec, margin, avoid = -60f to 1160f) + nearBlocks()
                else -> proceduralBlocks(spec, margin, avoid = null)
            }
            buildLayer(spec, blocks, index, margin)
        }
    }

    /** Hand-placed street-front towers, matching the District 9 composition. */
    private fun nearBlocks(): List<Block> = listOf(
        Block(-60f, 840f, 62f),
        Block(66f, 560f, 362f, crown = 1, antenna = true),
        Block(330f, 1030f, 600f),
        Block(596f, 790f, 802f),
        Block(780f, 470f, 845f),
        Block(840f, 292f, 1042f, crown = 3, antenna = true),
        Block(1040f, 640f, 1160f),
    )

    private fun marginFor(parallax: Float) = maxShift * parallax + frame.s(40f)

    /**
     * Fills the view width plus [margin] with towers, in reference units, leaving [avoid] to
     * the hand-placed buildings. Left of it towers are laid outward from its edge, so every
     * tower is full width (no sliver squeezed in at the seam); right of it, outward too.
     */
    private fun proceduralBlocks(spec: Spec, margin: Float, avoid: Pair<Float, Float>?): List<Block> {
        val out = mutableListOf<Block>()
        val startRef = (-margin - frame.x(0f)) / frame.k - 40f
        val endRef = (frame.width + margin - frame.x(0f)) / frame.k + 40f
        if (avoid != null) {
            var right = avoid.first + 10f
            while (right > startRef) {
                val w = rng.range(spec.minW, spec.maxW)
                out += procedural(spec, right - w, right)
                right -= w * rng.range(0.72f, 1.02f)
            }
        }
        var x = avoid?.let { it.second - 10f } ?: startRef
        while (x < endRef) {
            val w = rng.range(spec.minW, spec.maxW)
            out += procedural(spec, x, x + w)
            x += w * rng.range(0.72f, 1.02f)
        }
        return out
    }

    /** A generated tower: windows always; the banded, windowless facade is Tower 61's alone. */
    private fun procedural(spec: Spec, l: Float, r: Float): Block {
        val top = rng.range(spec.minTop, spec.maxTop)
        val crown = if (rng.chance(0.3f)) (if (rng.chance(0.5f)) 1 else 3) else 0
        return Block(l, top, r, crown = crown, antenna = rng.chance(0.18f))
    }

    private fun buildLayer(spec: Spec, blocks: List<Block>, index: Int, margin: Float): SkyLayer {
        val minTopRef = (blocks.minOfOrNull { it.t } ?: 900f) - 140f
        val top = frame.y(minTopRef).coerceAtLeast(0f)
        val left = -margin
        val bmpW = (frame.width + 2 * margin).toInt().coerceAtLeast(1)
        val bmpH = (frame.horizon - top).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.translate(-left, -top)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.color }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Neon.mix(spec.color, 0xFF7B3FA8.toInt(), 0.35f)
            strokeWidth = frame.s(1.4f)
        }
        val darkWin = Paint().apply { color = Neon.mix(spec.color, 0xFF3A2A5A.toInt(), if (index == 3) 0.28f else 0.22f) }
        val mast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Neon.mix(spec.color, 0xFF4A3A6A.toInt(), 0.4f)
            strokeWidth = frame.s(2.2f)
        }

        val winRects = ArrayList<Float>()
        val winColors = ArrayList<Int>()
        val palette = intArrayOf(
            0xFFFFD36B.toInt(), 0xFFFFD36B.toInt(), 0xFFFFE9C7.toInt(), 0xFFFF6BB5.toInt(),
            0xFF6BE8FF.toInt(), 0xFFA88BFF.toInt(), 0xFFFFD36B.toInt(), 0xFF6BE8FF.toInt(),
        )
        val path = Path()
        for (b in blocks) {
            val l = frame.x(b.l)
            val r = frame.x(b.r)
            val t = frame.y(b.t)
            val w = r - l
            c.drawRect(l, t, r, frame.horizon, body)
            when (b.crown) {
                1 -> { // stepped crown with a dome
                    val cl = l + w * 0.2f
                    val cr = r - w * 0.2f
                    c.drawRect(cl, t - frame.s(28f), cr, t + 1, body)
                    path.reset()
                    path.addArc(RectF(cl + w * 0.08f, t - frame.s(70f), cr - w * 0.08f, t + frame.s(14f)), 180f, 180f)
                    c.drawPath(path, body)
                    if (b.antenna) {
                        val ax = (l + r) / 2f
                        c.drawLine(ax, t - frame.s(150f), ax, t - frame.s(50f), mast)
                        beacons += Beacon(ax, t - frame.s(150f), Neon.MAGENTA, 1.7f, rng.next(), index)
                    }
                }
                2 -> { // Tower 61: banded facade and a projector deck
                    c.drawRect(l + w * 0.32f, t - frame.s(26f), r - w * 0.32f, t + 1, body)
                    val band = Paint().apply { color = Neon.mix(spec.color, 0xFF000000.toInt(), 0.45f) }
                    var bands = 0
                    var y = t + frame.s(52f)
                    while (y < frame.horizon) {
                        c.drawRect(l + frame.s(6f), y, r - frame.s(6f), y + bandH, band)
                        y += bandStep
                        bands++
                    }
                    // Other banded towers share the look; only the real Tower 61 lights up.
                    if (b.isTower61) {
                        towerL = l
                        towerR = r
                        towerT = t
                        bandTop = t + frame.s(52f)
                        bandCount = bands
                    }
                }
                3 -> { // set-back crown
                    c.drawRect(l + w * 0.12f, t - frame.s(36f), r - w * 0.04f, t + 1, body)
                    if (b.antenna) {
                        val ax = l + w * 0.7f
                        c.drawLine(ax, t - frame.s(110f), ax, t - frame.s(36f), mast)
                        c.drawLine(ax - frame.s(14f), t - frame.s(80f), ax + frame.s(14f), t - frame.s(80f), mast)
                        beacons += Beacon(ax, t - frame.s(110f), 0xFFFF3B3B.toInt(), 2.3f, rng.next(), index)
                    }
                }
                else -> if (b.antenna && index < 3) {
                    val ax = l + w * rng.range(0.3f, 0.7f)
                    c.drawLine(ax, t - frame.s(rng.range(30f, 70f)), ax, t, mast)
                }
            }
            // Rim light from the smog on the left edge.
            c.drawLine(l + rim.strokeWidth / 2, t, l + rim.strokeWidth / 2, frame.horizon, rim)

            if (b.crown == 2) continue // Tower 61's facade is bands, not windows
            // Windows: irregular grid, some columns blank, leaving a margin.
            val ww = frame.s(spec.win)
            val wh = frame.s(spec.winH)
            val gx = frame.s(spec.gapX)
            val gy = frame.s(spec.gapY)
            val colSkip = rng.range(0.05f, 0.3f)
            var y = t + gy * 0.8f
            while (y + wh < frame.horizon - frame.s(if (index == 3) 120f else 20f)) {
                var x = l + gx * 0.6f
                var col = 0
                while (x + ww < r - gx * 0.4f) {
                    if (hash(col, (b.l * 7).toInt()) > colSkip && rng.chance(0.86f)) {
                        c.drawRect(x, y, x + ww, y + wh, darkWin)
                        winRects += x; winRects += y; winRects += x + ww; winRects += y + wh
                        winColors += palette[rng.int(0, palette.size)]
                    }
                    x += gx
                    col++
                }
                y += gy
            }
        }

        // Smog haze, heavier on the far layers, only where there is a building.
        val haze = Paint().apply {
            shader = LinearGradient(
                0f, top, 0f, frame.horizon,
                Neon.alpha(0xFF3A0B4A.toInt(), spec.haze * 0.4f),
                Neon.alpha(0xFF5A1060.toInt(), spec.haze),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(left, top, left + bmpW, frame.horizon, haze)

        val n = winColors.size
        val lit = BooleanArray(n) { rng.chance(spec.litChance) }
        val next = FloatArray(n) { rng.range(0f, 50f) }
        return SkyLayer(
            spec.parallax, bitmap, left, top,
            winRects.toFloatArray(), winColors.toIntArray(), lit, next, spec.litAlpha, rng,
        )
    }

    fun update(t: Float) {
        for (layer in layers) layer.update(t)
    }

    fun drawLayer(canvas: Canvas, index: Int, dx: Float, blackoutT: Float = -1f) = layers[index].draw(canvas, dx, blackoutT)

    /** "TOWER 61" lettering on the mid-layer tower, a steady, slightly tired teal. */
    /** "TOWER 61" lettering: a tired teal, blazing white-cyan when [lit] (the goal is met). */
    fun drawTowerLabel(canvas: Canvas, t: Float, dx: Float, lit: Float = 0f, power: Float = 1f) {
        val x = frame.x(503f) + dx
        val y = frame.y(724f)
        val hum = lerp(0.75f + 0.25f * noise1(t * 3f, 61), 1f, lit) * (0.12f + 0.88f * power)
        labelGlow.color = Neon.mix(Neon.TEAL, Neon.CYAN, lit)
        labelGlow.alpha = ((60 + 110 * lit) * hum).toInt()
        canvas.drawText("TOWER 61", x, y, labelGlow)
        labelPaint.color = Neon.mix(Neon.TEAL, 0xFFE6FDFF.toInt(), lit)
        labelPaint.alpha = ((215 + 40 * lit) * hum).toInt()
        canvas.drawText("TOWER 61", x, y, labelPaint)
    }

    /**
     * Tower 61 lit for the goal: facade bands power on floor by floor from the street up as
     * [level] rises to 1, in a magenta-to-cyan gradient with a light chase running up them.
     */
    fun drawTowerLights(canvas: Canvas, sprites: Sprites, t: Float, dx: Float, level: Float) {
        if (level <= 0.005f || bandCount == 0) return
        val l = towerL + dx
        val r = towerR + dx
        sprites.drawBlob(canvas, (l + r) / 2f, towerT + frame.s(150f), (r - l) * 1.7f, frame.s(280f), Neon.CYAN, 0.18f * level)

        val lit = level * (bandCount + 4)
        for (j in 0 until bandCount) {
            val fromBottom = bandCount - 1 - j
            val on = (lit - fromBottom).coerceIn(0f, 1f)
            if (on <= 0f) continue
            val chase = 0.6f + 0.4f * sin(t * 4f - fromBottom * 0.45f)
            val c = Neon.mix(Neon.MAGENTA, Neon.CYAN, 1f - j / bandCount.toFloat()) // cyan crown, magenta base
            val y = bandTop + j * bandStep
            towerFill.color = Neon.alpha(c, 0.28f * on * chase)
            canvas.drawRect(l + frame.s(4f), y - frame.s(3f), r - frame.s(4f), y + bandH + frame.s(3f), towerFill)
            towerFill.color = Neon.alpha(Neon.mix(c, 0xFFFFFFFF.toInt(), 0.3f), on * chase)
            canvas.drawRect(l + frame.s(6f), y, r - frame.s(6f), y + bandH, towerFill)
        }

        // Once fully lit, the edges and crown trace themselves in neon.
        val edge = smoothstep(0.85f, 1f, level)
        if (edge <= 0f) return
        val w = r - l
        val crownL = l + w * 0.32f
        val crownR = r - w * 0.32f
        val crownT = towerT - frame.s(26f)
        for (pass in 0..1) {
            towerLine.strokeWidth = frame.s(if (pass == 0) 8f else 2.4f)
            towerLine.color = Neon.alpha(if (pass == 0) Neon.CYAN else 0xFFE6FDFF.toInt(), edge * if (pass == 0) 0.25f else 0.95f)
            canvas.drawLine(l + frame.s(1f), frame.horizon, l + frame.s(1f), towerT, towerLine)
            canvas.drawLine(l + frame.s(1f), towerT, crownL, towerT, towerLine)
            canvas.drawLine(crownL, towerT, crownL, crownT, towerLine)
            canvas.drawLine(crownL, crownT, crownR, crownT, towerLine)
            canvas.drawLine(crownR, crownT, crownR, towerT, towerLine)
            canvas.drawLine(crownR, towerT, r - frame.s(1f), towerT, towerLine)
            canvas.drawLine(r - frame.s(1f), towerT, r - frame.s(1f), frame.horizon, towerLine)
        }
    }

    /** @param layerDx parallax offset of each layer, indexed like [layers]. */
    fun drawBeacons(canvas: Canvas, sprites: Sprites, t: Float, layerDx: FloatArray, power: Float = 1f) {
        if (power <= 0f) return
        // Beacons are generated per layer; cull those panned out of view.
        val w = frame.width + frame.s(30f)
        for (b in beacons) {
            val dx = layerDx[b.layer]
            if (b.x + dx < -frame.s(30f) || b.x + dx > w) continue
            val on = smoothstep(0.55f, 1f, 0.5f + 0.5f * wave(t, b.period, b.phase)) * power
            sprites.drawBlob(canvas, b.x + dx, b.y, frame.s(26f), frame.s(26f), b.color, 0.55f * on)
            sprites.drawBlob(canvas, b.x + dx, b.y, frame.s(6f), frame.s(6f), 0xFFFFFFFF.toInt(), 0.9f * on)
        }
    }

    companion object {
        /** How far each layer moves per pixel of camera pan, far to near. */
        val PARALLAX = floatArrayOf(0.12f, 0.32f, 0.6f, 1f)
    }
}
