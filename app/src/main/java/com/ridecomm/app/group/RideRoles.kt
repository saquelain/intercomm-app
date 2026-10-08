package com.ridecomm.app.group

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Lead & sweep: any rider can mark who leads and who rides last. Everyone sees the badges. With
 * the Group map on, the lead hears when someone gets ahead of them, the sweep hears when someone
 * drops behind, and that rider hears it too.
 */
object RideRoles {
    private const val TOPIC = "rc-role"
    /** Below this speed (km/h) a heading is guesswork, so no alerts. */
    private const val MOVING_KMH = 10f

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(RideRolesState())
    val state: StateFlow<RideRolesState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var watchJob: Job? = null
    private val watch = RoleWatch()

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
        if (watchJob == null) watchJob = scope.launch { GroupTracker.state.collect { check(it) } }
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        watchJob?.cancel()
        watchJob = null
        watch.clear()
        _state.value = RideRolesState()
    }

    /** Back after a drop, or just joined: ask who leads. */
    fun requestSync() {
        val r = room ?: return
        scope.launch { r.trySendText(JSONObject().put("t", "sync").toString(), TOPIC) }
    }

    fun onRiderJoined(identity: Participant.Identity) {
        if (shouldShare()) send(listOf(identity))
    }

    fun forget(identity: String) = watch.forget(identity)

    /** Makes [riderId] the lead (null clears it). A rider can't be lead and sweep at once. */
    fun setLead(riderId: String?, riderName: String) {
        val s = _state.value
        update(s.copy(leadId = riderId, sweepId = s.sweepId.takeUnless { it == riderId }), riderId, riderName, "lead")
    }

    fun setSweep(riderId: String?, riderName: String) {
        val s = _state.value
        update(s.copy(sweepId = riderId, leadId = s.leadId.takeUnless { it == riderId }), riderId, riderName, "sweep")
    }

    private fun update(next: RideRolesState, riderId: String?, riderName: String, role: String) {
        if (!::appContext.isInitialized) return
        _state.value = next.copy(atMs = System.currentTimeMillis(), byId = myId())
        watch.clear()
        send(emptyList())
        Announcer.speak(
            appContext,
            when {
                riderId == null -> "No $role now"
                riderId == myId() -> "You're the $role"
                else -> "$riderName is the $role"
            },
        )
    }

    /** Whoever set the roles answers for them; if they've left, the first remaining rider by id does. */
    private fun shouldShare(): Boolean {
        val s = _state.value
        val r = room ?: return false
        if (s.atMs == 0L) return false
        if (s.byId == myId()) return true
        val present = r.remoteParticipants.keys.map { it.value }
        if (s.byId in present) return false
        return (present + myId()).minOrNull() == myId()
    }

    private fun send(to: List<Participant.Identity>) {
        val r = room ?: return
        val s = _state.value
        val o = JSONObject().put("t", "roles").put("at", s.atMs).put("by", s.byId).put("name", myName())
        s.leadId?.let { o.put("lead", it) }
        s.sweepId?.let { o.put("sweep", it) }
        scope.launch { r.trySendText(o.toString(), TOPIC, to) }
    }

    private fun onMessage(o: JSONObject, from: String) {
        when (o.optString("t")) {
            "roles" -> {
                val incoming = RideRolesState(
                    leadId = o.optString("lead").ifBlank { null },
                    sweepId = o.optString("sweep").ifBlank { null },
                    atMs = o.getLong("at"),
                    byId = o.optString("by", from),
                )
                val current = _state.value
                if (!RoleLogic.newer(current, incoming)) return
                _state.value = incoming
                watch.clear()
                // Tell me when my own role changed; others' changes just show as badges.
                val me = myId()
                val by = o.optString("name", "A rider")
                when {
                    incoming.leadId == me && current.leadId != me -> say("$by made you the lead")
                    incoming.sweepId == me && current.sweepId != me -> say("$by made you the sweep")
                }
            }
            "sync" -> if (shouldShare()) send(listOf(Participant.Identity(from)))
        }
    }

    // ---- Alerts ----

    private fun check(group: GroupState) {
        if (!::appContext.isInitialized || !group.enabled || !Prefs.roleAlerts(appContext)) return
        val roles = _state.value
        val me = myId()
        val mine = group.me ?: return
        // Everyone's position and direction, me included.
        data class P(val lat: Double, val lon: Double, val heading: Double?, val name: String)
        val all = group.positions.mapValues { (_, p) ->
            P(p.lat, p.lon, p.headingDeg?.toDouble()?.takeIf { (p.speedKmh ?: 0f) >= MOVING_KMH }, p.name)
        } + (me to P(mine.lat, mine.lon, mine.headingDeg, myName()))
        val lead = roles.leadId?.let { all[it] }
        val sweep = roles.sweepId?.let { all[it] }
        if (lead == null && sweep == null) return
        all.forEach { (id, p) ->
            if (id == roles.leadId || id == roles.sweepId) return@forEach
            val fromLead = if (lead?.heading != null) RoleLogic.alongM(lead.lat, lead.lon, lead.heading, p.lat, p.lon) else null
            val fromSweep = if (sweep?.heading != null) RoleLogic.alongM(sweep.lat, sweep.lon, sweep.heading, p.lat, p.lon) else null
            watch.check(id, fromLead, fromSweep).forEach { alert ->
                // Only the riders involved hear it.
                when (alert) {
                    RoleWatch.Alert.AHEAD_OF_LEAD -> when (me) {
                        id -> say("You're ahead of the lead, ${lead!!.name}")
                        roles.leadId -> say("${p.name} is ahead of you")
                    }
                    RoleWatch.Alert.BEHIND_SWEEP -> when (me) {
                        id -> say("You're behind the sweep, ${sweep!!.name}")
                        roles.sweepId -> say("${p.name} dropped behind you")
                    }
                }
            }
        }
    }

    private fun say(text: String) = Announcer.speak(appContext, text)
}
