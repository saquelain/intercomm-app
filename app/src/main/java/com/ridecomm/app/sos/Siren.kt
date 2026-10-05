package com.ridecomm.app.sos

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * A rising/falling emergency siren, generated in code. Plays on the call audio path so it
 * reaches the helmet headset.
 */
class Siren {
    private var track: AudioTrack? = null

    fun play(seconds: Int) {
        stop()
        val samples = buildCycle()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        t.write(samples, 0, samples.size)
        // Each cycle is one second; loop it for the requested time.
        t.setLoopPoints(0, samples.size, (seconds - 1).coerceAtLeast(0))
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    /** One second: pitch sweeps 650 → 1350 Hz and back. */
    private fun buildCycle(): ShortArray {
        val out = ShortArray(SAMPLE_RATE)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val sweep = if (t < 0.5) t * 2 else (1 - t) * 2
            val freq = 650 + 700 * sweep
            phase += 2 * PI * freq / SAMPLE_RATE
            out[i] = (sin(phase) * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
