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
import com.district9.neonsteps.data.Cosmetics
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/**
 * Modal collection of mission rewards: one row per slot (rain, koi, sky) to pick what
 * District 9 wears, and a strip with every reward, the locked ones still a mystery.
 * Tapping outside closes it.
 */
class StyleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    /** Called with a slot and the style to wear in it (null = classic). */
    var onSelect: ((Cosmetics.Slot, String?) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var unlocked: Set<String> = emptySet()
    private val selected = HashMap<Cosmetics.Slot, String?>()
    private var nextReward: String? = null

    private val slots = Cosmetics.Slot.entries
    private val prevButtons = slots.map { slot -> button(R.string.cd_style_prev, slot, -1) }
    private val nextButtons = slots.map { slot -> button(R.string.cd_style_next, slot, +1) }

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
        color = MissionCardView.ORANGE
    }
    private val kicker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(13f)
        letterSpacing = 0.3f
        color = MissionCardView.ORANGE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textSize = sp(12f)
        letterSpacing = 0.16f
        color = Neon.TEXT_MUTED
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        isFakeBoldText = true
        letterSpacing = 0.08f
    }
    private val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        textAlign = Paint.Align.CENTER
        color = Neon.TEXT_DIM
    }
    private val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val cellFill = Paint()
    private val icon = RewardIcon(dp)
    private val panelRect = RectF()
    private val bracketPath = Path()
    private val rect = RectF()
    private val rowTop = FloatArray(slots.size)
    private var stripTop = 0f

    init {
        setWillNotDraw(false)
        visibility = GONE
        isClickable = true
        (prevButtons + nextButtons).forEach { addView(it) }
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private fun button(description: Int, slot: Cosmetics.Slot, step: Int) = NeonButtonView(context).apply {
        accent = MissionCardView.ORANGE
        setText(if (step < 0) "◀" else "▶", "", context.getString(description, slotName(slot)))
        setOnClickListener { cycle(slot, step) }
    }

    private fun slotName(slot: Cosmetics.Slot) = context.getString(
        when (slot) {
            Cosmetics.Slot.RAIN -> R.string.style_rain
            Cosmetics.Slot.KOI -> R.string.style_koi
            Cosmetics.Slot.SKY -> R.string.style_sky
        },
    )

    /** Classic first, then what's been unlocked for [slot], in collection order. */
    private fun options(slot: Cosmetics.Slot): List<String?> =
        listOf<String?>(null) + Cosmetics.all.filter { it.slot == slot && it.id in unlocked }.map { it.id }

    private fun cycle(slot: Cosmetics.Slot, step: Int) {
        val opts = options(slot)
        if (opts.size < 2) return
        val i = opts.indexOf(selected[slot]).coerceAtLeast(0)
        onSelect?.invoke(slot, opts[(i + step + opts.size) % opts.size])
    }

    fun setData(unlocked: Set<String>, selected: Map<Cosmetics.Slot, String?>, nextReward: String?) {
        this.unlocked = unlocked
        this.selected.clear()
        this.selected.putAll(selected)
        this.nextReward = nextReward
        val count = Cosmetics.all.count { it.id in unlocked }
        contentDescription = context.getString(
            R.string.cd_styles,
            count,
            Cosmetics.all.size,
            slots.joinToString(", ") { "${slotName(it)}: ${Cosmetics.byId(selected[it])?.name ?: context.getString(R.string.style_classic)}" },
        )
        invalidate()
    }

    fun show() {
        visibility = VISIBLE
        nextButtons.first().requestFocus()
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
        val rowH = 66f * dp
        val wanted = 54f * dp + rowH * slots.size + 96f * dp
        val panelH = minOf(wanted, available - 16f * dp).coerceAtLeast(minOf(wanted * 0.85f, h * 0.9f))
        val panelTop = (paddingTop + (available - panelH) / 2f).coerceIn(0f, (h - panelH).coerceAtLeast(0f))
        panelRect.set(side, panelTop, w - side, panelTop + panelH)

        val scale = panelH / wanted
        val size = (48f * dp * scale.coerceAtMost(1f)).toInt()
        val gap = (8f * dp).toInt()
        for (i in slots.indices) {
            rowTop[i] = panelRect.top + (54f * dp + i * rowH) * scale
            val y = (rowTop[i] + (rowH * scale - size) / 2f).toInt()
            val upRight = (panelRect.right - 16f * dp).toInt()
            layoutButton(nextButtons[i], upRight - size, y, size)
            layoutButton(prevButtons[i], upRight - 2 * size - gap, y, size)
        }
        stripTop = panelRect.top + (54f * dp + rowH * slots.size + 8f * dp) * scale
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
        canvas.drawText(context.getString(R.string.style_title), l, panelRect.top + 34f * dp, kicker)

        // One row per slot: name, what it's wearing, and its icon.
        val textRight = (prevButtons.first().left - 10f * dp)
        for ((i, slot) in slots.withIndex()) {
            val top = rowTop[i]
            val item = Cosmetics.byId(selected[slot])
            val opts = options(slot)
            val position = "${opts.indexOf(selected[slot]).coerceAtLeast(0) + 1}/${opts.size}"
            canvas.drawText("${slotName(slot)} · $position", l, top + 22f * dp, label)
            val name = item?.name ?: context.getString(R.string.style_classic)
            val iconR = 10f * dp
            val nameX = if (item != null) l + iconR * 2 + 8f * dp else l
            value.color = item?.color ?: Neon.TEXT
            value.textSize = sp(17f)
            val room = textRight - nameX
            val m = value.measureText(name)
            if (m > room && m > 0f) value.textSize = sp(17f) * room / m
            if (item != null) icon.draw(canvas, item.id, l + iconR, top + 40f * dp, iconR, item.color, 1f, 0f)
            canvas.drawText(name, nameX, top + 46f * dp, value)
        }

        // The whole collection; locked rewards are only a question mark (the next one, a hint).
        val count = Cosmetics.all.count { it.id in unlocked }
        val gap = 6f * dp
        val n = Cosmetics.all.size
        val cellW = ((panelRect.width() - 36f * dp) - gap * (n - 1)) / n
        val cellH = cellW.coerceAtMost(40f * dp)
        for ((k, item) in Cosmetics.all.withIndex()) {
            val x = l + k * (cellW + gap)
            rect.set(x, stripTop, x + cellW, stripTop + cellH)
            val have = item.id in unlocked
            val isNext = item.id == nextReward
            cellFill.color = Neon.alpha(if (have) item.color else Neon.TEXT_DIM, if (have) 0.12f else 0.06f)
            canvas.drawRect(rect, cellFill)
            cell.color = when {
                have -> Neon.alpha(item.color, 0.7f)
                isNext -> Neon.alpha(MissionCardView.ORANGE, 0.8f)
                else -> Neon.alpha(Neon.TEXT_DIM, 0.5f)
            }
            canvas.drawRect(rect, cell)
            if (have) {
                icon.draw(canvas, item.id, rect.centerX(), rect.centerY(), cellH * 0.3f, item.color, 1f, 0f)
            } else {
                cellText.textSize = cellH * 0.45f
                cellText.color = if (isNext) MissionCardView.ORANGE else Neon.TEXT_DIM
                canvas.drawText("?", rect.centerX(), rect.centerY() + cellText.textSize * 0.36f, cellText)
            }
        }
        val footerY = stripTop + cellH + 22f * dp
        val footer = context.getString(R.string.style_count, count, n) + " · " +
            if (count < n) context.getString(R.string.style_hint) else context.getString(R.string.style_complete)
        label.textSize = sp(11f)
        val fw = label.measureText(footer)
        val room = panelRect.width() - 36f * dp
        if (fw > room) label.textSize = sp(11f) * room / fw
        canvas.drawText(footer, l, footerY, label)
        label.textSize = sp(12f)
    }
}
