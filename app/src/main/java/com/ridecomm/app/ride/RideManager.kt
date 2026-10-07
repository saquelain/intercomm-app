package com.ridecomm.app.ride

import android.content.Context
import com.ridecomm.app.Prefs
import com.ridecomm.app.crash.CrashDetector
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.profile.ProfileSync
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.vote.VoteManager
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.LocalAudioTrackOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the group voice call. Lives for the whole process so the ride keeps going while the
 * screen is off or another app (e.g. Maps) is in front; [RideService] keeps the process alive.
 */
object RideManager {

    /** Voice-only bitrate: clear speech while staying light on mobile data (~24 kbps). */
    private const val VOICE_BITRATE = 24_000
    private const val MAX_RETRY_DELAY_MS = 15_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var rideJob: Job? = null

    private class RideError(message: String) : Exception(message)

    fun start(context: Context, code: String) {
        if (rideJob != null) return
        appContext = context.applicationContext
        _state.value = RideState(status = RideStatus.CONNECTING, code = code)
        RideService.start(appContext)
        CrashDetector.start(appContext)
        rideJob = scope.launch { runRide(code) }
    }

    fun leave() {
        rideJob?.cancel()
        rideJob = null
        MusicManager.release()
        VoteManager.release()
        SosManager.release()
        GroupTracker.release()
        ProfileSync.release()
        CrashDetector.stop()
        _state.value = RideState()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun toggleMute() {
        val muted = !_state.value.micMuted
        _state.update { it.copy(micMuted = muted) }
        val r = room ?: return
        scope.launch {
            r.localParticipant.setMicrophoneEnabled(!muted)
            refreshRiders()
        }
    }

    /**
     * Keeps the rider in the ride until they leave. Highways have dead zones, so after the
     * first successful join any drop is retried forever with a capped backoff.
     */
    private suspend fun runRide(code: String) {
        var everConnected = false
        var retryDelay = 2_000L
        while (true) {
            val reason = try {
                connectOnce(code) { everConnected = true; retryDelay = 2_000L }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!everConnected) {
                    endWithError(e.message ?: "Could not start the ride")
                    return
                }
                null
            }
            if (reason == DisconnectReason.DUPLICATE_IDENTITY) {
                endWithError("You joined this ride from somewhere else")
                return
            }
            _state.update { it.copy(status = RideStatus.RECONNECTING) }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }

    /** Joins the ride once and suspends until the connection ends; returns why it ended. */
    private suspend fun connectOnce(code: String, onConnected: () -> Unit): DisconnectReason = coroutineScope {
        val details = try {
            RidePass.fetch(appContext, code)
        } catch (e: RidePassError) {
            throw RideError(e.message ?: "Could not get into the ride")
        }

        val r = LiveKit.create(
            appContext,
            RoomOptions(
                // WebRTC's built-in noise suppression, echo cancellation and auto gain.
                audioTrackCaptureDefaults = LocalAudioTrackOptions(),
                // DTX sends almost nothing while you're silent; RED adds redundancy for lossy networks.
                audioTrackPublishDefaults = AudioTrackPublishDefaults(audioBitrate = VOICE_BITRATE, dtx = true, red = true),
            ),
            LiveKitOverrides(
                audioOptions = AudioOptions(
                    audioHandler = AudioSwitchHandler(appContext).apply {
                        // A call normally takes full audio focus, which pauses Spotify and other
                        // music apps. Keep them playing; MusicManager turns them down for voices.
                        manageAudioFocus = !Prefs.keepOtherMusic(appContext)
                    },
                ),
            ),
        )
        room = r
        MusicManager.attach(appContext, r)
        VoteManager.attach(appContext, r)
        SosManager.attach(r)
        GroupTracker.attach(appContext, r)
        ProfileSync.attach(appContext, r)
        val ended = CompletableDeferred<DisconnectReason>()
        val events = launch(start = CoroutineStart.UNDISPATCHED) {
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.Reconnecting -> _state.update { it.copy(status = RideStatus.RECONNECTING) }
                    is RoomEvent.Reconnected -> _state.update { it.copy(status = RideStatus.CONNECTED) }
                    is RoomEvent.Disconnected -> ended.complete(event.reason)
                    is RoomEvent.ActiveSpeakersChanged -> MusicManager.onSpeakersChanged(event.speakers)
                    is RoomEvent.ParticipantDisconnected -> event.participant.identity?.let { GroupTracker.forget(it.value) }
                    is RoomEvent.ParticipantConnected -> event.participant.identity?.let { ProfileSync.onRiderJoined(it) }
                    else -> Unit
                }
                refreshRiders()
            }
        }
        try {
            r.connect(details.serverUrl, details.participantToken)
            r.localParticipant.setMicrophoneEnabled(!_state.value.micMuted)
            onConnected()
            MusicManager.onConnected()
            GroupTracker.onConnected()
            ProfileSync.onConnected()
            _state.update { it.copy(status = RideStatus.CONNECTED) }
            refreshRiders()
            ended.await()
        } finally {
            events.cancel()
            MusicManager.detach()
            VoteManager.detach()
            SosManager.detach()
            GroupTracker.detach()
            ProfileSync.detach()
            room = null
            r.disconnect()
            r.release()
        }
    }

    private fun endWithError(message: String) {
        rideJob = null
        MusicManager.release()
        VoteManager.release()
        SosManager.release()
        GroupTracker.release()
        ProfileSync.release()
        CrashDetector.stop()
        _state.value = RideState(error = message)
    }

    private fun refreshRiders() {
        val r = room ?: return
        val me = r.localParticipant.toRider(isMe = true, muted = _state.value.micMuted)
        val others = r.remoteParticipants.values
            .map { it.toRider(isMe = false, muted = !it.isMicrophoneEnabled) }
            .sortedBy { it.name.lowercase() }
        _state.update { it.copy(riders = listOf(me) + others) }
    }

    private fun Participant.toRider(isMe: Boolean, muted: Boolean) = Rider(
        id = identity?.value ?: sid.value,
        name = name?.takeIf { it.isNotBlank() } ?: "Rider",
        isMe = isMe,
        isSpeaking = isSpeaking && !muted,
        isMuted = muted,
        signal = when (connectionQuality) {
            ConnectionQuality.EXCELLENT, ConnectionQuality.GOOD -> Signal.GOOD
            ConnectionQuality.POOR -> Signal.WEAK
            ConnectionQuality.LOST -> Signal.LOST
            ConnectionQuality.UNKNOWN -> Signal.UNKNOWN
        },
    )
}
