package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.district9.neonsteps.R
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts
import com.district9.neonsteps.util.Format

/**
 * Modal profile panel: height and weight, each with − / + buttons (hold to keep going),
 * and a readout of the stride and energy they produce. Tapping outside closes it.
 */
class ProfileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    /** Called with the new height (cm) and weight (kg); 0 means "not set". */
    var onChange: ((Int, Int) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var heightCm = 0
    private var weightKg = 0
    private var stride = 0.75
    private var kcalPerStep = 0.04

    private val heightDown = button(R.string.cd_height_down) { stepHeight(-1) }
    private val heightUp = button(R.string.cd_height_up) { stepHeight(+1) }
    private val weightDown = button(R.string.cd_weight_down) { stepWeight(-1) }
    private val weightUp = button(R.string.cd_weight_up) { stepWeight(+1) }
    private val buttons = listOf(heightDown, heightUp, weightDown, weightUp)

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
        color = Neon.CYAN
    }
    private val kicker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(13f)
        letterSpacing = 0.3f
        color = Neon.CYAN
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(12f)
        letterSpacing = 0.16f
        color = Neon.TEXT_MUTED
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(24f)
        letterSpacing = 0.08f
        color = Neon.TEXT
    }
    private val note = Paint(label).apply {
        textSize = sp(11f)
        letterSpacing = 0.1f
    }
    private val panelRect = RectF()
    private val bracketPath = Path()
    private val rowTop = FloatArray(2)

    init {
        setWillNotDraw(false)
        visibility = GONE
        isClickable = true
        buttons.forEach { addView(it) }
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun button(description: Int, step: () -> Unit) = NeonButtonView(context).apply {
        accent = Neon.CYAN
        contentDescription = context.getString(description)
        setOnClickListener { step() }
        // Holding keeps stepping, faster the longer you hold.
        setOnLongClickListener {
            val view = this
            var delay = 120L
            val repeat = object : Runnable {
                override fun run() {
                    if (!view.isPressed) return
                    step()
                    delay = (delay * 0.85f).toLong().coerceAtLeast(35L)
                    view.postDelayed(this, delay)
                }
            }
            post(repeat)
            true
        }
    }

    fun setData(heightCm: Int, weightKg: Int, strideMeters: Double, kcalPerStep: Double) {
        this.heightCm = heightCm
        this.weightKg = weightKg
        this.stride = strideMeters
        this.kcalPerStep = kcalPerStep
        heightDown.setText("−", "", context.getString(R.string.cd_height_down))
        heightUp.setText("+", "", context.getString(R.string.cd_height_up))
        weightDown.setText("−", "", context.getString(R.string.cd_weight_down))
        weightUp.setText("+", "", context.getString(R.string.cd_weight_up))
        contentDescription = context.getString(
            R.string.cd_profile,
            if (heightCm > 0) "$heightCm cm" else context.getString(R.string.profile_unset),
            if (weightKg > 0) "$weightKg kg" else context.getString(R.string.profile_unset),
        )
        invalidate()
    }

    private fun stepHeight(delta: Int) {
        val base = if (heightCm > 0) heightCm else DEFAULT_HEIGHT - delta
        onChange?.invoke((base + delta).coerceIn(MIN_HEIGHT, MAX_HEIGHT), weightKg)
    }

    private fun stepWeight(delta: Int) {
        val base = if (weightKg > 0) weightKg else DEFAULT_WEIGHT - delta
        onChange?.invoke(heightCm, (base + delta).coerceIn(MIN_WEIGHT, MAX_WEIGHT))
    }

    fun show() {
        visibility = VISIBLE
        heightUp.requestFocus()
    }

    fun hide() {
        visibility = GONE
    }

    val isOpen: Boolean get() = visibility == VISIBLE

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()
        val side = 16f * dp
        val available = h - paddingTop - paddingBottom
        val panelH = minOf(330f * dp, available - 16f * dp).coerceAtLeast(minOf(260f * dp, h * 0.9f))
        val panelTop = (paddingTop + (available - panelH) / 2f).coerceIn(0f, (h - panelH).coerceAtLeast(0f))
        panelRect.set(side, panelTop, w - side, panelTop + panelH)

        val size = (56f * dp).toInt()
        val gap = (10f * dp).toInt()
        rowTop[0] = panelRect.top + 66f * dp
        rowTop[1] = rowTop[0] + 92f * dp
        for ((row, pair) in listOf(heightDown to heightUp, weightDown to weightUp).withIndex()) {
            val y = rowTop[row].toInt()
            val upRight = (panelRect.right - 18f * dp).toInt()
            layoutButton(pair.second, upRight - size, y, size)
            layoutButton(pair.first, upRight - 2 * size - gap, y, size)
        }
    }

    private fun layoutButton(v: View, x: Int, y: Int, size: Int) {
        v.measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
        v.layout(x, y, x + size, y + size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !panelRect.contains(event.x, event.y)) {
            performClick()
            onDismiss?.invoke()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backdrop)
        canvas.drawRect(panelRect, panel)
        canvas.drawRect(panelRect, border)
        val arm = 16f * dp
        val o = bracket.strokeWidth / 2
        bracketPath.reset()
        bracketPath.moveTo(panelRect.left + o, panelRect.top + arm)
        bracketPath.lineTo(panelRect.left + o, panelRect.top + o)
        bracketPath.lineTo(panelRect.left + arm, panelRect.top + o)
        bracketPath.moveTo(panelRect.right - o, panelRect.bottom - arm)
        bracketPath.lineTo(panelRect.right - o, panelRect.bottom - o)
        bracketPath.lineTo(panelRect.right - arm, panelRect.bottom - o)
        canvas.drawPath(bracketPath, bracket)

        val l = panelRect.left + 18f * dp
        canvas.drawText(context.getString(R.string.profile_title), l, panelRect.top + 34f * dp, kicker)

        val rows = arrayOf(
            context.getString(R.string.profile_height) to if (heightCm > 0) "$heightCm CM" else "— CM",
            context.getString(R.string.profile_weight) to if (weightKg > 0) "$weightKg KG" else "— KG",
        )
        for ((i, row) in rows.withIndex()) {
            val y = rowTop[i]
            canvas.drawText(row.first, l, y + 14f * dp, label)
            canvas.drawText(row.second, l, y + 46f * dp, value)
        }

        // What the numbers turn into.
        val info = context.getString(
            R.string.profile_result,
            Format.meters(stride),
            Format.steps(Math.round(kcalPerStep * 1000).toInt()),
        )
        val infoY = rowTop[1] + 92f * dp
        canvas.drawText(info, l, infoY, label)
        if (heightCm == 0 || weightKg == 0) {
            canvas.drawText(context.getString(R.string.profile_defaults), l, infoY + 20f * dp, note)
        }
    }

    private companion object {
        const val DEFAULT_HEIGHT = 170
        const val DEFAULT_WEIGHT = 70
        const val MIN_HEIGHT = 120
        const val MAX_HEIGHT = 220
        const val MIN_WEIGHT = 30
        const val MAX_WEIGHT = 200
    }
}
