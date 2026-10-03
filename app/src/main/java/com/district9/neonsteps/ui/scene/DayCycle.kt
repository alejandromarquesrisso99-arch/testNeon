package com.district9.neonsteps.ui.scene

import com.district9.neonsteps.ui.Neon

/**
 * District 9 through the day, following the phone's clock: sky colours, how many windows are
 * lit, how hard the neon reads against the sky and how busy the skyways are. Keyframes are
 * interpolated, so the city drifts from one look to the next minute by minute.
 */
internal object DayCycle {

    /** @param sky four gradient stops, top to horizon. */
    class Look(
        val sky: IntArray,
        val occupancy: Float,
        val neon: Float,
        val traffic: Float,
        val daylight: Float,
    )

    private class Key(val hour: Float, val look: Look)

    private val night = intArrayOf(0xFF07010F.toInt(), 0xFF140428.toInt(), 0xFF2A0A47.toInt(), 0xFF46104F.toInt())

    private val keys = listOf(
        Key(0f, Look(night, occupancy = 0.55f, neon = 1f, traffic = 0.35f, daylight = 0f)),
        Key(2.5f, Look(night, occupancy = 0.2f, neon = 1f, traffic = 0.12f, daylight = 0f)),
        Key(5f, Look(rgb(0xFF0A0620, 0xFF1A1240, 0xFF2E1A55, 0xFF4A2460), occupancy = 0.22f, neon = 1f, traffic = 0.25f, daylight = 0.05f)),
        Key(6.5f, Look(rgb(0xFF1B0F3E, 0xFF43205E, 0xFFA0467A, 0xFFFF8A5C), occupancy = 0.4f, neon = 0.85f, traffic = 0.7f, daylight = 0.45f)),
        Key(8f, Look(rgb(0xFF2A2552, 0xFF4B4673, 0xFF7D6E92, 0xFFC9A7A7), occupancy = 0.35f, neon = 0.6f, traffic = 1f, daylight = 0.9f)),
        Key(12f, Look(rgb(0xFF2E3360, 0xFF4D5583, 0xFF7F7B9E, 0xFFB4A6BD), occupancy = 0.3f, neon = 0.55f, traffic = 0.7f, daylight = 1f)),
        Key(14f, Look(rgb(0xFF2E3360, 0xFF4D5583, 0xFF7F7B9E, 0xFFB4A6BD), occupancy = 0.3f, neon = 0.55f, traffic = 0.85f, daylight = 1f)),
        Key(17f, Look(rgb(0xFF2B2858, 0xFF4A4378, 0xFF85698F, 0xFFD29A92), occupancy = 0.42f, neon = 0.65f, traffic = 0.8f, daylight = 0.85f)),
        Key(19f, Look(rgb(0xFF170A38, 0xFF3E1554, 0xFFB23F6B, 0xFFFF6E48), occupancy = 0.75f, neon = 0.9f, traffic = 1f, daylight = 0.4f)),
        Key(20.5f, Look(night, occupancy = 0.95f, neon = 1f, traffic = 0.8f, daylight = 0f)),
        Key(23f, Look(night, occupancy = 0.8f, neon = 1f, traffic = 0.55f, daylight = 0f)),
        Key(24f, Look(night, occupancy = 0.55f, neon = 1f, traffic = 0.35f, daylight = 0f)),
    )

    private fun rgb(a: Long, b: Long, c: Long, d: Long) = intArrayOf(a.toInt(), b.toInt(), c.toInt(), d.toInt())

    /** The look at [hour] (0–24, fractional). */
    fun at(hour: Float): Look {
        val h = ((hour % 24f) + 24f) % 24f
        val i = keys.indexOfLast { it.hour <= h }.coerceIn(0, keys.size - 2)
        val a = keys[i]
        val b = keys[i + 1]
        val u = ((h - a.hour) / (b.hour - a.hour)).coerceIn(0f, 1f)
        val t = u * u * (3f - 2f * u)
        return Look(
            IntArray(4) { Neon.mix(a.look.sky[it], b.look.sky[it], t) },
            lerp(a.look.occupancy, b.look.occupancy, t),
            lerp(a.look.neon, b.look.neon, t),
            lerp(a.look.traffic, b.look.traffic, t),
            lerp(a.look.daylight, b.look.daylight, t),
        )
    }
}
