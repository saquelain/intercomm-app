package com.ridecomm.app.whisper

import android.content.Context
import com.ridecomm.app.Prefs
import com.ridecomm.app.overlay.Haptics
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RiderVolumes
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

data class WhisperState(
    /** The rider I'm talking only to (while I hold their name or the button). */
    val talkingTo: String? = null,
    val talkingToName: String = "",
    /** My voice is going out to them (after a short warm-up while the others silence me). */
    val live: Boolean = false,
    /** Someone talking only to me now, or a moment ago (so I can hold to reply). */
    val fromMe: WhisperView? = null,
    val fromMeNow: Boolean = false,
    /** Sender id → who they're talking to, for "Talking to Bilal" on rider cards. */
    val others: Map<String, String> = emptyMap(),
)

/**
 * Talk to one rider: hold a rider to speak only to them (lead to sweep, say), without filling
 * everyone's ears. Everybody gets a small "Asha is talking to Bilal" message; every phone except
 * Bilal's turns Asha's voice down to silence until she lets go. Bilal sees "Asha is talking only to
 * you" and can hold to reply. It is private between RideComm phones, not encrypted secret.
 */
object Whisper {
    private const val TOPIC = "rc-whisper"
    private const val CHECK_MS = 250L
    /** "Hold to reply" stays this long after a private talk to me ends. */
    private const val REPLY_WINDOW_MS = 8_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(WhisperState())
    val state: StateFlow<WhisperState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private val board = WhisperBoard()
    private var repeatJob: Job? = null
    private var checkJob: Job? = null
    private var lastToMeMs = 0L

    private fun myId() = Prefs.deviceId(appContext)
    private fun myName() = Prefs.riderName(appContext).ifBlank { "Rider" }

    fun attach(context: Context, r: Room) {
        appContext = context.applicationContext
        room = r
        r.registerTextStreamHandler(TOPIC) { reader, from ->
            scope.launch {
                val text = runCatching { reader.readAll().joinToString("") }.getOrNull() ?: return@launch
                runCatching { onMessage(JSONObject(text), from.value) }
            }
        }
    }

    /** Connection dropped: stop talking privately (the others will stop silencing me by themselves). */
    fun detach() {
        stop()
        room = null
    }

    fun release() {
        stop()
        room = null
        board.clear()
        checkJob?.cancel()
        checkJob = null
        lastToMeMs = 0
        RiderVolumes.setHushed(emptySet(), null)
        _state.value = WhisperState()
    }

    /** Start talking only to [riderId] (until [stop]). */
    fun start(riderId: String, riderName: String) {
        if (!::appContext.isInitialized || room == null || !Prefs.talkToOne(appContext)) return
        if (riderId == myId()) return
        if (_state.value.talkingTo == riderId) return
        stop()
        _state.value = _state.value.copy(talkingTo = riderId, talkingToName = riderName, live = false)
        RideManager.setWhisperMic(RideManager.WhisperMic.WARMING)
        Haptics(appContext).confirm()
        repeatJob = scope.launch {
            send(riderId, riderName, on = true)
            delay(WhisperBoard.WARM_UP_MS)
            if (_state.value.talkingTo != riderId) return@launch
            _state.value = _state.value.copy(live = true)
            RideManager.setWhisperMic(RideManager.WhisperMic.LIVE)
            while (isActive) {
                delay(WhisperBoard.REPEAT_MS)
                send(riderId, riderName, on = true)
            }
        }
    }

    /** Let go: my voice goes back to everyone. */
    fun stop() {
        val to = _state.value.talkingTo ?: return
        val name = _state.value.talkingToName
        repeatJob?.cancel()
        repeatJob = null
        _state.value = _state.value.copy(talkingTo = null, talkingToName = "", live = false)
        RideManager.setWhisperMic(RideManager.WhisperMic.NONE)
        scope.launch { send(to, name, on = false) }
    }

    /** A rider left: forget their private talk, and stop mine if it was to them. */
    fun forget(riderId: String) {
        board.forget(riderId)
        if (_state.value.talkingTo == riderId) stop()
        refresh()
    }

    private suspend fun send(to: String, toName: String, on: Boolean) {
        val r = room ?: return
        val o = JSONObject().put("t", "whisper").put("to", to).put("toName", toName).put("name", myName())
            .put("on", on).put("at", System.currentTimeMillis())
        r.trySendText(o.toString(), TOPIC)
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (o.optString("t") != "whisper") return
        val to = o.getString("to")
        val on = o.optBoolean("on", true)
        val now = System.currentTimeMillis()
        val wasToMe = board.toMe(myId(), now)
        board.onMessage(from, o.optString("name", "A rider"), to, o.optString("toName", ""), on, now)
        if (to == myId() && on && wasToMe?.fromId != from) Haptics(appContext).confirm()
        refresh()
        if (checkJob == null) {
            checkJob = scope.launch {
                while (isActive) {
                    delay(CHECK_MS)
                    refresh()
                    if (!board.busy(System.currentTimeMillis()) && System.currentTimeMillis() - lastToMeMs > REPLY_WINDOW_MS) {
                        refresh()
                        checkJob = null
                        return@launch
                    }
                }
            }
        }
    }

    /** Silences whoever is talking privately to someone else, and updates what's shown. */
    private fun refresh() {
        if (!::appContext.isInitialized) return
        val now = System.currentTimeMillis()
        val me = myId()
        RiderVolumes.setHushed(board.hushed(me, now), room)
        val toMe = board.toMe(me, now)
        if (toMe != null) lastToMeMs = now
        val recent = toMe ?: _state.value.fromMe?.takeIf { now - lastToMeMs <= REPLY_WINDOW_MS }
        _state.value = _state.value.copy(
            fromMe = recent,
            fromMeNow = toMe != null,
            others = board.active(now).filter { it.toId != me }.associate { it.fromId to it.toName },
        )
    }
}
