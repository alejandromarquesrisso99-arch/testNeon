package com.district9.neonsteps.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.district9.neonsteps.ui.scene.SceneAudio

/**
 * Streams [AmbientSynth] to an [AudioTrack] on its own thread while the app is on screen.
 * Triggers from the scene are ignored while stopped.
 */
class AmbientSound : SceneAudio {
    private val synth = AmbientSynth(RATE)
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "d9-ambient").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    override fun ambience(rain: Float, gridPower: Float) {
        synth.rain = rain
        synth.gridPower = gridPower
    }

    override fun thunder(strength: Float) = whenRunning { synth.thunder(strength) }
    override fun sparks(intensity: Float) = whenRunning { synth.sparks(intensity) }
    override fun firework(size: Float) = whenRunning { synth.firework(size) }
    override fun poof(big: Boolean) = whenRunning { synth.poof(big) }
    override fun powerDown() = whenRunning { synth.powerDown() }
    override fun chime() = whenRunning { synth.chime() }
    override fun ding() = whenRunning { synth.ding() }
    override fun flyby() = whenRunning { synth.flyby() }

    private inline fun whenRunning(block: () -> Unit) {
        if (running) block()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, CHUNK * 2) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "No audio track", e)
            running = false
            return
        }
        val mix = FloatArray(CHUNK)
        val pcm = ShortArray(CHUNK)
        var gain = 0f
        try {
            track.play()
            // Fade in on start; the loop exits after fading out once stop() is called.
            while (running || gain > 0f) {
                synth.render(mix, CHUNK)
                val target = if (running) 1f else 0f
                for (i in 0 until CHUNK) {
                    gain += (target - gain).coerceIn(-FADE_STEP * 4, FADE_STEP)
                    pcm[i] = (mix[i] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
                if (track.write(pcm, 0, CHUNK) < 0) break
                if (!running && gain <= 0.001f) break
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Audio stream failed", e)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val TAG = "AmbientSound"
        const val RATE = 22_050
        const val CHUNK = 1024
        const val FADE_STEP = 1f / (RATE * 1.2f) // ~1.2 s fade in
    }
}
