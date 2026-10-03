package com.district9.neonsteps.data

import com.district9.neonsteps.util.Format
import java.time.LocalDate

/** The kinds of daily mission the newswire can hand out. */
enum class MissionKind {
    STEPS_BY_HOUR, // reach N steps before a given hour
    MORNING_STEPS, // N steps before 10:00
    BEAT_YESTERDAY, // more steps than yesterday
    BRISK_MINUTES, // N minutes walking at a good pace
    DISTANCE, // walk N metres
    GOAL_BY_HOUR, // reach the goal before a given hour
    STRETCH, // go well past the goal
}

enum class MissionState { ACTIVE, DONE, FAILED }

/**
 * One day's mission. [target] is in steps, minutes or metres depending on [kind];
 * [deadlineHour] is 24 for "by the end of the day". [reward] is a [Cosmetics] id.
 */
data class Mission(
    val date: LocalDate,
    val kind: MissionKind,
    val target: Int,
    val deadlineHour: Int,
    val reward: String,
) {
    /** The mission as the card and notification put it. */
    val title: String
        get() = when (kind) {
            MissionKind.STEPS_BY_HOUR -> "LLEGA A ${Format.steps(target)} PASOS ANTES DE LAS $deadlineHour:00"
            MissionKind.MORNING_STEPS -> "DA ${Format.steps(target)} PASOS ANTES DE LAS $deadlineHour:00"
            MissionKind.BEAT_YESTERDAY -> "SUPERA LOS ${Format.steps(target - 1)} PASOS DE AYER"
            MissionKind.BRISK_MINUTES -> "CAMINA $target MINUTOS A BUEN RITMO"
            MissionKind.DISTANCE -> "RECORRE ${Format.decimal(target / 1000.0)} KM"
            MissionKind.GOAL_BY_HOUR -> "LLEGA A TU META ANTES DE LAS $deadlineHour:00"
            MissionKind.STRETCH -> "SUPERA LOS ${Format.steps(target)} PASOS"
        }

    /** Progress as shown on the card, e.g. "3.250 / 7.411". */
    fun progressLabel(current: Int): String = when (kind) {
        MissionKind.BRISK_MINUTES -> "${current.coerceAtMost(target)} / $target MIN"
        MissionKind.DISTANCE -> "${Format.decimal(current / 1000.0)} / ${Format.decimal(target / 1000.0)} KM"
        else -> "${Format.steps(current)} / ${Format.steps(target)}"
    }
}

/** Unlockable styles for District 9, earned by completing daily missions. */
object Cosmetics {
    enum class Slot { RAIN, KOI, SKY }

    /** @param koi main, chroma and core colours for the hologram koi (KOI slot only). */
    class Item(val id: String, val slot: Slot, val name: String, val color: Int, val koi: IntArray? = null)

    val all = listOf(
        Item("rain_cyan", Slot.RAIN, "LLUVIA CIAN", 0xFF7FE8FF.toInt()),
        Item("koi_sakura", Slot.KOI, "KOI SAKURA", 0xFFFF9EC7.toInt(), intArrayOf(0xFFFF9EC7.toInt(), 0xFF7FE8FF.toInt(), 0xFFFFF0F6.toInt())),
        Item("sky_moon", Slot.SKY, "LUNA LLENA", 0xFFF2E9D0.toInt()),
        Item("rain_pink", Slot.RAIN, "LLUVIA ROSA", 0xFFFF8CC6.toInt()),
        Item("koi_emerald", Slot.KOI, "KOI ESMERALDA", 0xFF3DFFA8.toInt(), intArrayOf(0xFF3DFFA8.toInt(), 0xFFFF2D95.toInt(), 0xFFE8FFF4.toInt())),
        Item("sky_aurora", Slot.SKY, "AURORA SOBRE D9", 0xFF5CFFC8.toInt()),
        Item("rain_gold", Slot.RAIN, "LLUVIA DORADA", 0xFFFFD36B.toInt()),
        Item("koi_shadow", Slot.KOI, "KOI SOMBRA", 0xFFB28CFF.toInt(), intArrayOf(0xFFB28CFF.toInt(), 0xFF19F0FF.toInt(), 0xFFF1E8FF.toInt())),
    )

    /** Once everything is collected, a mission pays out in fireworks instead. */
    const val FIREWORKS = "fireworks"

    fun byId(id: String?): Item? = all.firstOrNull { it.id == id }

    fun rewardName(id: String): String = byId(id)?.name ?: "SALVA DE FUEGOS ARTIFICIALES"

    fun rewardColor(id: String): Int = byId(id)?.color ?: 0xFFFFE81A.toInt()
}
