package com.district9.neonsteps.util

import java.time.LocalTime

/** Local time of day for the city and the missions; tests pin it so both are reproducible. */
object CityClock {
    @Volatile
    internal var fixed: LocalTime? = null

    fun now(): LocalTime = fixed ?: LocalTime.now()
}
