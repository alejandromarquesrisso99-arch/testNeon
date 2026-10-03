package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.district9.neonsteps.R
import com.district9.neonsteps.data.Cosmetics
import com.district9.neonsteps.data.Mission
import com.district9.neonsteps.data.MissionState
import com.district9.neonsteps.ui.Neon
import com.district9.neonsteps.ui.NeonFonts

/**
 * The day's mission as a job card from the newswire: what to do, how far along it is and,
 * in a box on the right, the reward it pays. Tapping it opens the collection of rewards.
 */
class MissionCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fonts = NeonFonts.get(context)
    private val dp = resources.displayMetrics.density

    private var mission: Mission? = null
    private var progress = 0
    private var state = MissionState.ACTIVE
    private var flashAt = -1L

    private val panel = Paint().apply { color = Neon.PANEL }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Neon.alpha(0xFF6F7BD0.toInt(), 0.35f)
    }
    private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val flash = Paint()
    private val kicker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        isFakeBoldText = true
        letterSpacing = 0.22f
    }
    private val status = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        letterSpacing = 0.12f
    }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        isFakeBoldText = true
        letterSpacing = 0.06f
        color = Neon.TEXT
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.mono
        letterSpacing = 0.1f
        color = Neon.TEXT_MUTED
    }
    private val centered = Paint(small).apply { textAlign = Paint.Align.CENTER }
    private val segOn = Paint()
    private val segOff = Paint()
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val boxFill = Paint()
    private val icon = RewardIcon(dp)
    private val bracketPath = Path()
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    fun setData(mission: Mission, progress: Int, state: MissionState) {
        if (mission == this.mission && progress == this.progress && state == this.state) return
        this.mission = mission
        this.progress = progress
        this.state = state
        val reward = Cosmetics.rewardName(mission.reward)
        contentDescription = context.getString(
            when (state) {
                MissionState.ACTIVE -> R.string.cd_mission_active
                MissionState.DONE -> R.string.cd_mission_done
                MissionState.FAILED -> R.string.cd_mission_failed
            },
            mission.title,
            mission.progressLabel(progress),
            reward,
        ) + " " + context.getString(R.string.cd_mission_action)
        invalidate()
    }

    /** A brief glow when the mission is completed with the app open. */
    fun flash() {
        flashAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = paddingTop + paddingBottom + (78f * dp).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val m = mission ?: return
        val l = paddingLeft.toFloat()
        val t = paddingTop.toFloat()
        val r = (width - paddingRight).toFloat()
        val b = (height - paddingBottom).toFloat()
        val accent = when (state) {
            MissionState.ACTIVE -> ORANGE
            MissionState.DONE -> GREEN
            MissionState.FAILED -> Neon.TEXT_DIM
        }
        val rewardColor = Cosmetics.rewardColor(m.reward)

        canvas.drawRect(l, t, r, b, panel)
        val since = SystemClock.uptimeMillis() - flashAt
        val glow = if (flashAt >= 0 && since < FLASH_MS) 1f - since.toFloat() / FLASH_MS else 0f
        val pressed = if (isPressed || isFocused) 0.12f else 0f
        if (glow > 0f || pressed > 0f) {
            flash.color = Neon.alpha(if (glow > 0f) rewardColor else accent, pressed + 0.3f * glow * (0.6f + 0.4f * kotlin.math.sin(since / 70f)))
            canvas.drawRect(l, t, r, b, flash)
        }
        border.color = Neon.alpha(if (pressed > 0f) accent else 0xFF6F7BD0.toInt(), if (pressed > 0f) 0.9f else 0.35f)
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
        bracket.color = accent
        canvas.drawPath(bracketPath, bracket)

        // Reward box on the right.
        val boxW = 96f * dp
        val boxR = r - 10f * dp
        val boxL = boxR - boxW
        rect.set(boxL, t + 9f * dp, boxR, b - 9f * dp)
        boxFill.color = Neon.alpha(rewardColor, if (state == MissionState.FAILED) 0.04f else 0.1f)
        canvas.drawRect(rect, boxFill)
        box.color = Neon.alpha(rewardColor, if (state == MissionState.FAILED) 0.25f else 0.6f)
        canvas.drawRect(rect, box)
        val cx = rect.centerX()
        centered.textSize = sp(9f)
        centered.letterSpacing = 0.18f
        centered.color = if (state == MissionState.DONE) GREEN else Neon.TEXT_MUTED
        val boxLabel = context.getString(if (state == MissionState.DONE) R.string.mission_unlocked else R.string.mission_reward)
        fit(centered, boxLabel, boxW - 8f * dp, sp(9f))
        canvas.drawText(boxLabel, cx, rect.top + 13f * dp, centered)
        val iconAlpha = if (state == MissionState.FAILED) 0.35f else 1f
        icon.draw(canvas, m.reward, cx, rect.centerY() + 1f * dp, 13f * dp, rewardColor, iconAlpha, (SystemClock.uptimeMillis() % 100_000L) / 1000f)
        val name = Cosmetics.rewardName(m.reward)
        centered.color = Neon.alpha(rewardColor, iconAlpha)
        centered.letterSpacing = 0.08f
        fit(centered, name, boxW - 10f * dp, sp(10f))
        canvas.drawText(name, cx, rect.bottom - 6f * dp, centered)

        // Mission text on the left.
        val x = l + 14f * dp
        val colR = boxL - 12f * dp
        val colW = colR - x
        val kick = context.getString(R.string.mission_kicker)
        kicker.color = accent
        fit(kicker, kick, colW * 0.55f, sp(11f))
        canvas.drawText(kick, x, t + 20f * dp, kicker)
        val statusText = when (state) {
            MissionState.ACTIVE ->
                if (m.deadlineHour >= 24) context.getString(R.string.mission_until_midnight)
                else context.getString(R.string.mission_until, m.deadlineHour)
            MissionState.DONE -> context.getString(R.string.mission_done)
            MissionState.FAILED -> context.getString(R.string.mission_failed)
        }
        status.color = when (state) {
            MissionState.ACTIVE -> Neon.TEXT_MUTED
            MissionState.DONE -> GREEN
            MissionState.FAILED -> Neon.MAGENTA
        }
        status.textAlign = Paint.Align.RIGHT
        fit(status, statusText, colW - kicker.measureText(kick) - 8f * dp, sp(10f))
        canvas.drawText(statusText, colR, t + 20f * dp, status)

        title.color = if (state == MissionState.FAILED) Neon.TEXT_MUTED else Neon.TEXT
        fit(title, m.title, colW, sp(13.5f))
        canvas.drawText(m.title, x, t + 42f * dp, title)

        // Segmented progress bar and the numbers.
        val label = m.progressLabel(progress)
        fit(small, label, colW * 0.45f, sp(11f))
        val labelW = small.measureText(label)
        val barR = colR - labelW - 8f * dp
        val barTop = t + 52f * dp
        val barBottom = barTop + 6f * dp
        val segments = 16
        val gap = 2f * dp
        val segW = ((barR - x) - gap * (segments - 1)) / segments
        val frac = (progress.toFloat() / m.target.coerceAtLeast(1)).coerceIn(0f, 1f)
        val lit = if (state == MissionState.DONE) segments else (frac * segments).toInt()
        segOn.color = accent
        segOff.color = Neon.alpha(accent, 0.18f)
        for (k in 0 until segments) {
            val sx = x + k * (segW + gap)
            canvas.drawRect(sx, barTop, sx + segW, barBottom, if (k < lit) segOn else segOff)
        }
        small.color = if (state == MissionState.DONE) GREEN else Neon.TEXT
        canvas.drawText(label, colR - labelW, barBottom + 1f * dp, small)

        small.color = Neon.TEXT_DIM
        val hint = context.getString(
            when (state) {
                MissionState.ACTIVE -> R.string.mission_hint_active
                MissionState.DONE ->
                    if (m.reward == Cosmetics.FIREWORKS) R.string.mission_hint_done_fireworks else R.string.mission_hint_done
                MissionState.FAILED -> R.string.mission_hint_failed
            },
        )
        fit(small, hint, colW, sp(9.5f))
        canvas.drawText(hint, x, b - 9f * dp, small)

        if (glow > 0f) postInvalidateOnAnimation()
    }

    private fun fit(p: Paint, text: String, maxWidth: Float, preferred: Float) {
        p.textSize = preferred
        val w = p.measureText(text)
        if (w > maxWidth && w > 0f) p.textSize = preferred * maxWidth / w
    }

    companion object {
        /** The newswire's job-board orange: missions in progress. */
        const val ORANGE = 0xFFFF8A1E.toInt()
        const val GREEN = 0xFF3DFFA8.toInt()
        private const val FLASH_MS = 2_400L
    }
}

/** Tiny line icons for the rewards: drops, a koi, the moon, an aurora, a firework. */
internal class RewardIcon(private val dp: Float) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()

    /** [r] is the icon's radius; [t] seconds, for the ones that move. */
    fun draw(canvas: Canvas, id: String, cx: Float, cy: Float, r: Float, color: Int, alpha: Float, t: Float) {
        stroke.strokeWidth = 1.6f * dp
        stroke.color = Neon.alpha(color, alpha)
        fill.color = Neon.alpha(color, alpha)
        val item = Cosmetics.byId(id)
        when {
            item == null -> firework(canvas, cx, cy, r, t)
            item.slot == Cosmetics.Slot.RAIN -> rain(canvas, cx, cy, r)
            item.slot == Cosmetics.Slot.KOI -> koi(canvas, cx, cy, r)
            item.id == "sky_moon" -> moon(canvas, cx, cy, r, color, alpha)
            else -> aurora(canvas, cx, cy, r, t)
        }
    }

    private fun rain(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (k in -1..1) {
            val x = cx + k * r * 0.6f
            val y = cy - r * 0.15f + (if (k == 0) -r * 0.25f else 0f)
            canvas.drawLine(x + r * 0.18f, y - r * 0.55f, x - r * 0.05f, y + r * 0.45f, stroke)
        }
        oval.set(cx - r, cy + r * 0.62f, cx + r, cy + r * 0.98f)
        canvas.drawOval(oval, stroke)
    }

    private fun koi(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        oval.set(cx - r * 0.85f, cy - r * 0.38f, cx + r * 0.5f, cy + r * 0.38f)
        canvas.drawOval(oval, stroke)
        path.reset()
        path.moveTo(cx + r * 0.45f, cy)
        path.lineTo(cx + r, cy - r * 0.45f)
        path.lineTo(cx + r * 0.88f, cy)
        path.lineTo(cx + r, cy + r * 0.45f)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawCircle(cx - r * 0.55f, cy - r * 0.08f, 1.4f * dp, fill)
        canvas.drawLine(cx - r * 0.2f, cy - r * 0.38f, cx + r * 0.05f, cy - r * 0.62f, stroke)
    }

    private fun moon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        fill.color = Neon.alpha(color, 0.85f * alpha)
        canvas.drawCircle(cx, cy, r * 0.8f, fill)
        fill.color = Neon.alpha(0xFF8C7FA6.toInt(), 0.5f * alpha)
        canvas.drawCircle(cx - r * 0.25f, cy - r * 0.15f, r * 0.18f, fill)
        canvas.drawCircle(cx + r * 0.25f, cy + r * 0.25f, r * 0.12f, fill)
    }

    private fun aurora(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        for (k in 0 until 3) {
            path.reset()
            val y0 = cy - r * 0.5f + k * r * 0.5f
            path.moveTo(cx - r, y0)
            val step = r / 4f
            var x = cx - r
            while (x < cx + r) {
                x += step
                path.lineTo(x, y0 + r * 0.22f * kotlin.math.sin((x - cx) / r * 3.2f + t * 1.6f + k))
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun firework(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val pulse = 0.75f + 0.25f * kotlin.math.sin(t * 3f)
        for (k in 0 until 8) {
            val a = k * Math.PI.toFloat() / 4f
            val c = kotlin.math.cos(a)
            val s = kotlin.math.sin(a)
            canvas.drawLine(cx + c * r * 0.3f, cy + s * r * 0.3f, cx + c * r * pulse, cy + s * r * pulse, stroke)
        }
        canvas.drawCircle(cx, cy, 1.8f * dp, fill)
    }
}
