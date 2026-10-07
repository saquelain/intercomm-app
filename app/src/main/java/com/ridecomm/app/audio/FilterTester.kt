package com.ridecomm.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records a short clip from the mic (outside a ride) so the rider can see and hear what the wind
 * filter does: live meters while recording, then the clip played back as recorded or as filtered.
 */
class FilterTester {
    sealed interface State {
        data object Idle : State
        data class Recording(val progress: Float) : State
        class Recorded(val clip: FloatArray) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _live = MutableStateFlow<GateFrame?>(null)
    val live: StateFlow<GateFrame?> = _live.asStateFlow()

    @Volatile private var stopRequested = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var trackFrames = 0

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Records [RECORD_MS], running the live meter with whatever [sensitivity] returns at the time. */
    @SuppressLint("MissingPermission")
    fun record(context: Context, sensitivity: () -> NoiseGate.Sensitivity?) {
        if (thread != null || !hasPermission(context)) return
        stopPlayback()
        stopRequested = false
        _state.value = State.Recording(0f)
        thread = Thread({
            val total = RATE * RECORD_MS / 1000
            val clip = FloatArray(total)
            val chunk = RATE / 100
            val pcm = ShortArray(chunk)
            val work = FloatArray(chunk)
            val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = try {
                // The same mic processing as calls (echo cancelling, noise suppression), so it sounds like a ride.
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, chunk * 2 * 4),
                )
            } catch (e: Exception) {
                null
            }
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                _state.value = State.Failed("Couldn't open the microphone")
                thread = null
                return@Thread
            }
            val gate = NoiseGate(RATE)
            val meter = GateMeter(RATE)
            var at = 0
            try {
                recorder.startRecording()
                while (at < total && !stopRequested) {
                    val n = recorder.read(pcm, 0, minOf(chunk, total - at))
                    if (n <= 0) break
                    for (i in 0 until n) {
                        work[i] = pcm[i] / 32768f
                        clip[at + i] = work[i]
                    }
                    at += n
                    val level = sensitivity()
                    meter.beforeGate(work, n)
                    if (level != null) {
                        gate.sensitivity = level
                        gate.process(work, n)
                    }
                    meter.afterGate(work, n, if (level != null) gate else null)?.let { _live.value = it }
                    _state.value = State.Recording(at.toFloat() / total)
                }
            } catch (e: Exception) {
                Log.w("RideComm", "Filter test recording failed", e)
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
            _live.value = null
            _state.value = if (at > RATE / 2) State.Recorded(clip.copyOf(at)) else State.Failed("Recording was too short")
            thread = null
        }, "rc-filter-test").apply { start() }
    }

    /** Plays [samples] (a clip at [RATE]) through the phone's media output. */
    fun play(samples: FloatArray) {
        stopPlayback()
        val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (e: Exception) {
            Log.w("RideComm", "Couldn't play the test clip", e)
            return
        }
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
        trackFrames = pcm.size
    }

    /** How far playback has got (0..1), or null when nothing is playing. */
    fun playhead(): Float? {
        val t = track ?: return null
        if (t.playState != AudioTrack.PLAYSTATE_PLAYING || trackFrames == 0) return null
        val f = t.playbackHeadPosition.toFloat() / trackFrames
        return if (f >= 1f) null else f
    }

    fun stopPlayback() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    fun release() {
        stopRequested = true
        stopPlayback()
    }

    companion object {
        const val RATE = 48_000
        const val RECORD_MS = 8_000
    }
}
