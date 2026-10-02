package com.district9.neonsteps.ui.scene

import kotlin.math.floor
import kotlin.math.sin

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Cheap smooth 1D value noise in [0, 1], used for neon hum and smog drift. */
internal fun noise1(x: Float, seed: Int = 0): Float {
    val i = floor(x)
    val f = x - i
    val a = hash(i.toInt(), seed)
    val b = hash(i.toInt() + 1, seed)
    val u = f * f * (3f - 2f * f)
    return a + (b - a) * u
}

internal fun hash(n: Int, seed: Int = 0): Float {
    var h = n * 374761393 + seed * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}

internal fun wave(t: Float, period: Float, phase: Float = 0f): Float =
    sin((t / period + phase) * 2f * Math.PI.toFloat())

/** Tiny deterministic PRNG so the city is the same every launch. */
internal class Rng(seed: Long) {
    private var state = seed xor 0x5DEECE66DL

    fun next(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }

    fun range(a: Float, b: Float) = a + (b - a) * next()

    fun int(a: Int, bExclusive: Int) = a + (next() * (bExclusive - a)).toInt().coerceAtMost(bExclusive - a - 1)

    fun chance(p: Float) = next() < p

    fun <T> pick(items: List<T>): T = items[int(0, items.size)]
}

/**
 * Maps coordinates of the 1080 × 1936 reference frame (horizon at y = 1360) onto the
 * actual view: the street-level composition keeps its proportions and taller screens
 * simply get more sky.
 */
internal class SceneFrame(val width: Int, val height: Int, val horizon: Float = height * 0.705f) {
    val k: Float = minOf(width / REF_WIDTH, horizon / REF_HORIZON)
    private val x0: Float = (width - REF_WIDTH * k) / 2f

    fun x(refX: Float) = x0 + refX * k
    fun y(refY: Float) = horizon - (REF_HORIZON - refY) * k
    fun s(refSize: Float) = refSize * k

    companion object {
        const val REF_WIDTH = 1080f
        const val REF_HORIZON = 1360f
    }
}
