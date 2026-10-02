package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.district9.neonsteps.R
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/** One headline on the newswire; highlighted ones are set in cyan. */
data class TickerItem(val text: String, val highlight: Boolean = false)

/** The D9 WIRE: a scrolling fictional newswire that also reports your walk. */
class TickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density
    private val barHeight = 40f * dp

    /** Asked for fresh headlines each time the strip has scrolled through once. */
    var provider: (() -> List<TickerItem>)? = null

    private var items: List<TickerItem> = emptyList()
    private var widths = FloatArray(0)
    private var stripWidth = 0f
    private var offset = 0f
    private var lastFrame = 0L

    private val bg = Paint().apply { color = 0xF207020F.toInt() }
    private val topLine = Paint().apply {
        color = Neon.alpha(Neon.MAGENTA, 0.45f)
        strokeWidth = 1f * dp
    }
    private val badge = Paint().apply { color = 0xFFFF2D78.toInt() }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        isFakeBoldText = true
        textSize = sp(15f)
        letterSpacing = 0.2f
        color = 0xFF2A0016.toInt()
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A0040.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(14.5f)
        letterSpacing = 0.16f
    }
    private val diamond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Neon.YELLOW }
    private val diamondPath = Path()
    private val sepGap = 22f * dp
    private val badgeLabel = context.getString(R.string.ticker_badge)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (barHeight + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    /** Breaking news: a fresh strip led by [item], scrolling in from the right edge right now. */
    fun breaking(item: TickerItem) {
        items = listOf(item) + provider?.invoke().orEmpty()
        widths = FloatArray(items.size) { text.measureText(items[it].text) }
        stripWidth = widths.sum() + items.size * (2 * sepGap + 7f * dp)
        val badgeW = 24f * dp + badgeText.measureText(badgeLabel) + 12f * dp
        offset = -(width - badgeW - 16f * dp)
        invalidate()
    }

    private fun rebuild() {
        items = provider?.invoke().orEmpty().ifEmpty { listOf(TickerItem("D9 WIRE")) }
        widths = FloatArray(items.size) { text.measureText(items[it].text) }
        stripWidth = widths.sum() + items.size * (2 * sepGap + 7f * dp)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        if (items.isEmpty()) rebuild()
        offset += dt * 64f * dp
        if (offset >= stripWidth && stripWidth > 0f) {
            offset -= stripWidth
            rebuild()
        }

        val w = width.toFloat()
        val top = paddingTop.toFloat()
        canvas.drawRect(0f, top, w, height.toFloat(), bg)
        canvas.drawLine(0f, top, w, top, topLine)
        val cy = top + barHeight / 2
        val baseline = cy + text.textSize * 0.34f

        val badgeW = 24f * dp + badgeText.measureText(badgeLabel) + 12f * dp
        canvas.save()
        canvas.clipRect(badgeW, top, w, height.toFloat())
        var x = badgeW + 16f * dp - offset
        // Draw the strip twice so the loop is seamless.
        repeat(2) {
            for (i in items.indices) {
                if (x < w && x + widths[i] > badgeW) {
                    text.color = if (items[i].highlight) Neon.CYAN else Neon.TEXT
                    canvas.drawText(items[i].text, x, baseline, text)
                }
                x += widths[i] + sepGap
                drawDiamond(canvas, x + 3.5f * dp, cy)
                x += 7f * dp + sepGap
            }
        }
        canvas.restore()

        canvas.drawRect(0f, top, badgeW, height.toFloat(), badge)
        val blink = (now / 700) % 2 == 0L
        dot.alpha = if (blink) 255 else 90
        canvas.drawCircle(13f * dp, cy, 4.5f * dp, dot)
        canvas.drawText(badgeLabel, 24f * dp, baseline, badgeText)

        postInvalidateOnAnimation()
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float) {
        val r = 4f * dp
        diamondPath.reset()
        diamondPath.moveTo(cx, cy - r)
        diamondPath.lineTo(cx + r, cy)
        diamondPath.lineTo(cx, cy + r)
        diamondPath.lineTo(cx - r, cy)
        diamondPath.close()
        canvas.drawPath(diamondPath, diamond)
    }
}
