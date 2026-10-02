package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/** HUD toggle: label over a coloured value, hairline border and a lit corner tab. */
class NeonButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    var accent: Int = Neon.CYAN
        set(value) {
            field = value
            invalidate()
        }
    private var label = ""
    private var value = ""
    private var pulseAt = 0L

    private val bg = Paint().apply { color = Neon.PANEL }
    private val fill = Paint()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val tab = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        isFakeBoldText = true
        letterSpacing = 0.2f
        color = Neon.TEXT
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(labelPaint).apply { letterSpacing = 0.12f }
    private val tabPath = Path()

    init {
        isClickable = true
        isFocusable = true
    }

    fun setText(label: String, value: String, description: String) {
        this.label = label
        this.value = value
        contentDescription = description
        invalidate()
    }

    override fun performClick(): Boolean {
        pulseAt = SystemClock.uptimeMillis()
        invalidate()
        return super.performClick()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((58f * dp).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val o = border.strokeWidth / 2
        canvas.drawRect(0f, 0f, w, h, bg)

        val since = SystemClock.uptimeMillis() - pulseAt
        val pulse = if (since < 450) 1f - since / 450f else 0f
        val active = isPressed || isFocused
        val fillAlpha = (if (active) 0.16f else 0f) + 0.28f * pulse
        if (fillAlpha > 0f) {
            fill.color = Neon.alpha(accent, fillAlpha)
            canvas.drawRect(0f, 0f, w, h, fill)
        }
        border.color = Neon.alpha(accent, if (active) 1f else 0.6f)
        canvas.drawRect(o, o, w - o, h - o, border)

        val tabSize = 12f * dp
        tabPath.reset()
        tabPath.moveTo(w - tabSize, 0f)
        tabPath.lineTo(w, 0f)
        tabPath.lineTo(w, tabSize)
        tabPath.close()
        tab.color = accent
        canvas.drawPath(tabPath, tab)

        fit(labelPaint, label, w - 16f * dp, sp(13f))
        fit(valuePaint, value, w - 16f * dp, sp(13.5f))
        valuePaint.color = accent
        canvas.drawText(label, w / 2, h * 0.45f, labelPaint)
        canvas.drawText(value, w / 2, h * 0.78f, valuePaint)

        if (pulse > 0f) postInvalidateOnAnimation()
    }

    private fun fit(p: Paint, text: String, maxWidth: Float, preferred: Float) {
        p.textSize = preferred
        val m = p.measureText(text)
        if (m > maxWidth) p.textSize = preferred * maxWidth / m
    }
}
