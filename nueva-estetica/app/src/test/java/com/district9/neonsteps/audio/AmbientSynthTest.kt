package com.district9.neonsteps.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

class AmbientSynthTest {
    private val rate = 22_050

    private fun render(synth: AmbientSynth, seconds: Float): FloatArray {
        val total = (seconds * rate).toInt()
        val out = FloatArray(total)
        val chunk = FloatArray(1024)
        var i = 0
        while (i < total) {
            val n = minOf(1024, total - i)
            synth.render(chunk, n)
            System.arraycopy(chunk, 0, out, i, n)
            i += n
        }
        return out
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from))
    }

    @Test
    fun staysFiniteAndUnclipped() {
        val synth = AmbientSynth(rate).apply { rain = 1f }
        synth.thunder(1f); synth.sparks(1f); synth.firework(1.5f); synth.poof(true)
        synth.powerDown(); synth.chime(); synth.ding()
        val x = render(synth, 5f)
        assertTrue(x.all { it.isFinite() && abs(it) <= 1.4f })
        assertTrue(x.count { abs(it) > 0.99f } < x.size / 1000)
    }

    @Test
    fun heavierRainIsLouder() {
        val light = rms(render(AmbientSynth(rate).apply { rain = 0.1f; gridPower = 0f }, 2f))
        val heavy = rms(render(AmbientSynth(rate).apply { rain = 1f; gridPower = 0f }, 2f))
        assertTrue("light=$light heavy=$heavy", heavy > light * 2)
    }

    @Test
    fun thunderRollsInAfterTheFlash() {
        val synth = AmbientSynth(rate).apply { rain = 0f; gridPower = 0f }
        synth.thunder(0.5f) // weaker strikes are farther away: longer delay
        val x = render(synth, 3f)
        val before = rms(x, 0, rate / 4)
        val after = rms(x, rate, rate * 2)
        assertTrue("before=$before after=$after", after > before * 3)
    }

    /** Power at one frequency (Goertzel), to isolate the 100 Hz mains hum from the rain. */
    private fun tone(x: FloatArray, hz: Double, from: Int, to: Int): Double {
        val k = 2 * Math.cos(2 * Math.PI * hz / rate)
        var s1 = 0.0
        var s2 = 0.0
        for (i in from until to) {
            val s0 = x[i] + k * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - k * s1 * s2
    }

    @Test
    fun blackoutSilencesTheHum() {
        val lit = render(AmbientSynth(rate).apply { rain = 0f; gridPower = 1f }, 3f)
        val dark = render(AmbientSynth(rate).apply { rain = 0f; gridPower = 0f }, 3f)
        val litHum = tone(lit, 100.0, rate * 2, rate * 3)
        val darkHum = tone(dark, 100.0, rate * 2, rate * 3)
        assertTrue("lit=$litHum dark=$darkHum", litHum > darkHum * 50)
    }

    /** A listenable demo of everything, for a human ear: build/sounds/demo.wav. */
    @Test
    fun writeDemo() {
        val synth = AmbientSynth(rate).apply { rain = 0.7f }
        val parts = ArrayList<FloatArray>()
        parts += render(synth, 2f)
        synth.thunder(1f); parts += render(synth, 4f)
        synth.sparks(0.5f); parts += render(synth, 0.6f)
        synth.sparks(1f); parts += render(synth, 1.4f)
        synth.firework(1.2f); synth.firework(0.9f); parts += render(synth, 2f)
        synth.poof(true); parts += render(synth, 0.4f)
        repeat(5) { synth.poof(false); parts += render(synth, 0.12f) }
        parts += render(synth, 1f)
        synth.chime(); parts += render(synth, 1.8f)
        synth.ding(); parts += render(synth, 2f)
        synth.powerDown(); synth.gridPower = 0f; parts += render(synth, 3f)
        val all = parts.flatMap { it.asList() }
        val dir = File("build/sounds").apply { mkdirs() }
        RandomAccessFile(File(dir, "demo.wav"), "rw").use { f ->
            f.setLength(0)
            val dataBytes = all.size * 2
            fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            f.write("RIFF".toByteArray()); f.write(le32(36 + dataBytes)); f.write("WAVEfmt ".toByteArray())
            f.write(le32(16)); f.write(le16(1)); f.write(le16(1)); f.write(le32(rate)); f.write(le32(rate * 2)); f.write(le16(2)); f.write(le16(16))
            f.write("data".toByteArray()); f.write(le32(dataBytes))
            val bytes = ByteArray(dataBytes)
            all.forEachIndexed { i, v ->
                val s = (v * 32767).toInt().coerceIn(-32767, 32767)
                bytes[i * 2] = s.toByte(); bytes[i * 2 + 1] = (s shr 8).toByte()
            }
            f.write(bytes)
        }
    }
}
