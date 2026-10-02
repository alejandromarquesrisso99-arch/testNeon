package com.district9.neonsteps.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import com.district9.neonsteps.R

/** The District 9 palette: ink black to deep violet, lit by hot magenta, cyan and acid yellow. */
object Neon {
    const val INK = 0xFF05010F.toInt()
    const val VIOLET_DEEP = 0xFF1A0433.toInt()
    const val MAGENTA = 0xFFFF2D95.toInt()
    const val CYAN = 0xFF19F0FF.toInt()
    const val YELLOW = 0xFFFFE81A.toInt()
    const val VIOLET = 0xFFA36BFF.toInt()
    const val TEAL = 0xFF3FB8C9.toInt()

    const val TEXT = 0xFFF4EEFF.toInt()
    const val TEXT_MUTED = 0xFFA99BC4.toInt()
    const val TEXT_DIM = 0xFF6F6390.toInt()
    const val PANEL = 0x940A0418.toInt()

    fun alpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((Color.alpha(color) * a.coerceIn(0f, 1f)).toInt() shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
        )
    }
}

/** Bundled typefaces so the look doesn't depend on the OEM's system fonts. */
class NeonFonts private constructor(context: Context) {
    val mono: Typeface = load(context, R.font.share_tech_mono, Typeface.MONOSPACE)
    val black: Typeface = load(context, R.font.roboto_black, Typeface.DEFAULT_BOLD)
    val bold: Typeface = load(context, R.font.roboto_bold, Typeface.DEFAULT_BOLD)

    private fun load(context: Context, id: Int, fallback: Typeface): Typeface =
        runCatching { context.resources.getFont(id) }.getOrDefault(fallback)

    companion object {
        @Volatile
        private var instance: NeonFonts? = null

        fun get(context: Context): NeonFonts =
            instance ?: synchronized(this) {
                instance ?: NeonFonts(context.applicationContext).also { instance = it }
            }
    }
}
