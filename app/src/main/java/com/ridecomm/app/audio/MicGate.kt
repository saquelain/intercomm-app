package com.ridecomm.app.audio

import android.media.AudioFormat
import io.livekit.android.audio.AudioBufferCallback
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the [NoiseGate] on the microphone before it's sent to the group. The gate is created on
 * the first buffer (that's when the sample rate is known) and rebuilt if the format changes.
 */
object MicGate {
    @Volatile private var settings: GateSettings? = GateSettings.MEDIUM
    private var gate: NoiseGate? = null
    private var gateRate = 0
    private var samples = FloatArray(0)
    private var meter: GateMeter? = null
    private var meterRate = 0
    @Volatile private var resetMeter = false

    private val _live = MutableStateFlow<GateFrame?>(null)
    /** The filter's latest reading (every 50 ms while the mic runs), for the meters on screen. */
    val live: StateFlow<GateFrame?> = _live.asStateFlow()
    private val _totals = MutableStateFlow(GateTotals())
    /** Voice sent and noise blocked since the ride started. */
    val totals: StateFlow<GateTotals> = _totals.asStateFlow()

    /**
     * Also gets each mono mic chunk (as it was captured, before the gate), on the audio thread.
     * Used for voice commands; must return quickly.
     */
    @Volatile var tap: ((samples: FloatArray, count: Int, sampleRate: Int) -> Unit)? = null

    /** null switches the gate off (the mic goes out as WebRTC captured it). */
    fun setSettings(value: GateSettings?) {
        settings = value
    }

    /** New ride: start the totals from zero. */
    fun resetTotals() {
        resetMeter = true
        _live.value = null
        _totals.value = GateTotals()
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

    private fun publish(frame: GateFrame, m: GateMeter) {
        _live.value = frame
        _totals.value = m.totals
    }

    // Called on WebRTC's capture thread only.
    private fun process(buffer: ByteBuffer, audioFormat: Int, channelCount: Int, sampleRate: Int, bytesRead: Int) {
        val level = settings
        val listener = tap
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        // Gating the mixed signal is fine for mono and stereo alike: every channel opens together.
        val count = bytesRead / bytesPerSample
        if (count <= 0 || channelCount <= 0) return
        if (samples.size < count) samples = FloatArray(count)
        // WebRTC fills the buffer in the device's byte order but leaves it marked big-endian.
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        if (bytesPerSample == 2) {
            for (i in 0 until count) samples[i] = data.getShort(i * 2) / 32768f
        } else {
            for (i in 0 until count) samples[i] = data.getFloat(i * 4)
        }
        if (listener != null && channelCount == 1) runCatching { listener(samples, count, sampleRate) }
        val rate = sampleRate * channelCount
        if (resetMeter || meterRate != rate) {
            resetMeter = false
            meter = GateMeter(rate)
            meterRate = rate
        }
        val m = meter!!
        m.beforeGate(samples, count)
        if (level == null) {
            m.afterGate(samples, count, null)?.let { publish(it, m) }
            return
        }
        val g = gate?.takeIf { gateRate == rate } ?: NoiseGate(rate).also { gate = it; gateRate = rate }
        g.settings = level
        g.process(samples, count)
        m.afterGate(samples, count, g)?.let { publish(it, m) }
        if (bytesPerSample == 2) {
            for (i in 0 until count) data.putShort(i * 2, (samples[i] * 32767f).toInt().toShort())
        } else {
            for (i in 0 until count) data.putFloat(i * 4, samples[i])
        }
    }
}
