package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.district9.neonsteps.R
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/** The thin monospace readout: local time, distance, calories and goal "signal" bars. */
class HudPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    private var time = "--:--:--"
    private var distance = "0,00 KM"
    private var kcal = "0 KCAL"
    private var percent = 0

    private val labels = arrayOf(
        context.getString(R.string.hud_time),
        context.getString(R.string.hud_distance),
        context.getString(R.string.hud_calories),
        context.getString(R.string.hud_goal),
    )

    private val panel = Paint().apply { color = Neon.PANEL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Neon.alpha(0xFF6F7BD0.toInt(), 0.35f)
    }
    private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Neon.CYAN
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(11f)
        letterSpacing = 0.16f
        color = Neon.TEXT_MUTED
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        letterSpacing = 0.1f
        color = Neon.TEXT
    }
    private val barOn = Paint().apply { color = Neon.CYAN }
    private val barOff = Paint().apply { color = Neon.alpha(Neon.CYAN, 0.2f) }
    private val bracketPath = Path()

    fun setData(time: String, distance: String, kcal: String, percent: Int) {
        if (time == this.time && distance == this.distance && kcal == this.kcal && percent == this.percent) return
        this.time = time
        this.distance = distance
        this.kcal = kcal
        this.percent = percent
        contentDescription = context.getString(R.string.cd_hud, time, distance, kcal, percent) + " " +
            context.getString(R.string.cd_hud_action)
        invalidate()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = paddingTop + paddingBottom + (62f * dp).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val l = paddingLeft.toFloat()
        val t = paddingTop.toFloat()
        val r = (width - paddingRight).toFloat()
        val b = (height - paddingBottom).toFloat()
        canvas.drawRect(l, t, r, b, panel)
        canvas.drawRect(l, t, r, b, border)

        val arm = 14f * dp
        val o = bracket.strokeWidth / 2
        bracketPath.reset()
        bracketPath.moveTo(l + o, t + arm)
        bracketPath.lineTo(l + o, t + o)
        bracketPath.lineTo(l + arm, t + o)
        bracketPath.moveTo(r - o, b - arm)
        bracketPath.lineTo(r - o, b - o)
        bracketPath.lineTo(r - arm, b - o)
        canvas.drawPath(bracketPath, bracket)

        val padX = 14f * dp
        val unit = (r - l - 2 * padX) / COLUMN_WEIGHTS.sum()
        val labelY = t + (b - t) * 0.4f
        val valueY = t + (b - t) * 0.78f
        val values = arrayOf(time, distance, kcal)
        var x = l + padX
        for (i in 0 until 4) {
            val colW = unit * COLUMN_WEIGHTS[i]
            val room = colW - 10f * dp
            fitText(label, labels[i], room, sp(11f))
            canvas.drawText(labels[i], x, labelY, label)
            if (i < 3) {
                fitText(value, values[i], room, sp(14f))
                canvas.drawText(values[i], x, valueY, value)
            } else {
                drawGoalBars(canvas, x, valueY, colW)
            }
            x += colW
        }
    }

    private companion object {
        val COLUMN_WEIGHTS = floatArrayOf(1f, 1.04f, 1.04f, 0.98f)
    }

    private fun fitText(p: Paint, text: String, maxWidth: Float, preferred: Float) {
        p.textSize = preferred
        val w = p.measureText(text)
        if (w > maxWidth) p.textSize = preferred * maxWidth / w
    }

    private fun drawGoalBars(canvas: Canvas, x: Float, baseline: Float, colW: Float) {
        val barW = 3.5f * dp
        val gap = 2f * dp
        val lit = when {
            percent >= 100 -> 5
            percent <= 0 -> 0
            else -> 1 + percent * 4 / 100
        }
        for (k in 0 until 5) {
            val h = (4f + k * 2.6f) * dp
            val bx = x + k * (barW + gap)
            canvas.drawRect(bx, baseline - h, bx + barW, baseline, if (k < lit) barOn else barOff)
        }
        val text = "${percent.coerceAtMost(999)}%"
        val tx = x + 5 * (barW + gap) + 6f * dp
        fitText(value, text, colW - (tx - x) - 2f * dp, sp(14f))
        canvas.drawText(text, tx, baseline, value)
    }
}
