package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.district9.neonsteps.R
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts
import com.district9.neonsteps.util.Format
import kotlin.math.floor

/**
 * Kicker line, today's step count as the big chromatic-aberration title, the goal subtitle
 * and a neon progress tube. Becomes a button asking for the sensor permission when needed.
 */
class HeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class State { OK, NEED_PERMISSION, NO_SENSOR }

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    private var steps = 0
    private var goal = 8000
    private var state = State.OK
    private var stepsText = Format.steps(0)

    private val kickerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Neon.CYAN
        strokeWidth = 1.5f * dp
    }
    private val kickerBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(12.5f)
        letterSpacing = 0.36f
        color = Neon.CYAN
    }
    private val kickerPlace = Paint(kickerBrand).apply { color = Neon.TEXT_DIM }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.black
        letterSpacing = -0.01f
    }
    private val subtitleSize = sp(15f)
    private val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.bold
        textSize = subtitleSize
        letterSpacing = 0.3f
        color = Neon.TEXT
    }
    private val subtitleAccent = Paint(subtitle).apply { color = Neon.YELLOW }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * dp
        color = Neon.alpha(Neon.CYAN, 0.16f)
    }
    private val tube = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * dp
    }
    private val tubeGlow = Paint(tube).apply { strokeWidth = 9f * dp }
    private val tubeCore = Paint(tube).apply {
        strokeWidth = 1.2f * dp
        color = 0xE6FFFFFF.toInt()
    }
    private val tick = Paint().apply { color = Neon.alpha(Neon.TEXT_MUTED, 0.5f) }

    private var titleSize = sp(92f)
    private var glitchUntil = 0L
    private var nextGlitch = SystemClock.uptimeMillis() + 2500
    private var tubeShaderWidth = -1f

    private val gapKicker = 14f * dp
    private val gapSubtitle = 16f * dp
    private val gapTube = 16f * dp

    var onPermissionRequest: (() -> Unit)? = null

    init {
        setOnClickListener { if (state == State.NEED_PERMISSION) onPermissionRequest?.invoke() }
        isClickable = false
    }

    fun setData(steps: Int, goal: Int, state: State) {
        val changed = steps != this.steps
        if (changed && this.steps > 0) glitchUntil = SystemClock.uptimeMillis() + 140 // a step jolts the sign
        this.steps = steps
        this.goal = goal
        this.state = state
        stepsText = Format.steps(steps)
        isClickable = state == State.NEED_PERMISSION
        isFocusable = isClickable
        contentDescription = if (state == State.NEED_PERMISSION) {
            context.getString(R.string.cd_header_permission)
        } else {
            context.getString(R.string.cd_header, Format.steps(steps), Format.steps(goal), Format.percent(steps, goal))
        }
        invalidate()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun capHeight(p: Paint) = -p.fontMetrics.ascent * 0.72f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val avail = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        title.textSize = 100f
        val per100 = title.measureText("88.888")
        titleSize = minOf(sp(96f), avail * 0.97f / per100 * 100f)
        title.textSize = titleSize
        val h = paddingTop + capHeight(kickerBrand) + gapKicker + capHeight(title) + gapSubtitle +
            capHeight(subtitle) + gapTube + 6f * dp + paddingBottom
        setMeasuredDimension(width, resolveSize(h.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        var y = paddingTop.toFloat()

        // Kicker: ——— NEON STEPS · DISTRICT 9
        y += capHeight(kickerBrand)
        val lineW = 36f * dp
        canvas.drawLine(left, y - capHeight(kickerBrand) / 2, left + lineW, y - capHeight(kickerBrand) / 2, kickerLine)
        val brand = context.getString(R.string.kicker_brand)
        val bx = left + lineW + 14f * dp
        canvas.drawText(brand, bx, y, kickerBrand)
        canvas.drawText(context.getString(R.string.kicker_place), bx + kickerBrand.measureText(brand) + 6f * dp, y, kickerPlace)

        // Title: the step count with chromatic aberration and the occasional glitch.
        y += gapKicker + capHeight(title)
        if (now >= nextGlitch) {
            glitchUntil = now + 120
            nextGlitch = now + 3500 + (Math.random() * 6000).toLong()
        }
        drawChromaticTitle(canvas, left, y, now < glitchUntil, now)

        // Subtitle, shrunk to fit narrow screens and large font settings.
        y += gapSubtitle + capHeight(subtitle)
        val avail = right - left
        when (state) {
            State.OK -> {
                val a = context.getString(R.string.subtitle_steps)
                val b = if (steps >= goal) {
                    context.getString(R.string.subtitle_goal_done)
                } else {
                    context.getString(R.string.subtitle_goal, Format.steps(goal))
                }
                val gap = 10f * dp
                fitSubtitle(subtitle.measureText(a) + gap + subtitleAccent.measureText(b), avail)
                canvas.drawText(a, left, y, subtitle)
                canvas.drawText(b, left + subtitle.measureText(a) + gap, y, subtitleAccent)
            }
            State.NEED_PERMISSION -> {
                val text = context.getString(R.string.subtitle_need_permission)
                fitSubtitle(subtitleAccent.measureText(text), avail)
                val blink = if ((now / 600) % 2 == 0L) 1f else 0.45f
                subtitleAccent.color = Neon.alpha(Neon.MAGENTA, blink)
                canvas.drawText(text, left, y, subtitleAccent)
                subtitleAccent.color = Neon.YELLOW
            }
            State.NO_SENSOR -> {
                val text = context.getString(R.string.subtitle_no_sensor)
                fitSubtitle(subtitleAccent.measureText(text), avail)
                subtitleAccent.color = Neon.MAGENTA
                canvas.drawText(text, left, y, subtitleAccent)
                subtitleAccent.color = Neon.YELLOW
            }
        }
        subtitle.textSize = subtitleSize
        subtitleAccent.textSize = subtitleSize

        // Progress tube toward today's goal.
        y += gapTube
        drawTube(canvas, left, right, y, now)

        postInvalidateOnAnimation()
    }

    /** Scales the subtitle paints down when [width] (measured at full size) exceeds [avail]. */
    private fun fitSubtitle(width: Float, avail: Float) {
        subtitle.textSize = subtitleSize
        subtitleAccent.textSize = subtitleSize
        if (width > avail) {
            subtitle.textSize = subtitleSize * avail / width
            subtitleAccent.textSize = subtitle.textSize
        }
    }

    private fun drawChromaticTitle(canvas: Canvas, x: Float, baseline: Float, glitch: Boolean, now: Long) {
        val ca = titleSize * 0.028f
        if (!glitch) {
            drawTitleLayers(canvas, x, baseline, ca)
            return
        }
        // Tear the title into horizontal slices shifted sideways.
        val top = baseline - capHeight(title)
        val sliceH = capHeight(title) / 4f
        for (i in 0 until 5) {
            val seed = floor(now / 40f).toInt() * 7 + i
            val shift = ((seed * 1103515245 + 12345) ushr 16 and 0xFF) / 255f - 0.5f
            canvas.save()
            canvas.clipRect(0f, top + i * sliceH - (if (i == 0) titleSize else 0f), width.toFloat(), top + (i + 1) * sliceH + if (i == 4) titleSize else 0f)
            drawTitleLayers(canvas, x + shift * titleSize * 0.12f, baseline, ca * 2.2f)
            canvas.restore()
        }
    }

    private fun drawTitleLayers(canvas: Canvas, x: Float, baseline: Float, ca: Float) {
        title.color = Neon.MAGENTA
        canvas.drawText(stepsText, x - ca, baseline + ca * 0.55f, title)
        title.color = Neon.CYAN
        canvas.drawText(stepsText, x + ca, baseline - ca * 0.25f, title)
        title.color = 0xFFFFF3FA.toInt()
        canvas.drawText(stepsText, x, baseline, title)
    }

    private fun drawTube(canvas: Canvas, left: Float, right: Float, y: Float, now: Long) {
        val inset = 2f * dp
        canvas.drawLine(left + inset, y, right - inset, y, track)
        for (q in 1..3) {
            val tx = left + (right - left) * q / 4f
            canvas.drawRect(tx - 0.5f * dp, y - 5f * dp, tx + 0.5f * dp, y - 3f * dp, tick)
        }
        val progress = if (goal <= 0) 0f else (steps / goal.toFloat()).coerceIn(0f, 1f)
        if (progress <= 0f) return
        if (tubeShaderWidth != right - left) {
            tubeShaderWidth = right - left
            val shader = LinearGradient(left, 0f, right, 0f, Neon.CYAN, Neon.MAGENTA, Shader.TileMode.CLAMP)
            tube.shader = shader
            tubeGlow.shader = shader
        }
        val end = left + inset + (right - left - 2 * inset) * progress
        val hum = 0.85f + 0.15f * kotlin.math.sin(now / 160f)
        tubeGlow.alpha = (60 * hum).toInt()
        canvas.drawLine(left + inset, y, end, y, tubeGlow)
        tube.alpha = 255
        canvas.drawLine(left + inset, y, end, y, tube)
        canvas.drawLine(left + inset, y, end, y, tubeCore)
    }
}
