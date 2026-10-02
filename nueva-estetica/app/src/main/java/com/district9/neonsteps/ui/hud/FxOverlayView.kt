package com.district9.neonsteps.ui.hud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** CRT scanlines and a vignette over the whole frame. Never takes touches. */
class FxOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val scan = Paint()
    private val vignette = Paint()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val period = (3f * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        val pattern = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0)
            setPixel(0, 0, 0x2E000000)
        }
        scan.shader = BitmapShader(pattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) {
            vignette.shader = RadialGradient(
                w / 2f, h * 0.45f, maxOf(w, h) * 0.75f,
                intArrayOf(0x00000000, 0x00000000, 0x8C02000A.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scan)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)
    }
}
