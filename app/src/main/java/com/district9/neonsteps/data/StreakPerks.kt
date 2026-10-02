package com.district9.neonsteps.data

/** What District 9 gains for each run of days that meet the goal. Kept while the streak lasts. */
object StreakPerks {
    class Perk(val days: Int, val headline: String)

    val all = listOf(
        Perk(2, "UN LETRERO NUEVO EN LA TORRE OESTE: «ファイト» (¡ÁNIMO!)"),
        Perk(3, "ABRE UN PUESTO DE DANGO EN EL MERCADO"),
        Perk(5, "FAROLILLOS DE PAPEL FLOTAN SOBRE EL MERCADO"),
        Perk(7, "UN SEGUNDO KOI SE UNE AL DE LA TORRE 61"),
        Perk(14, "UN DIRIGIBLE CRUZA EL CIELO CON TU RACHA"),
        Perk(30, "EL TÉCNICO ARREGLA LA «L» DEL HOTEL... PARA SIEMPRE"),
    )

    const val FIGHT_SIGN = 2
    const val DANGO_STALL = 3
    const val LANTERNS = 5
    const val SECOND_KOI = 7
    const val AIRSHIP = 14
    const val HOTEL_FOREVER = 30

    /** The perk earned on exactly the [days]-th day, if any. */
    fun unlockedAt(days: Int): Perk? = all.firstOrNull { it.days == days }

    fun next(days: Int): Perk? = all.firstOrNull { it.days > days }
}
