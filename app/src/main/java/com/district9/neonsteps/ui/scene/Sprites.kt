package com.district9.neonsteps.ui.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.SparseArray

/** Shared soft sprites, tinted at draw time, for glows, steam, washes of light and reflections. */
internal class Sprites {
    /** White radial falloff; tinted with [tint] it becomes any coloured glow. */
    val blob: Bitmap = Bitmap.createBitmap(BLOB, BLOB, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp)
        val r = BLOB / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(Color.WHITE, Color.argb(150, 255, 255, 255), Color.argb(40, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.25f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, p)
    }

    private val filters = SparseArray<ColorFilter>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    fun tint(color: Int): ColorFilter {
        val rgb = color or 0xFF000000.toInt()
        return filters.get(rgb) ?: PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_IN).also { filters.put(rgb, it) }
    }

    fun drawBlob(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
        if (alpha <= 0.004f) return
        paint.colorFilter = tint(color)
        paint.alpha = (alpha.coerceAtMost(1f) * 255).toInt()
        dst.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawBitmap(blob, null, dst, paint)
    }

    companion object {
        private const val BLOB = 96
    }
}

/**
 * In-place separable box blur, three passes ≈ gaussian. Used once per layout on small,
 * opaque bitmaps (so unpremultiplied averaging is exact), hence a plain implementation.
 */
internal fun boxBlur(bitmap: Bitmap, radiusX: Int, radiusY: Int, passes: Int = 3) {
    val w = bitmap.width
    val h = bitmap.height
    var a = IntArray(w * h)
    var b = IntArray(w * h)
    bitmap.getPixels(a, 0, w, 0, 0, w, h)
    repeat(passes) {
        if (radiusX > 0) {
            blurPass(a, b, w, h, radiusX, horizontal = true)
            a = b.also { b = a }
        }
        if (radiusY > 0) {
            blurPass(a, b, w, h, radiusY, horizontal = false)
            a = b.also { b = a }
        }
    }
    bitmap.setPixels(a, 0, w, 0, 0, w, h)
}

private fun blurPass(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val step = if (horizontal) 1 else w
    val div = 2 * r + 1
    for (line in 0 until lines) {
        val base = if (horizontal) line * w else line
        var sa = 0
        var sr = 0
        var sg = 0
        var sb = 0
        for (i in -r..r) {
            val p = input[base + i.coerceIn(0, len - 1) * step]
            sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
        }
        for (i in 0 until len) {
            output[base + i * step] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val pOut = input[base + (i - r).coerceIn(0, len - 1) * step]
            val pIn = input[base + (i + r + 1).coerceIn(0, len - 1) * step]
            sa += (pIn ushr 24) - (pOut ushr 24)
            sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
            sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
            sb += (pIn and 0xFF) - (pOut and 0xFF)
        }
    }
}
