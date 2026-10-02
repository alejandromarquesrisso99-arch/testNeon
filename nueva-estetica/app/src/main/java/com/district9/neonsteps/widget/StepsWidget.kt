package com.district9.neonsteps.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.district9.neonsteps.MainActivity
import com.district9.neonsteps.R
import com.district9.neonsteps.data.StepRepository
import com.district9.neonsteps.util.Format
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home-screen widget: today's steps as a chromatic neon title over a little District 9
 * skyline whose Tower 61 lights up (with fireworks) once the goal is met. Text is real
 * TextViews so it stays sharp; only the backdrop is a small bitmap.
 */
class StepsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, manager, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, manager, appWidgetId)
    }

    companion object {
        private var lastPush = 0L
        private var lastSignature = ""

        /**
         * Refresh every placed widget. Step-by-step updates are throttled; a change of goal,
         * goal state or streak always goes through.
         */
        fun refresh(context: Context, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, StepsWidget::class.java))
            if (ids.isEmpty()) return
            val repo = StepRepository.get(context)
            val steps = repo.stepsToday()
            val signature = "${repo.goal}|${steps >= repo.goal}|${repo.streak()}"
            val now = SystemClock.elapsedRealtime()
            if (!force && signature == lastSignature && now - lastPush < THROTTLE_MS) return
            lastSignature = signature
            lastPush = now
            for (id in ids) render(context, manager, id)
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val repo = StepRepository.get(context)
            val steps = repo.stepsToday()
            val goal = repo.goal
            val streak = repo.streak()
            val met = steps >= goal
            val percent = Format.percent(steps, goal)

            val views = RemoteViews(context.packageName, R.layout.widget_steps)
            val number = Format.steps(steps)
            views.setTextViewText(R.id.widget_steps, number)
            views.setTextViewText(R.id.widget_steps_cyan, number)
            views.setTextViewText(R.id.widget_steps_magenta, number)
            views.setTextViewText(R.id.widget_percent, "$percent%")
            views.setTextViewText(
                R.id.widget_kicker,
                if (streak > 0) context.getString(R.string.widget_kicker_streak, streak) else context.getString(R.string.widget_kicker),
            )
            views.setTextColor(R.id.widget_kicker, if (streak > 0) STREAK_ORANGE else KICKER_CYAN)
            views.setTextViewText(
                R.id.widget_subtitle,
                if (met) {
                    context.getString(R.string.widget_subtitle_done, Format.km(steps, repo.strideMeters))
                } else {
                    context.getString(R.string.widget_subtitle, Format.steps(goal))
                },
            )
            views.setProgressBar(R.id.widget_progress, 1000, (steps * 1000L / goal.coerceAtLeast(1)).toInt().coerceIn(0, 1000), false)
            views.setContentDescription(R.id.widget_steps, context.getString(R.string.cd_header, number, Format.steps(goal), percent))

            val options = manager.getAppWidgetOptions(id)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(120)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).coerceAtLeast(60)
            views.setImageViewBitmap(R.id.widget_backdrop, backdrop(context, wDp, hDp, met))

            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(id, views)
        }

        /**
         * The backdrop: sky, smog, a stepped skyline with Tower 61, scanlines. Kept small (it
         * travels to the launcher in a binder transaction) and scaled up behind the crisp text.
         */
        internal fun backdrop(context: Context, wDp: Int, hDp: Int, goalMet: Boolean): Bitmap {
            val density = context.resources.displayMetrics.density
            val scale = minOf(density, MAX_BITMAP_WIDTH / wDp.toFloat())
            val w = (wDp * scale).toInt().coerceAtLeast(1)
            val h = (hDp * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = 20f * scale
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
            c.clipPath(clip)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1A0433.toInt(), 0xFF05010F.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = RadialGradient(w * 0.7f, h * 0.95f, w * 0.6f, 0x705A1060, 0x005A1060, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            if (goalMet) {
                fun burst(cx: Float, cy: Float, radius: Float, color: Int) {
                    paint.strokeWidth = 1.6f * scale
                    paint.strokeCap = Paint.Cap.ROUND
                    for (k in 0 until 18) {
                        val a = k / 18f * 6.283f
                        paint.color = color and 0x00FFFFFF or (0xB0 shl 24)
                        c.drawLine(
                            cx + cos(a) * radius * 0.45f, cy + sin(a) * radius * 0.45f,
                            cx + cos(a) * radius, cy + sin(a) * radius, paint,
                        )
                    }
                }
                burst(w * 0.84f, h * 0.36f, h * 0.2f, 0xFFFF2D95.toInt())
                burst(w * 0.64f, h * 0.24f, h * 0.14f, 0xFF19F0FF.toInt())
                burst(w * 0.52f, h * 0.2f, h * 0.09f, 0xFFFFE81A.toInt())
            }

            // Skyline: three stepped layers along the bottom, far to near.
            val rng = java.util.Random(61)
            val layers = intArrayOf(0xFF1D0638.toInt(), 0xFF12032A.toInt(), 0xFF07020F.toInt())
            val windowColors = intArrayOf(0xFFFFD36B.toInt(), 0xFFFF6BB5.toInt(), 0xFF6BE8FF.toInt(), 0xFFA88BFF.toInt())
            for ((i, color) in layers.withIndex()) {
                var x = -rng.nextInt((12 * scale).toInt() + 1).toFloat()
                while (x < w) {
                    val bw = (14 + rng.nextInt(26)) * scale * (1 + i * 0.3f)
                    val top = h * (0.55f + rng.nextFloat() * 0.25f + i * 0.06f)
                    paint.color = color
                    c.drawRect(x, top, x + bw, h.toFloat(), paint)
                    for (k in 0 until (bw * (h - top) / (90f * scale * scale)).toInt()) {
                        if (rng.nextFloat() > 0.35f) continue
                        paint.color = windowColors[rng.nextInt(windowColors.size)] and 0x00FFFFFF or ((150 + i * 40) shl 24)
                        val wx = x + rng.nextFloat() * (bw - 3 * scale)
                        val wy = top + 3 * scale + rng.nextFloat() * (h - top - 6 * scale)
                        c.drawRect(wx, wy, wx + 2f * scale, wy + 2.5f * scale, paint)
                    }
                    x += bw * (0.75f + rng.nextFloat() * 0.3f)
                }
            }

            // Tower 61, banded; the bands light up magenta-to-cyan when the goal is met.
            val tl = w * 0.6f
            val tr = tl + 22f * scale
            val tt = h * 0.42f
            paint.color = 0xFF0D0222.toInt()
            c.drawRect(tl, tt, tr, h.toFloat(), paint)
            c.drawRect(tl + 7f * scale, tt - 6f * scale, tr - 7f * scale, tt, paint)
            var y = tt + 6f * scale
            while (y < h) {
                val u = ((y - tt) / (h - tt)).coerceIn(0f, 1f)
                paint.color = if (goalMet) blend(0xFF19F0FF.toInt(), 0xFFFF2D95.toInt(), u) else 0xFF1C0B36.toInt()
                c.drawRect(tl + 2f * scale, y, tr - 2f * scale, y + 1.2f * scale, paint)
                y += 4f * scale
            }
            if (goalMet) {
                paint.shader = RadialGradient((tl + tr) / 2, tt + 20f * scale, 60f * scale, 0x5519F0FF, 0x0019F0FF, Shader.TileMode.CLAMP)
                c.drawRect(tl - 60f * scale, tt - 40f * scale, tr + 60f * scale, h.toFloat(), paint)
                paint.shader = null
            }

            // Scanlines.
            paint.color = 0x2E000000
            var sy = 0f
            while (sy < h) {
                c.drawRect(0f, sy, w.toFloat(), sy + scale, paint)
                sy += 3f * scale
            }
            return bmp
        }

        private fun blend(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private const val THROTTLE_MS = 20_000L
        private const val MAX_BITMAP_WIDTH = 420f
        private const val KICKER_CYAN = 0xFF19F0FF.toInt()
        private const val STREAK_ORANGE = 0xFFFF8A1E.toInt()
    }
}
