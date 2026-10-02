package com.district9.neonsteps.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Spanish-style number formatting (6.482 · 4,86 km) independent of the device locale quirks. */
object Format {
    private val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val integer = DecimalFormat("#,##0", symbols)
    private val twoDecimals = DecimalFormat("0.00", symbols)
    private val oneDecimal = DecimalFormat("0.0", symbols)

    fun steps(value: Int): String = integer.format(value)

    fun km(steps: Int, strideMeters: Double): String {
        val km = steps * strideMeters / 1000.0
        return (if (km < 10) twoDecimals else oneDecimal).format(km) + " KM"
    }

    fun kcal(steps: Int, kcalPerStep: Double): String = integer.format(Math.round(steps * kcalPerStep)) + " KCAL"

    fun meters(value: Double): String = twoDecimals.format(value) + " M"

    fun decimal(value: Double): String = oneDecimal.format(value)

    /** Calories burned, counted in bowls of ramen. */
    fun ramenBowls(steps: Int, kcalPerStep: Double): String {
        val bowls = steps * kcalPerStep / KCAL_PER_RAMEN
        val text = oneDecimal.format(bowls)
        return text + if (text == "1,0") " CUENCO" else " CUENCOS"
    }

    /** A generous bowl of tonkotsu ramen. */
    private const val KCAL_PER_RAMEN = 550.0

    fun percent(steps: Int, goal: Int): Int = if (goal <= 0) 0 else (steps * 100L / goal).toInt()
}
