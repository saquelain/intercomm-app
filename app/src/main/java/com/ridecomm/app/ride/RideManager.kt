package com.ridecomm.app.ride

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.alerts.RiderAlerts
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.trip.TripTracker
import com.ridecomm.app.voice.VoiceCommands
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
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.Track
import livekit.org.webrtc.RtpSender
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the group voice call. Lives for the whole process so the ride keeps going while the
 * screen is off or another app (e.g. Maps) is in front; [RideService] keeps the process alive.
 */
object RideManager {

    private const val MAX_RETRY_DELAY_MS = 15_000L
    private const val NETWORK_CHECK_MS = 2_000L
    /** Let the phone hand audio back to the ride before speaking. */
    private const val CALL_END_SETTLE_MS = 1_500L
    private const val CATCH_UP_MARGIN_MS = 5_000L
    /** Only say "Back online" after a real drop, not a blip. */
    private const val BACK_ONLINE_ANNOUNCE_MS = 5_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(RideState())
    val state: StateFlow<RideState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var rideJob: Job? = null
    /** The ride just left, kept connected for a moment to say bye. */
    private var leavingRide: Job? = null
    /** When the connection last dropped (wall clock), until we're back and have caught up. */
    private var offlineSinceMs: Long? = null
    private var callWatch: Job? = null
    /** I muted the mic because a phone call started, so unmute when it ends. */
    private var mutedForCall = false

    private class RideError(message: String) : Exception(message)

    fun start(context: Context, code: String) {
        if (rideJob != null) return
        leavingRide?.cancel()
        appContext = context.applicationContext
        _state.value = RideState(status = RideStatus.CONNECTING, code = code)
        DataUsage.start()
        DataSaver.reset(appContext)
        offlineSinceMs = null
        Prefs.rideStarted(appContext, code)
        MicGate.resetTotals()
        RiderAlerts.start(appContext)
        TripTracker.start(appContext)
        RideService.start(appContext)
        CrashDetector.start(appContext)
        watchPhoneCalls()
        rideJob = scope.launch { runRide(code) }
    }

    /**
     * A regular phone call doesn't end the ride: my ride mic goes off for the call (so the group
     * doesn't hear it), the group is told, and the mic comes back afterwards.
     */
    private fun watchPhoneCalls() {
        mutedForCall = false
        PhoneCalls.start(appContext)
        callWatch?.cancel()
        callWatch = scope.launch {
            PhoneCalls.inCall.drop(1).collect { inCall ->
                if (inCall) {
                    if (!_state.value.micMuted) {
                        toggleMute()
                        mutedForCall = true
                    }
                } else {
                    if (mutedForCall && _state.value.micMuted) toggleMute()
                    mutedForCall = false
                    delay(CALL_END_SETTLE_MS)
                    Announcer.speak(appContext, "Back on the ride")
                }
                RiderAlerts.setMyPhoneCall(inCall)
            }
        }
    }

    private fun stopWatchingPhoneCalls() {
        callWatch?.cancel()
        callWatch = null
        PhoneCalls.stop()
        mutedForCall = false
    }

    fun leave() {
        if (::appContext.isInitialized) Prefs.clearUnfinishedRide(appContext)
        val job = rideJob
        val r = room
        rideJob = null
        // Say bye before disconnecting, so the others hear "left the ride" rather than "dropped out".
        leavingRide = scope.launch {
            try {
                if (r != null) RiderAlerts.sayBye(r)
            } finally {
                job?.cancel()
            }
        }
        MusicManager.release()
        VoteManager.release()
        SosManager.release()
        GroupTracker.release()
        ProfileSync.release()
        RiderAlerts.release()
        VoiceCommands.stop()
        TripTracker.stop()
        stopWatchingPhoneCalls()
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
            // Joining muted publishes the mic only now, so hook the gate onto it here too.
            if (!muted) {
                MicGate.attach(r)
                applyVoiceQuality(r, DataSaver.active.value)
            }
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
                audioTrackPublishDefaults = AudioTrackPublishDefaults(
                    audioBitrate = if (DataSaver.active.value) DataSaver.SAVING_VOICE_BPS else DataSaver.NORMAL_VOICE_BPS,
                    dtx = true,
                    red = true,
                ),
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
        RiderAlerts.attach(r)
        val ended = CompletableDeferred<DisconnectReason>()
        val events = launch(start = CoroutineStart.UNDISPATCHED) {
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.Reconnecting -> {
                        if (offlineSinceMs == null) offlineSinceMs = System.currentTimeMillis()
                        _state.update { it.copy(status = RideStatus.RECONNECTING) }
                        checkNetwork(r)
                    }
                    is RoomEvent.Reconnected -> {
                        _state.update { it.copy(status = RideStatus.CONNECTED) }
                        catchUp()
                    }
                    is RoomEvent.ConnectionQualityChanged -> if (event.participant === r.localParticipant) checkNetwork(r)
                    is RoomEvent.Disconnected -> ended.complete(event.reason)
                    is RoomEvent.ActiveSpeakersChanged -> MusicManager.onSpeakersChanged(event.speakers)
                    is RoomEvent.ParticipantDisconnected -> {
                        event.participant.identity?.let { GroupTracker.forget(it.value) }
                        RiderAlerts.onRiderLeft(event.participant)
                    }
                    is RoomEvent.ParticipantConnected -> {
                        event.participant.identity?.let {
                            ProfileSync.onRiderJoined(it)
                            GroupTracker.onRiderJoined(it)
                        }
                        RiderAlerts.onRiderJoined(event.participant)
                    }
                    else -> Unit
                }
                refreshRiders()
            }
        }
        try {
            r.connect(details.serverUrl, details.participantToken)
            r.localParticipant.setMicrophoneEnabled(!_state.value.micMuted)
            MicGate.setSettings(Prefs.windGate(appContext))
            MicGate.attach(r)
            VoiceCommands.start(appContext)
            onConnected()
            MusicManager.onConnected()
            GroupTracker.onConnected()
            ProfileSync.onConnected()
            RiderAlerts.onConnected()
            _state.update { it.copy(status = RideStatus.CONNECTED) }
            refreshRiders()
            catchUp()
            // Data saving reacts to how the connection has been over the last seconds.
            val networkWatch = launch {
                while (true) {
                    checkNetwork(r)
                    delay(NETWORK_CHECK_MS)
                }
            }
            val quality = launch { DataSaver.active.collect { applyVoiceQuality(r, it) } }
            try {
                ended.await()
            } finally {
                networkWatch.cancel()
                quality.cancel()
            }
        } finally {
            if (offlineSinceMs == null) offlineSinceMs = System.currentTimeMillis()
            events.cancel()
            MusicManager.detach()
            VoteManager.detach()
            SosManager.detach()
            GroupTracker.detach()
            ProfileSync.detach()
            RiderAlerts.detach()
            if (room === r) room = null
            r.disconnect()
            r.release()
        }
    }

    /** Back online after a drop: get what the others said meanwhile, and send anything that waited. */
    private fun catchUp() {
        val since = offlineSinceMs ?: return
        offlineSinceMs = null
        // A little earlier than the drop, in case the phones' clocks differ slightly.
        val from = since - CATCH_UP_MARGIN_MS
        VoteManager.requestCatchUp(from)
        SosManager.onBackOnline(from)
        GroupTracker.requestSync()
        if (System.currentTimeMillis() - since > BACK_ONLINE_ANNOUNCE_MS) Announcer.speak(appContext, "Back online")
    }

    /**
     * Sets the voice bitrate on the live call without re-publishing the mic (no gap in my voice).
     * LiveKit keeps the WebRTC sender internal, so it's reached by its compiled name; if that ever
     * fails, the call just stays at its current quality.
     */
    private fun applyVoiceQuality(r: Room, saving: Boolean) {
        val track = r.localParticipant.getTrackPublication(Track.Source.MICROPHONE)?.track as? LocalAudioTrack ?: return
        val bps = if (saving) DataSaver.SAVING_VOICE_BPS else DataSaver.NORMAL_VOICE_BPS
        runCatching {
            val sender = LocalAudioTrack::class.java.getMethod("getSender\$livekit_android_sdk_release").invoke(track) as RtpSender
            val params = sender.parameters
            params.encodings.forEach { it.maxBitrateBps = bps }
            sender.setParameters(params)
        }.onFailure { Log.w("RideComm", "Couldn't change the voice bitrate", it) }
    }

    private fun checkNetwork(r: Room) {
        val weak = _state.value.status != RideStatus.CONNECTED ||
            r.localParticipant.connectionQuality == ConnectionQuality.POOR ||
            r.localParticipant.connectionQuality == ConnectionQuality.LOST
        DataSaver.onConnection(SystemClock.elapsedRealtime(), weak)
    }

    private fun endWithError(message: String) {
        Prefs.clearUnfinishedRide(appContext)
        rideJob = null
        MusicManager.release()
        VoteManager.release()
        SosManager.release()
        GroupTracker.release()
        ProfileSync.release()
        RiderAlerts.release()
        VoiceCommands.stop()
        TripTracker.stop()
        stopWatchingPhoneCalls()
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
