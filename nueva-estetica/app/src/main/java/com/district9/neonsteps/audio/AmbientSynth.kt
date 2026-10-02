package com.district9.neonsteps.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural soundscape for District 9, rendered sample by sample: a bed of rain and neon
 * hum plus one-shot voices (thunder, sparks, fireworks, poofs, bells). Pure Kotlin so it can
 * be rendered offline in tests; [AmbientSound] streams it to an AudioTrack.
 *
 * Parameter setters and triggers may be called from any thread.
 */
class AmbientSynth(private val rate: Int = 22_050) {

    @Volatile var rain = 0.6f
    @Volatile var gridPower = 1f

    private val pending = ConcurrentLinkedQueue<Voice>()
    private val voices = ArrayList<Voice>()
    private val noise = Noise(1)

    // Rain bed filters and state.
    private var lpHi = 0f
    private var lpLo = 0f
    private var gust = 1f
    private var gustTarget = 1f
    private var tickEnv = 0f
    private var tickPrev = 0f
    private var humPhase = 0.0
    private var humLevel = 1f
    private var buzz = 0f

    fun thunder(strength: Float) = add(Thunder(strength.coerceIn(0.2f, 1f), delaySeconds = 0.25f + (1f - strength) * 0.9f))
    fun sparks(intensity: Float) = add(Sparks(intensity.coerceIn(0.2f, 1f)))
    fun firework(size: Float) = add(Pop(size.coerceIn(0.5f, 1.5f), delaySeconds = 0.35f + noise.next01() * 0.5f))
    fun poof(big: Boolean) = add(Poof(if (big) 1f else 0.55f))
    fun powerDown() = add(PowerDown())
    fun chime() = add(Chime())
    fun ding() = add(Ding())

    private fun add(v: Voice) {
        // Never let a burst of events pile up voices without bound.
        if (pending.size < 32) pending.add(v)
    }

    /** Mixes [n] samples into [out] (overwriting it), in roughly -1..1. */
    fun render(out: FloatArray, n: Int) {
        while (true) {
            val v = pending.poll() ?: break
            if (voices.size < 24) voices.add(v)
        }
        val r = rain.coerceIn(0f, 1f)
        val power = gridPower.coerceIn(0f, 1f)
        val bed = 0.03f + 0.12f * r
        val tickRate = (20f + 140f * r) / rate
        val omega = 2.0 * PI * HUM_HZ / rate

        for (i in 0 until n) {
            // Rain: band-limited hiss with slow gusts, plus the patter of individual drops.
            val w = noise.next()
            lpHi += 0.72f * (w - lpHi)
            lpLo += 0.1f * (w - lpLo)
            if (i % 512 == 0) gustTarget = 0.8f + 0.4f * noise.next01()
            gust += (gustTarget - gust) * 0.0004f
            var s = (lpHi - lpLo) * bed * gust
            if (noise.next01() < tickRate) tickEnv += 0.05f + 0.2f * noise.next01()
            tickEnv *= 0.985f
            val tick = w * tickEnv
            s += (tick - tickPrev) * 0.5f
            tickPrev = tick

            // Mains hum of the signs, with a little buzz; it follows the grid in a blackout.
            humLevel += (power - humLevel) * 0.002f
            humPhase += omega
            if (humPhase > 2 * PI) humPhase -= 2 * PI
            if (i % 256 == 0) buzz = if (noise.next01() < 0.08f) 0.6f + 0.4f * noise.next01() else buzz * 0.5f
            val ph = humPhase.toFloat()
            s += humLevel * (0.016f * sin(ph) + 0.009f * sin(2 * ph) + 0.006f * sin(3 * ph) * (1f + buzz))

            out[i] = s
        }

        val it = voices.iterator()
        while (it.hasNext()) {
            if (!it.next().render(out, n)) it.remove()
        }
        for (i in 0 until n) {
            val x = out[i] * MASTER
            out[i] = x / (1f + abs(x)) * 1.4f // soft clip
        }
    }

    // --- Voices ---------------------------------------------------------------------------

    private abstract inner class Voice(delaySeconds: Float = 0f) {
        private var delay = (delaySeconds * rate).toInt()
        protected var t = 0 // samples since start

        /** Adds this voice into [out]; returns false once finished. */
        fun render(out: FloatArray, n: Int): Boolean {
            var start = 0
            if (delay > 0) {
                if (delay >= n) {
                    delay -= n
                    return true
                }
                start = delay
                delay = 0
            }
            for (i in start until n) {
                out[i] += sample(t / rate.toFloat())
                t++
            }
            return t < length * rate
        }

        abstract val length: Float
        abstract fun sample(sec: Float): Float
    }

    /** A crack, then a rolling low rumble from integrated noise. */
    private inner class Thunder(private val strength: Float, delaySeconds: Float) : Voice(delaySeconds) {
        override val length = 2.6f + 1.8f * strength
        private val n = Noise((strength * 1000).toInt() + 7)
        private var brown = 0f
        private var lp = 0f
        private var crackLp = 0f
        private var roll = 1f
        private var rollTarget = 1f

        override fun sample(sec: Float): Float {
            val w = n.next()
            brown = brown * 0.996f + w * 0.03f
            lp += 0.05f * (brown - lp)
            crackLp += 0.35f * (w - crackLp)
            if (t % 2048 == 0) rollTarget = 0.55f + 0.6f * n.next01()
            roll += (rollTarget - roll) * 0.0008f
            val attack = (sec / 0.12f).coerceAtMost(1f)
            val rumble = attack * exp(-sec * 1.1f) * roll
            val crack = if (sec < 0.2f) crackLp * exp(-sec * 22f) * 0.6f else 0f
            return (lp * 9f * rumble + crack) * 0.55f * strength
        }
    }

    /** A shorting tube: random clicks over a 120 Hz buzz. */
    private inner class Sparks(private val intensity: Float) : Voice() {
        override val length = 0.25f + 0.55f * intensity
        private val n = Noise(311)
        private var click = 0f
        private var prev = 0f

        override fun sample(sec: Float): Float {
            if (n.next01() < 0.008f * intensity) click = 0.6f + 0.4f * n.next01()
            click *= 0.9f
            val w = n.next() * click
            val hp = w - prev
            prev = w
            val buzz = if (sin(2f * PI.toFloat() * 120f * sec) > 0f) 0.05f else -0.05f
            val env = 1f - sec / length
            return (hp + buzz * intensity) * env
        }
    }

    /** A distant firework: a low thump and a puff of noise. */
    private inner class Pop(private val size: Float, delaySeconds: Float) : Voice(delaySeconds) {
        override val length = 0.6f
        private val n = Noise(97)
        private var lp = 0f
        private var phase = 0f

        override fun sample(sec: Float): Float {
            val f = 50f + 70f * exp(-sec * 18f)
            phase += 2f * PI.toFloat() * f / rate
            val thump = sin(phase) * exp(-sec * 9f)
            lp += 0.15f * (n.next() - lp)
            val puff = lp * exp(-sec * 7f)
            return (thump * 0.35f + puff * 0.8f) * size
        }
    }

    /** Shadow-clone poof: a quick band-passed whoosh. */
    private inner class Poof(private val level: Float) : Voice() {
        override val length = 0.35f
        private val n = Noise(55)
        private var a = 0f
        private var b = 0f

        override fun sample(sec: Float): Float {
            val w = n.next()
            a += (0.5f - sec) * (w - a)
            b += 0.1f * (w - b)
            val env = (sec / 0.02f).coerceAtMost(1f) * exp(-sec * 10f)
            return (a - b) * env * 0.9f * level
        }
    }

    /** The grid dying: a thump and a descending motor whine. */
    private inner class PowerDown : Voice() {
        override val length = 1.1f
        private var phase = 0f

        override fun sample(sec: Float): Float {
            val f = 110f * exp(-sec * 1.8f) + 22f
            phase += 2f * PI.toFloat() * f / rate
            val whine = (sin(phase) + 0.4f * sin(phase * 2f)) * exp(-sec * 2.2f) * 0.14f
            val thump = sin(2f * PI.toFloat() * 55f * sec) * exp(-sec * 14f) * 0.35f
            return whine + thump
        }
    }

    /** Golden koi: a rising arpeggio of little bells. */
    private inner class Chime : Voice() {
        override val length = 1.6f
        override fun sample(sec: Float): Float {
            var s = 0f
            for ((k, f) in CHIME_NOTES.withIndex()) {
                val on = sec - k * 0.09f
                if (on < 0f) continue
                val env = exp(-on * 2.6f)
                s += (sin(2f * PI.toFloat() * f * on) + 0.25f * sin(2f * PI.toFloat() * f * 2.76f * on)) * env
            }
            return s * 0.12f
        }
    }

    /** The ramen counter bell: ding, and a softer ding. */
    private inner class Ding : Voice() {
        override val length = 1.8f
        override fun sample(sec: Float): Float {
            fun bell(on: Float, level: Float) = if (on < 0f) 0f else
                (sin(2f * PI.toFloat() * 1760f * on) + 0.5f * sin(2f * PI.toFloat() * 2637f * on)) * exp(-on * 2.2f) * level
            return (bell(sec, 1f) + bell(sec - 0.2f, 0.55f)) * 0.15f
        }
    }

    /** xorshift noise, white in -1..1. */
    private class Noise(seed: Int) {
        private var s = seed * 1103515245 + 12345 or 1
        fun next(): Float {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            return (s and 0xFFFFFF) / 8388608f - 1f
        }
        fun next01(): Float = (next() + 1f) * 0.5f
    }

    private companion object {
        const val HUM_HZ = 100.0 // 50 Hz mains, hum at twice the line frequency
        const val MASTER = 0.9f
        val CHIME_NOTES = floatArrayOf(1046.5f, 1318.5f, 1568f, 2093f)
    }
}
