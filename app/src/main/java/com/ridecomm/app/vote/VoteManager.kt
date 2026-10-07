package com.ridecomm.app.vote

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.overlay.Haptics
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class Ballot(val name: String, val yes: Boolean)

data class Vote(
    val id: String,
    val kind: VoteKind,
    val starterName: String,
    val startedAtMs: Long,
    /** Riders in the ride when the vote started. */
    val riders: Int,
    /** Voter identity → ballot. */
    val ballots: Map<String, Ballot>,
    /** Null while open; then whether the stop was approved. */
    val approved: Boolean? = null,
) {
    val yes get() = ballots.values.count { it.yes }
    val no get() = ballots.values.count { !it.yes }
}

data class VoteState(
    val active: Vote? = null,
    /** What I voted on the active vote, or null if I haven't. */
    val myVote: Boolean? = null,
    /** The last finished vote, shown for a short while. */
    val lastResult: Vote? = null,
    /** When the active vote opened on this phone (its timeout counts from here). */
    val activeSinceMs: Long = 0,
    /** When [lastResult] was decided (it's shown for [VoteManager.RESULT_SHOWN_MS]). */
    val resultSinceMs: Long = 0,
) {
    val needsMyVote: Boolean get() = active != null && myVote == null
}

/**
 * Group votes ("Break?", "Fuel?") and one-way quick messages ("Slow down"). Every phone keeps its
 * own tally from the broadcast ballots and announces the result in the headset, so nobody needs
 * to look at the screen.
 */
object VoteManager {
    private const val TOPIC = "rc-vote"
    const val VOTE_TIMEOUT_MS = 45_000L
    const val RESULT_SHOWN_MS = 6_000L
    const val SENT_SHOWN_MS = 3_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(VoteState())
    val state: StateFlow<VoteState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var timeoutJob: Job? = null
    private var clearResultJob: Job? = null

    /** Votes I started: I re-send them, while still open, to riders who come back online. */
    private val startedByMe = mutableSetOf<String>()

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

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        timeoutJob?.cancel()
        clearResultJob?.cancel()
        clearSentJob?.cancel()
        _state.value = VoteState()
        _sent.value = null
        startedByMe.clear()
    }

    /** Back online after a drop: ask for any vote that's still open. */
    fun requestCatchUp(sinceMs: Long) {
        send(JSONObject().put("t", "catchup").put("since", sinceMs))
    }

    // ---- Actions ----

    fun startVote(kind: VoteKind) {
        if (_state.value.active != null) return
        val me = myIdentity() ?: return
        val vote = Vote(
            id = UUID.randomUUID().toString(),
            kind = kind,
            starterName = myName(),
            startedAtMs = System.currentTimeMillis(),
            riders = RideManager.state.value.riders.size.coerceAtLeast(1),
            ballots = mapOf(me to Ballot(myName(), yes = true)),
        )
        open(vote, myVote = true)
        startedByMe += vote.id
        send(startMessage(vote))
        check()
    }

    private fun startMessage(vote: Vote) = JSONObject()
        .put("t", "start")
        .put("id", vote.id)
        .put("kind", vote.kind.name)
        .put("name", vote.starterName)
        .put("at", vote.startedAtMs)
        .put("riders", vote.riders)

    fun cast(yes: Boolean) {
        val s = _state.value
        val vote = s.active ?: return
        if (s.myVote != null) return
        val me = myIdentity() ?: return
        _state.update {
            it.copy(active = vote.copy(ballots = vote.ballots + (me to Ballot(myName(), yes))), myVote = yes)
        }
        send(JSONObject().put("t", "cast").put("id", vote.id).put("name", myName()).put("yes", yes))
        check()
    }

    /** A quick message I just sent, shown briefly as confirmation. */
    data class Sent(val message: QuickMessage, val atMs: Long)

    private val _sent = MutableStateFlow<Sent?>(null)
    val sent: StateFlow<Sent?> = _sent.asStateFlow()
    private var clearSentJob: Job? = null

    fun sendQuick(message: QuickMessage) {
        send(JSONObject().put("t", "say").put("msg", message.name).put("name", myName()))
        // Same confirmation whether sent from the app or the floating button.
        announce("Sent: ${message.label}")
        val sent = Sent(message, System.currentTimeMillis())
        _sent.value = sent
        clearSentJob?.cancel()
        clearSentJob = scope.launch {
            delay(SENT_SHOWN_MS)
            if (_sent.value == sent) _sent.value = null
        }
    }

    // ---- Incoming ----

    private fun onMessage(o: JSONObject, from: String) {
        when (o.getString("t")) {
            "start" -> {
                // A catch-up re-send also carries the ballots cast so far.
                val earlier = o.optJSONArray("ballots")?.let { list ->
                    (0 until list.length()).associate { i ->
                        val b = list.getJSONObject(i)
                        b.getString("id") to Ballot(b.getString("name"), b.getBoolean("yes"))
                    }
                }.orEmpty()
                val incoming = Vote(
                    id = o.getString("id"),
                    kind = VoteKind.valueOf(o.getString("kind")),
                    starterName = o.getString("name"),
                    startedAtMs = o.getLong("at"),
                    riders = o.getInt("riders"),
                    ballots = earlier + (from to Ballot(o.getString("name"), yes = true)),
                )
                if (_state.value.active?.id == incoming.id) return
                val current = _state.value.active
                // Two riders started at once: everyone keeps the earlier vote.
                val keepCurrent = current != null &&
                    (current.startedAtMs < incoming.startedAtMs ||
                        (current.startedAtMs == incoming.startedAtMs && current.id <= incoming.id))
                if (keepCurrent) return
                val me = myIdentity()
                open(incoming, myVote = incoming.ballots[me]?.yes)
                Haptics(appContext).confirm()
                val late = if (o.optBoolean("late")) "While you were offline, " else ""
                announce("$late${incoming.starterName} ${incoming.kind.asking}. Slide to vote.")
                check()
            }
            "catchup" -> replyCatchUp(from)
            "cast" -> {
                val vote = _state.value.active ?: return
                if (vote.id != o.getString("id")) return
                val ballot = Ballot(o.getString("name"), o.getBoolean("yes"))
                _state.update { it.copy(active = vote.copy(ballots = vote.ballots + (from to ballot))) }
                check()
            }
            "say" -> {
                // Old quick messages replayed after a drop (by an earlier app version) are stale: skip them.
                if (o.optBoolean("late")) return
                val message = QuickMessage.valueOf(o.getString("msg"))
                Haptics(appContext).confirm()
                announce("${o.getString("name")} says ${message.says}")
            }
            // "result" (a vote that finished while I was offline, from an earlier version) is ignored too.
        }
    }

    /**
     * [to] came back online: if I'm running a vote that's still open, send it with the ballots so
     * far so they can vote. Finished votes and old quick messages aren't replayed.
     */
    private fun replyCatchUp(to: String) {
        val active = _state.value.active ?: return
        if (active.id !in startedByMe) return
        val ballots = org.json.JSONArray()
        active.ballots.forEach { (id, b) -> ballots.put(JSONObject().put("id", id).put("name", b.name).put("yes", b.yes)) }
        sendTo(to, startMessage(active).put("ballots", ballots).put("late", true))
    }

    // ---- Tally ----

    private fun open(vote: Vote, myVote: Boolean?) {
        clearResultJob?.cancel()
        _state.value = VoteState(active = vote, myVote = myVote, activeSinceMs = System.currentTimeMillis())
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(VOTE_TIMEOUT_MS)
            check(timedOut = true)
        }
    }

    private fun check(timedOut: Boolean = false) {
        val vote = _state.value.active ?: return
        val approved = VoteTally.outcome(vote.yes, vote.no, vote.riders, timedOut) ?: return
        timeoutJob?.cancel()
        val done = vote.copy(approved = approved)
        _state.value = VoteState(lastResult = done, resultSinceMs = System.currentTimeMillis())
        announce(
            "${done.kind.stopName} ${if (approved) "approved" else "not approved"}. " +
                "${done.yes} yes, ${done.no} no.",
        )
        clearResultJob = scope.launch {
            delay(RESULT_SHOWN_MS)
            _state.update { if (it.lastResult?.id == done.id) it.copy(lastResult = null) else it }
        }
    }

    // ---- Helpers ----

    private fun send(o: JSONObject) {
        val r = room ?: return
        scope.launch {
            // Best effort: a rider who misses a ballot still gets the result when the vote times out.
            r.trySendText(o.toString(), TOPIC)
        }
    }

    private fun sendTo(identity: String, o: JSONObject) {
        val r = room ?: return
        scope.launch { r.trySendText(o.toString(), TOPIC, listOf(Participant.Identity(identity))) }
    }

    private fun announce(text: String) {
        if (::appContext.isInitialized) Announcer.speak(appContext, text)
    }

    private fun myIdentity(): String? = if (::appContext.isInitialized) Prefs.deviceId(appContext) else null

    private fun myName() = Prefs.riderName(appContext).ifBlank { "Rider" }
}
