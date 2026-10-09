package com.ridecomm.app.hazard

import android.content.Context
import android.location.Location
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.score.Score
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.trip.TripTracker
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A hazard as seen from where I am. */
data class HazardView(val hazard: Hazard, val distanceM: Double?, val ahead: Boolean?)

data class HazardsState(
    /** The Hazard alerts setting is on. */
    val enabled: Boolean = false,
    /** Nearest first. */
    val hazards: List<HazardView> = emptyList(),
)

/**
 * Road hazards: any rider marks a pothole, police check, accident… where they are (one tap or
 * "RideComm, pothole"), and the riders behind hear "Pothole in 300 meters" as they get close.
 * Only the hazard's spot is shared, not anyone's position, so it works without the Group map.
 */
object Hazards {
    private const val TOPIC = "rc-hazard"
    private const val TICK_MS = 30_000L
    /** Below this speed (m/s) the GPS heading is noise. */
    private const val MIN_SPEED_FOR_HEADING = 2f

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(HazardsState())
    val state: StateFlow<HazardsState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var jobs = emptyList<Job>()
    private val hazards = linkedMapOf<String, Hazard>()
    /** Hazards I've already been warned about. */
    private val warned = mutableSetOf<String>()
    private var me: Location? = null
    private var myHeading: Double? = null

    private fun enabled() = ::appContext.isInitialized && Prefs.hazardAlerts(appContext)
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
        if (jobs.isEmpty()) {
            jobs = listOf(
                scope.launch { TripTracker.location.collect { it?.let(::onMyLocation) } },
                scope.launch {
                    while (isActive) {
                        delay(TICK_MS)
                        publish()
                    }
                },
            )
        }
        publish()
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        hazards.clear()
        warned.clear()
        me = null
        myHeading = null
        _state.value = HazardsState()
    }

    /** The setting changed mid-ride. */
    fun applySettings() {
        if (!::appContext.isInitialized) return
        if (enabled()) requestSync()
        publish()
    }

    /** Back after a drop: ask for hazards marked meanwhile. */
    fun requestSync() {
        val r = room ?: return
        scope.launch { r.trySendText(JSONObject().put("t", "sync").toString(), TOPIC) }
    }

    /** A rider joined: they get the hazards I marked. */
    fun onRiderJoined(identity: Participant.Identity) = sendMine(listOf(identity))

    /** Marks a hazard where I am now. */
    fun mark(kind: HazardKind) {
        if (!enabled()) return
        val loc = me ?: LocationHelper.lastKnown(appContext)
        if (loc == null) {
            say("Allow location to mark hazards")
            return
        }
        val now = System.currentTimeMillis()
        val existing = HazardLogic.sameSpot(hazards.values, kind, loc.latitude, loc.longitude)
        val hazard = existing?.copy(atMs = now) ?: Hazard(UUID.randomUUID().toString(), kind, loc.latitude, loc.longitude, myId(), myName(), now)
        hazards[hazard.id] = hazard
        if (existing == null) Score.noteHazard()
        // I'm at it: no warning for me.
        warned += hazard.id
        send(hazard, emptyList())
        say("${kind.spoken} marked for the group")
        publish()
    }

    fun remove(id: String) {
        val hazard = hazards.remove(id) ?: return
        val r = room
        if (r != null) scope.launch { r.trySendText(JSONObject().put("t", "hazard-clear").put("id", hazard.id).toString(), TOPIC) }
        publish()
    }

    private fun send(h: Hazard, to: List<Participant.Identity>) {
        val r = room ?: return
        scope.launch { r.trySendText(toJson(h).toString(), TOPIC, to) }
    }

    private fun sendMine(to: List<Participant.Identity>) {
        if (!::appContext.isInitialized) return
        val r = room ?: return
        val mine = hazards.values.filter { it.byId == myId() }
        if (mine.isEmpty()) return
        val o = JSONObject().put("t", "hazards").put("list", JSONArray(mine.map { toJson(it) }))
        scope.launch { r.trySendText(o.toString(), TOPIC, to) }
    }

    private fun toJson(h: Hazard) = JSONObject()
        .put("t", "hazard")
        .put("id", h.id)
        .put("kind", h.kind.name)
        .put("lat", h.lat)
        .put("lon", h.lon)
        .put("by", h.byId)
        .put("name", h.byName)
        .put("at", h.atMs)

    private fun fromJson(o: JSONObject, from: String): Hazard? {
        val kind = HazardKind.entries.firstOrNull { it.name == o.optString("kind") } ?: return null
        return Hazard(o.getString("id"), kind, o.getDouble("lat"), o.getDouble("lon"), o.optString("by", from), o.optString("name", "A rider"), o.getLong("at"))
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (!enabled()) return
        when (o.optString("t")) {
            "hazard" -> fromJson(o, from)?.let { receive(it, announce = true) }
            "hazards" -> o.optJSONArray("list")?.let { list ->
                (0 until list.length()).forEach { i -> fromJson(list.getJSONObject(i), from)?.let { receive(it, announce = false) } }
            }
            "hazard-clear" -> if (hazards.remove(o.getString("id")) != null) publish()
            "sync" -> sendMine(listOf(Participant.Identity(from)))
        }
    }

    private fun receive(h: Hazard, announce: Boolean) {
        if (h.expired(System.currentTimeMillis())) return
        val known = hazards[h.id]
        hazards[h.id] = h
        publish()
        if (known != null || !announce) return
        // Heads-up now if it's on the road ahead; the distance warning comes later as I get close.
        val loc = me
        if (loc == null) {
            say("${h.byName} marked: ${h.kind.spoken}")
            return
        }
        val d = GroupMath.distanceM(loc.latitude, loc.longitude, h.lat, h.lon)
        val ahead = HazardLogic.isAhead(loc.latitude, loc.longitude, myHeading, h.lat, h.lon, withinDeg = 60.0)
        when {
            d > HazardLogic.ANNOUNCE_WITHIN_M -> Unit
            ahead == true -> say("${h.byName} marked: ${h.kind.spoken}, ${GroupMath.spokenDistance(d)} ahead")
            ahead == null -> say("${h.byName} marked: ${h.kind.spoken}")
        }
    }

    private fun onMyLocation(location: Location) {
        val previous = me
        myHeading = when {
            location.hasBearing() && location.speed >= MIN_SPEED_FOR_HEADING -> location.bearing.toDouble()
            previous != null && GroupMath.distanceM(previous.latitude, previous.longitude, location.latitude, location.longitude) > 30 ->
                GroupMath.bearingDeg(previous.latitude, previous.longitude, location.latitude, location.longitude)
            else -> myHeading
        }
        me = location
        if (!enabled()) return
        hazards.values.forEach { h ->
            if (h.id in warned) return@forEach
            val d = GroupMath.distanceM(location.latitude, location.longitude, h.lat, h.lon)
            if (HazardLogic.shouldWarn(d, HazardLogic.isAhead(location.latitude, location.longitude, myHeading, h.lat, h.lon))) {
                warned += h.id
                say(HazardLogic.warning(h.kind, d))
            }
        }
        publish()
    }

    private fun publish() {
        if (!::appContext.isInitialized) return
        val now = System.currentTimeMillis()
        hazards.values.removeAll { it.expired(now) }
        val loc = me
        val views = hazards.values.map { h ->
            if (loc == null) {
                HazardView(h, null, null)
            } else {
                HazardView(
                    h,
                    GroupMath.distanceM(loc.latitude, loc.longitude, h.lat, h.lon),
                    HazardLogic.isAhead(loc.latitude, loc.longitude, myHeading, h.lat, h.lon, withinDeg = 60.0),
                )
            }
        }.sortedBy { it.distanceM ?: Double.MAX_VALUE }
        _state.value = HazardsState(enabled = enabled(), hazards = if (enabled()) views else emptyList())
    }

    private fun say(text: String) = Announcer.speak(appContext, text)
}
