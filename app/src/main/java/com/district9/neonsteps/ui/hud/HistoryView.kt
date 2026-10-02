package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.district9.neonsteps.R
import com.district9.neonsteps.data.DayEntry
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts
import com.district9.neonsteps.util.Format
import java.time.format.TextStyle
import java.util.Locale

/**
 * Modal 7-day history: one column per day against a goal reference line. Today is the
 * magenta column; tapping a column reads out its value.
 */
class HistoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density
    private val spanish = Locale.forLanguageTag("es")

    private var days: List<DayEntry> = emptyList()
    private var goal = 8000
    private var selected = -1
    private var shownAt = 0L

    var onDismiss: (() -> Unit)? = null

    private val backdrop = Paint().apply { color = 0x9905010F.toInt() }
    private val panel = Paint().apply { color = 0xF00A0418.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Neon.alpha(0xFF6F7BD0.toInt(), 0.4f)
    }
    private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Neon.YELLOW
    }
    private val kicker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(13f)
        letterSpacing = 0.3f
        color = Neon.CYAN
    }
    private val statLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(11f)
        letterSpacing = 0.14f
        color = Neon.TEXT_MUTED
    }
    private val statValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(16f)
        letterSpacing = 0.08f
        color = Neon.TEXT
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(12f)
        letterSpacing = 0.1f
        color = Neon.TEXT_MUTED
        textAlign = Paint.Align.CENTER
    }
    private val valueText = Paint(axisText).apply {
        color = Neon.TEXT
        textSize = sp(13f)
    }
    private val baseline = Paint().apply { color = Neon.alpha(Neon.TEXT_MUTED, 0.35f) }
    private val goalLine = Paint().apply { color = Neon.alpha(Neon.YELLOW, 0.75f) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPath = Path()
    private val panelRect = RectF()
    private val chartRect = RectF()
    private val bracketPath = Path()

    init {
        visibility = GONE
        isClickable = true
        isFocusable = true
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    fun setData(days: List<DayEntry>, goal: Int) {
        this.days = days
        this.goal = goal
        if (selected !in days.indices) selected = days.lastIndex
        contentDescription = days.joinToString(". ") { d ->
            "${d.date.dayOfWeek.getDisplayName(TextStyle.FULL, spanish)} ${d.date.dayOfMonth}: ${Format.steps(d.steps)} pasos"
        }
        invalidate()
    }

    fun show() {
        selected = days.lastIndex
        shownAt = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    val isOpen: Boolean get() = visibility == VISIBLE

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        performClick()
        if (!panelRect.contains(event.x, event.y)) {
            onDismiss?.invoke()
            return true
        }
        if (chartRect.contains(event.x, event.y) && days.isNotEmpty()) {
            val slot = chartRect.width() / days.size
            selected = ((event.x - chartRect.left) / slot).toInt().coerceIn(0, days.lastIndex)
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, backdrop)

        val side = 16f * dp
        val available = h - paddingTop - paddingBottom
        val panelH = minOf(420f * dp, available - 16f * dp).coerceAtLeast(minOf(280f * dp, h * 0.9f))
        val top = (paddingTop + (available - panelH) / 2f).coerceIn(0f, (h - panelH).coerceAtLeast(0f))
        panelRect.set(side, top, w - side, top + panelH)
        canvas.drawRect(panelRect, panel)
        canvas.drawRect(panelRect, border)
        drawBrackets(canvas)

        val padX = 18f * dp
        val l = panelRect.left + padX
        val r = panelRect.right - padX
        var y = panelRect.top + 30f * dp
        canvas.drawText(context.getString(R.string.history_title), l, y, kicker)

        // Summary figures.
        y += 30f * dp
        val values = days.map { it.steps }
        val avg = if (values.isEmpty()) 0 else values.sum() / values.size
        val best = values.maxOrNull() ?: 0
        val hits = values.count { it >= goal }
        val stats = listOf(
            context.getString(R.string.history_avg) to Format.steps(avg),
            context.getString(R.string.history_best) to Format.steps(best),
            context.getString(R.string.history_goal_days) to "$hits/${values.size}",
        )
        val colW = (r - l) / stats.size
        stats.forEachIndexed { i, (label, value) ->
            canvas.drawText(label, l + i * colW, y, statLabel)
            canvas.drawText(value, l + i * colW, y + 22f * dp, statValue)
        }

        // Chart.
        val axisH = 40f * dp
        chartRect.set(l, y + 78f * dp, r, panelRect.bottom - 18f * dp - axisH)
        if (days.isEmpty()) return
        val scaleMax = maxOf(goal * 1.15f, best * 1.12f, 1f)
        val grow = ((SystemClock.uptimeMillis() - shownAt) / 520f).coerceIn(0f, 1f)
        val ease = 1f - (1f - grow) * (1f - grow) * (1f - grow)
        fun yFor(v: Float) = chartRect.bottom - chartRect.height() * (v / scaleMax)

        // Goal reference line; its key sits above the plot so no column can cover it.
        val gy = yFor(goal.toFloat())
        val goalLabel = context.getString(R.string.subtitle_goal, Format.steps(goal))
        axisText.textAlign = Paint.Align.RIGHT
        val keyY = chartRect.top - 22f * dp
        canvas.drawText(goalLabel, chartRect.right, keyY, axisText)
        val keyRight = chartRect.right - axisText.measureText(goalLabel) - 8f * dp
        canvas.drawRect(keyRight - 14f * dp, keyY - 5f * dp, keyRight, keyY - 3f * dp, goalLine)
        axisText.textAlign = Paint.Align.CENTER

        canvas.drawRect(chartRect.left, chartRect.bottom, chartRect.right, chartRect.bottom + 1f * dp, baseline)

        val slot = chartRect.width() / days.size
        val barW = minOf(24f * dp, slot * 0.56f)
        val radius = 4f * dp
        for ((i, d) in days.withIndex()) {
            val cx = chartRect.left + slot * (i + 0.5f)
            val isToday = i == days.lastIndex
            val color = if (isToday) Neon.MAGENTA else Neon.CYAN
            val v = d.steps * ease
            val top0 = yFor(v)
            if (d.steps > 0 && chartRect.bottom - top0 > 0.5f) {
                if (i == selected) {
                    glow.color = Neon.alpha(color, 0.22f)
                    canvas.drawRoundRect(cx - barW / 2 - 4f * dp, top0 - 4f * dp, cx + barW / 2 + 4f * dp, chartRect.bottom, radius * 2, radius * 2, glow)
                }
                bar.color = Neon.alpha(color, if (i == selected) 1f else 0.72f)
                roundedTopBar(cx - barW / 2, top0, cx + barW / 2, chartRect.bottom, radius)
                canvas.drawPath(barPath, bar)
            }
            if (i == selected) {
                canvas.drawText(Format.steps(d.steps), cx, top0 - 8f * dp, valueText)
            }
            val dayLabel = if (isToday) {
                context.getString(R.string.history_today)
            } else {
                d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, spanish).uppercase(spanish)
            }
            axisText.color = if (isToday || i == selected) Neon.TEXT else Neon.TEXT_MUTED
            canvas.drawText(dayLabel, cx, chartRect.bottom + 18f * dp, axisText)
            axisText.color = Neon.TEXT_DIM
            canvas.drawText(d.date.dayOfMonth.toString(), cx, chartRect.bottom + 34f * dp, axisText)
            axisText.color = Neon.TEXT_MUTED
        }
        // The reference line rides over the columns so it stays readable where they cross it.
        canvas.drawRect(chartRect.left, gy - 0.5f * dp, chartRect.right, gy + 0.5f * dp, goalLine)
        if (grow < 1f) postInvalidateOnAnimation()
    }

    /** Column with a rounded data end and a square baseline. */
    private fun roundedTopBar(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val rr = minOf(radius, (b - t), (r - l) / 2)
        barPath.reset()
        barPath.moveTo(l, b)
        barPath.lineTo(l, t + rr)
        barPath.quadTo(l, t, l + rr, t)
        barPath.lineTo(r - rr, t)
        barPath.quadTo(r, t, r, t + rr)
        barPath.lineTo(r, b)
        barPath.close()
    }

    private fun drawBrackets(canvas: Canvas) {
        val arm = 16f * dp
        val o = bracket.strokeWidth / 2
        val (l, t, r, b) = listOf(panelRect.left, panelRect.top, panelRect.right, panelRect.bottom)
        bracketPath.reset()
        bracketPath.moveTo(r - arm, t + o)
        bracketPath.lineTo(r - o, t + o)
        bracketPath.lineTo(r - o, t + arm)
        bracketPath.moveTo(l + o, b - arm)
        bracketPath.lineTo(l + o, b - o)
        bracketPath.lineTo(l + arm, b - o)
        canvas.drawPath(bracketPath, bracket)
    }
}
