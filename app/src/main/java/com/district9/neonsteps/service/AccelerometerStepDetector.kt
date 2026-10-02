package com.district9.neonsteps.service

import kotlin.math.sqrt

/**
 * Fallback step detection for devices without a hardware step sensor: tracks gravity with a
 * low-pass filter and counts a step on each upward swing of the remaining acceleration,
 * with hysteresis and a minimum spacing that rules out shakes faster than running pace.
 */
class AccelerometerStepDetector {
    private var gravity = SENSOR_GRAVITY
    private var smoothed = 0f
    private var armed = true
    private var lastStepNs = 0L

    /** Returns true when this sample completes a step. */
    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        gravity += (magnitude - gravity) * GRAVITY_ALPHA
        smoothed += (magnitude - gravity - smoothed) * SMOOTH_ALPHA
        if (armed && smoothed > HIGH_THRESHOLD) {
            armed = false
            if (timestampNs - lastStepNs > MIN_STEP_INTERVAL_NS) {
                lastStepNs = timestampNs
                return true
            }
        } else if (!armed && smoothed < LOW_THRESHOLD) {
            armed = true
        }
        return false
    }

    private companion object {
        const val SENSOR_GRAVITY = 9.81f
        const val GRAVITY_ALPHA = 0.02f
        const val SMOOTH_ALPHA = 0.35f
        const val HIGH_THRESHOLD = 1.25f
        const val LOW_THRESHOLD = 0.25f
        const val MIN_STEP_INTERVAL_NS = 280_000_000L
    }
}
