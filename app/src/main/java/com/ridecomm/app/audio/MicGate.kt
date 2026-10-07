package com.ridecomm.app.audio

import android.media.AudioFormat
import io.livekit.android.audio.AudioBufferCallback
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the [NoiseGate] on the microphone before it's sent to the group. The gate is created on
 * the first buffer (that's when the sample rate is known) and rebuilt if the format changes.
 */
object MicGate {
    @Volatile private var sensitivity: NoiseGate.Sensitivity? = NoiseGate.Sensitivity.MEDIUM
    private var gate: NoiseGate? = null
    private var gateRate = 0
    private var samples = FloatArray(0)

    /** null switches the gate off (the mic goes out as WebRTC captured it). */
    fun setSensitivity(value: NoiseGate.Sensitivity?) {
        sensitivity = value
    }

    /** Hooks the gate onto the room's published microphone track; call after the mic is enabled. */
    fun attach(room: Room) {
        val track = room.localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track as? LocalAudioTrack
        track?.setAudioBufferCallback(callback)
    }

    internal val callback = object : AudioBufferCallback {
        override fun onBuffer(
            buffer: ByteBuffer,
            audioFormat: Int,
            channelCount: Int,
            sampleRate: Int,
            bytesRead: Int,
            captureTimeNs: Long,
        ): Long {
            runCatching { process(buffer, audioFormat, channelCount, sampleRate, bytesRead) }
            return captureTimeNs
        }
    }

    // Called on WebRTC's capture thread only.
    private fun process(buffer: ByteBuffer, audioFormat: Int, channelCount: Int, sampleRate: Int, bytesRead: Int) {
        val level = sensitivity ?: return
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        // Gating the mixed signal is fine for mono and stereo alike: every channel opens together.
        val count = bytesRead / bytesPerSample
        if (count <= 0 || channelCount <= 0) return
        val g = gate?.takeIf { gateRate == sampleRate * channelCount }
            ?: NoiseGate(sampleRate * channelCount).also { gate = it; gateRate = sampleRate * channelCount }
        g.sensitivity = level
        if (samples.size < count) samples = FloatArray(count)
        // WebRTC fills the buffer in the device's byte order but leaves it marked big-endian.
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        if (bytesPerSample == 2) {
            for (i in 0 until count) samples[i] = data.getShort(i * 2) / 32768f
            g.process(samples, count)
            for (i in 0 until count) data.putShort(i * 2, (samples[i] * 32767f).toInt().toShort())
        } else {
            for (i in 0 until count) samples[i] = data.getFloat(i * 4)
            g.process(samples, count)
            for (i in 0 until count) data.putFloat(i * 4, samples[i])
        }
    }
}
