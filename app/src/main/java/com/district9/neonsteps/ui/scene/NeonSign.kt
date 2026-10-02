package com.district9.neonsteps.ui.scene

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.district9.neonsteps.ui.Neon
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Hand-traced, katakana-like glyphs as tube polylines in a unit box. */
internal object KanaGlyphs {
    private fun circle(cx: Float, cy: Float, r: Float): FloatArray =
        FloatArray(26) { i ->
            val a = (i / 2) / 12f * 2f * Math.PI.toFloat()
            if (i % 2 == 0) cx + r * cos(a) else cy + r * sin(a)
        }

    private val glyphs: Map<Char, Array<FloatArray>> = mapOf(
        'コ' to arrayOf(floatArrayOf(0.18f, 0.2f, 0.82f, 0.2f, 0.82f, 0.8f, 0.18f, 0.8f)),
        'リ' to arrayOf(floatArrayOf(0.3f, 0.14f, 0.3f, 0.58f), floatArrayOf(0.72f, 0.1f, 0.72f, 0.5f, 0.64f, 0.72f, 0.42f, 0.9f)),
        'ホ' to arrayOf(
            floatArrayOf(0.12f, 0.32f, 0.88f, 0.32f), floatArrayOf(0.5f, 0.08f, 0.5f, 0.9f, 0.4f, 0.84f),
            floatArrayOf(0.3f, 0.52f, 0.14f, 0.78f), floatArrayOf(0.7f, 0.52f, 0.86f, 0.78f),
        ),
        'モ' to arrayOf(
            floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f), floatArrayOf(0.1f, 0.47f, 0.9f, 0.47f),
            floatArrayOf(0.46f, 0.2f, 0.46f, 0.76f, 0.56f, 0.86f, 0.88f, 0.86f),
        ),
        'ン' to arrayOf(floatArrayOf(0.18f, 0.22f, 0.36f, 0.38f), floatArrayOf(0.14f, 0.86f, 0.5f, 0.72f, 0.88f, 0.24f)),
        'エ' to arrayOf(floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f), floatArrayOf(0.5f, 0.2f, 0.5f, 0.82f), floatArrayOf(0.1f, 0.82f, 0.9f, 0.82f)),
        'ス' to arrayOf(floatArrayOf(0.2f, 0.18f, 0.78f, 0.18f, 0.56f, 0.52f, 0.14f, 0.88f), floatArrayOf(0.5f, 0.6f, 0.88f, 0.88f)),
        'テ' to arrayOf(
            floatArrayOf(0.24f, 0.14f, 0.76f, 0.14f), floatArrayOf(0.1f, 0.4f, 0.9f, 0.4f),
            floatArrayOf(0.52f, 0.4f, 0.52f, 0.62f, 0.34f, 0.9f),
        ),
        'ッ' to arrayOf(
            floatArrayOf(0.26f, 0.46f, 0.32f, 0.62f), floatArrayOf(0.46f, 0.42f, 0.52f, 0.58f),
            floatArrayOf(0.76f, 0.4f, 0.66f, 0.72f, 0.42f, 0.9f),
        ),
        'プ' to arrayOf(floatArrayOf(0.14f, 0.24f, 0.76f, 0.24f, 0.68f, 0.56f, 0.48f, 0.78f, 0.22f, 0.92f), circle(0.88f, 0.12f, 0.08f)),
        'チ' to arrayOf(
            floatArrayOf(0.7f, 0.1f, 0.26f, 0.22f), floatArrayOf(0.1f, 0.46f, 0.9f, 0.46f),
            floatArrayOf(0.5f, 0.2f, 0.5f, 0.66f, 0.34f, 0.9f),
        ),
        'カ' to arrayOf(floatArrayOf(0.14f, 0.34f, 0.84f, 0.34f, 0.8f, 0.76f, 0.66f, 0.9f), floatArrayOf(0.46f, 0.08f, 0.42f, 0.52f, 0.18f, 0.9f)),
        'ム' to arrayOf(floatArrayOf(0.46f, 0.1f, 0.16f, 0.8f, 0.86f, 0.72f), floatArrayOf(0.68f, 0.54f, 0.86f, 0.9f)),
        'サ' to arrayOf(
            floatArrayOf(0.1f, 0.36f, 0.9f, 0.36f), floatArrayOf(0.32f, 0.12f, 0.32f, 0.62f),
            floatArrayOf(0.7f, 0.1f, 0.7f, 0.56f, 0.56f, 0.8f, 0.34f, 0.92f),
        ),
        'ロ' to arrayOf(floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.82f, 0.2f, 0.82f, 0.2f, 0.2f)),
        'ラ' to arrayOf(floatArrayOf(0.24f, 0.14f, 0.76f, 0.14f), floatArrayOf(0.14f, 0.38f, 0.84f, 0.38f, 0.74f, 0.66f, 0.42f, 0.9f)),
        'メ' to arrayOf(floatArrayOf(0.78f, 0.12f, 0.6f, 0.5f, 0.2f, 0.9f), floatArrayOf(0.3f, 0.36f, 0.84f, 0.8f)),
        'ー' to arrayOf(floatArrayOf(0.12f, 0.5f, 0.88f, 0.5f)),
    )

    fun path(ch: Char, left: Float, top: Float, size: Float): Path {
        val p = Path()
        for (stroke in glyphs[ch] ?: emptyArray()) {
            p.moveTo(left + stroke[0] * size, top + stroke[1] * size)
            var i = 2
            while (i < stroke.size) {
                p.lineTo(left + stroke[i] * size, top + stroke[i + 1] * size)
                i += 2
            }
        }
        return p
    }
}

/** How a sign misbehaves. Real neon stutters, drops out, browns out and dies. */
internal class SignTemper(
    val baseLevel: Float = 1f,
    val eventGap: ClosedFloatingPointRange<Float> = 4f..12f,
    val glyphDropGap: ClosedFloatingPointRange<Float> = 6f..20f,
    val deadGlyphs: Set<Int> = emptySet(),
    val darkGlyphs: Set<Int> = emptySet(),
)

/**
 * A neon sign pre-rendered into bitmaps: the unlit glass once, and each tube element
 * (frame, glyph, letter) lit with its glow, so per frame we only blend bitmaps by intensity.
 */
internal class NeonSign(
    val color: Int,
    val box: RectF,
    private val elements: List<Path>,
    private val hasFrame: Boolean,
    private val temper: SignTemper,
    private val frame: SceneFrame,
    seed: Int,
) {
    private val pad = frame.s(34f)
    private val rng = Rng(seed.toLong())
    private val seed = seed

    private val base: Bitmap
    private val baseLeft = box.left - pad
    private val baseTop = box.top - pad
    private val lit = ArrayList<Bitmap>(elements.size)
    private val litLeft = FloatArray(elements.size)
    private val litTop = FloatArray(elements.size)

    /** Blurred, low-res copy of the fully lit sign for the wet-street reflection. */
    val reflection: Bitmap
    val reflectionRect = RectF()

    private val levels = FloatArray(elements.size)
    var averageLevel = 0f
        private set

    // Sign-wide event state.
    private var eventKind = 0 // 0 none, 1 stutter, 2 brownout, 3 dropout
    private var eventEnd = 0f
    private var eventStart = 0f
    private var nextEvent = rng.range(temper.eventGap.start, temper.eventGap.endInclusive)
    private val glyphDropEnd = FloatArray(elements.size)
    private val glyphNextDrop = FloatArray(elements.size) { rng.range(2f, temper.glyphDropGap.endInclusive) }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        val w = (box.width() + 2 * pad).toInt().coerceAtLeast(1)
        val h = (box.height() + 2 * pad).toInt().coerceAtLeast(1)
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(base).apply {
            translate(-baseLeft, -baseTop)
            if (hasFrame) {
                val backing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE0090314.toInt() }
                drawRoundRect(box, frame.s(6f), frame.s(6f), backing)
            }
            val glass = tubePaint(frame.s(3.8f), Neon.mix(color, 0xFF160C26.toInt(), 0.74f))
            val glassEdge = tubePaint(frame.s(1.2f), Neon.mix(color, 0xFF000000.toInt(), 0.55f))
            for (p in elements) {
                drawPath(p, glass)
                drawPath(p, glassEdge)
            }
        }

        val bounds = RectF()
        for ((i, p) in elements.withIndex()) {
            p.computeBounds(bounds, true)
            val l = bounds.left - pad
            val t = bounds.top - pad
            val bmp = Bitmap.createBitmap(
                (bounds.width() + 2 * pad).toInt().coerceAtLeast(1),
                (bounds.height() + 2 * pad).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            Canvas(bmp).apply {
                translate(-l, -t)
                drawLitTube(this, p)
            }
            lit += bmp
            litLeft[i] = l
            litTop[i] = t
        }

        val q = 0.5f
        reflectionRect.set(baseLeft, baseTop, baseLeft + w, baseTop + h)
        reflection = Bitmap.createBitmap((w * q).toInt().coerceAtLeast(1), (h * q).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(reflection).apply {
            scale(q, q)
            translate(-baseLeft, -baseTop)
            val smear = tubePaint(frame.s(9f), color).apply {
                maskFilter = BlurMaskFilter(frame.s(12f), BlurMaskFilter.Blur.NORMAL)
            }
            val core = tubePaint(frame.s(4f), Neon.mix(color, 0xFFFFFFFF.toInt(), 0.4f)).apply {
                maskFilter = BlurMaskFilter(frame.s(5f), BlurMaskFilter.Blur.NORMAL)
            }
            for (p in elements) {
                drawPath(p, smear)
                drawPath(p, core)
            }
        }
    }

    private fun tubePaint(width: Float, c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = c
    }

    private fun drawLitTube(c: Canvas, p: Path) {
        val halo = tubePaint(frame.s(10f), Neon.alpha(color, 0.42f)).apply {
            maskFilter = BlurMaskFilter(frame.s(18f), BlurMaskFilter.Blur.NORMAL)
        }
        val glow = tubePaint(frame.s(6f), Neon.alpha(color, 0.95f)).apply {
            maskFilter = BlurMaskFilter(frame.s(5f), BlurMaskFilter.Blur.NORMAL)
        }
        c.drawPath(p, halo)
        c.drawPath(p, glow)
        c.drawPath(p, tubePaint(frame.s(3.6f), Neon.mix(color, 0xFFFFFFFF.toInt(), 0.3f)))
        c.drawPath(p, tubePaint(frame.s(1.5f), Neon.mix(color, 0xFFFFFFFF.toInt(), 0.8f)))
    }

    fun update(t: Float) {
        if (eventKind != 0 && t >= eventEnd) eventKind = 0
        if (eventKind == 0 && t >= nextEvent) {
            val roll = rng.next()
            eventKind = when {
                roll < 0.5f -> 1
                roll < 0.8f -> 2
                else -> 3
            }
            eventStart = t
            eventEnd = t + when (eventKind) {
                1 -> rng.range(0.25f, 0.8f)
                2 -> rng.range(1.2f, 3.2f)
                else -> rng.range(0.12f, 0.5f)
            }
            nextEvent = eventEnd + rng.range(temper.eventGap.start, temper.eventGap.endInclusive)
        }
        val signLevel = when (eventKind) {
            1 -> if (hash(floor(t * 26f).toInt(), seed) > 0.45f) 1f else 0.06f
            2 -> {
                val fade = smoothstep(0f, 0.25f, t - eventStart) * smoothstep(0f, 0.35f, eventEnd - t)
                lerp(1f, 0.32f + 0.12f * noise1(t * 18f, seed), fade)
            }
            3 -> 0.04f
            else -> 1f
        }
        val hum = 0.93f + 0.07f * noise1(t * 11f, seed)

        var sum = 0f
        for (i in elements.indices) {
            val glyph = when {
                i in temper.darkGlyphs -> 0f
                i in temper.deadGlyphs -> deadGlyphAttempt(t, i)
                else -> {
                    if (t >= glyphNextDrop[i]) {
                        glyphDropEnd[i] = t + rng.range(0.08f, 0.9f)
                        glyphNextDrop[i] = glyphDropEnd[i] + rng.range(temper.glyphDropGap.start, temper.glyphDropGap.endInclusive)
                    }
                    if (t < glyphDropEnd[i]) {
                        if (hash(floor(t * 19f).toInt(), seed + i) > 0.7f) 0.7f else 0.05f
                    } else {
                        1f
                    }
                }
            }
            levels[i] = (temper.baseLevel * signLevel * glyph * hum).coerceIn(0f, 1f)
            sum += levels[i]
        }
        averageLevel = if (elements.isEmpty()) 0f else sum / elements.size
    }

    /** A dead tube mostly stays dark, but every so often it tries, and fails, to strike. */
    private fun deadGlyphAttempt(t: Float, i: Int): Float {
        val cycle = 13f + 5f * hash(i, seed)
        val phase = t % cycle
        return if (phase < 0.35f && hash(floor(t * 30f).toInt(), seed * 3 + i) > 0.55f) 0.35f else 0f
    }

    fun drawWash(canvas: Canvas, sprites: Sprites, dx: Float) {
        sprites.drawBlob(
            canvas, box.centerX() + dx, box.centerY(),
            box.width() * 0.9f + frame.s(70f), box.height() * 0.62f + frame.s(70f),
            color, 0.24f * averageLevel,
        )
    }

    fun draw(canvas: Canvas, dx: Float) {
        canvas.drawBitmap(base, baseLeft + dx, baseTop, null)
        for (i in lit.indices) {
            val a = levels[i]
            if (a < 0.01f) continue
            paint.alpha = (a * 255).toInt()
            canvas.drawBitmap(lit[i], litLeft[i] + dx, litTop[i], paint)
        }
    }

    companion object {
        /** Vertical katakana sign: glyphs stacked in a framed box. */
        fun vertical(
            frame: SceneFrame, text: String, color: Int,
            refLeft: Float, refTop: Float, refWidth: Float,
            temper: SignTemper, seed: Int, framed: Boolean = true,
        ): NeonSign {
            val left = frame.x(refLeft)
            val top = frame.y(refTop)
            val w = frame.s(refWidth)
            val glyph = w * 0.72f
            val step = glyph * 1.12f
            val inset = (w - glyph) / 2f
            val box = RectF(left, top, left + w, top + inset * 2 + step * text.length - (step - glyph))
            val paths = ArrayList<Path>()
            if (framed) {
                paths += Path().apply {
                    addRoundRect(
                        RectF(box.left + frame.s(4f), box.top + frame.s(4f), box.right - frame.s(4f), box.bottom - frame.s(4f)),
                        frame.s(6f), frame.s(6f), Path.Direction.CW,
                    )
                }
            }
            text.forEachIndexed { i, ch -> paths += KanaGlyphs.path(ch, left + inset, top + inset + i * step, glyph) }
            val shifted = if (framed) {
                SignTemper(
                    temper.baseLevel, temper.eventGap, temper.glyphDropGap,
                    temper.deadGlyphs.map { it + 1 }.toSet(), temper.darkGlyphs.map { it + 1 }.toSet(),
                )
            } else {
                temper
            }
            return NeonSign(color, box, paths, framed, shifted, frame, seed)
        }

        /** Horizontal word sign whose letters are outlined tubes traced from a bold typeface. */
        fun word(
            frame: SceneFrame, text: String, color: Int, typeface: Typeface,
            refLeft: Float, refTop: Float, refRight: Float, refBottom: Float,
            temper: SignTemper, seed: Int,
        ): NeonSign {
            val box = RectF(frame.x(refLeft), frame.y(refTop), frame.x(refRight), frame.y(refBottom))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = box.height() * 0.62f
                letterSpacing = 0.06f
            }
            val total = p.measureText(text)
            var x = box.centerX() - total / 2f
            val baseline = box.centerY() + p.textSize * 0.36f
            val paths = ArrayList<Path>()
            paths += Path().apply {
                addRoundRect(
                    RectF(box.left + frame.s(4f), box.top + frame.s(4f), box.right - frame.s(4f), box.bottom - frame.s(4f)),
                    frame.s(5f), frame.s(5f), Path.Direction.CW,
                )
            }
            val widths = FloatArray(text.length)
            p.getTextWidths(text, widths)
            text.forEachIndexed { i, ch ->
                val glyph = Path()
                p.getTextPath(ch.toString(), 0, 1, x, baseline, glyph)
                paths += glyph
                x += widths[i]
            }
            val shifted = SignTemper(
                temper.baseLevel, temper.eventGap, temper.glyphDropGap,
                temper.deadGlyphs.map { it + 1 }.toSet(), temper.darkGlyphs.map { it + 1 }.toSet(),
            )
            return NeonSign(color, box, paths, true, shifted, frame, seed)
        }
    }
}
