package com.ridecomm.app.group

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.DataSaver
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.privateAudience
import com.ridecomm.app.ride.riderIds
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.sos.LocationHelper
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

/** Another rider's last shared position, as seen from my phone. */
data class RiderPosition(
    val lat: Double,
    val lon: Double,
    val atMs: Long,
    /** Null until my own location is known. */
    val distanceM: Double?,
    val relation: Relation,
    val name: String = "",
    val speedKmh: Float? = null,
    val headingDeg: Float? = null,
)

/** My own position for the map. */
data class MyFix(val lat: Double, val lon: Double, val headingDeg: Double?, val accuracyM: Float)

data class GroupState(
    /** Rider identity → position. */
    val positions: Map<String, RiderPosition> = emptyMap(),
    /** True while my phone is sharing its location with the group. */
    val sharing: Boolean = false,
    /** The Group map setting is on. */
    val enabled: Boolean = false,
    val me: MyFix? = null,
    val regroup: RegroupPoint? = null,
    /** Distance and direction from me to [regroup]. */
    val regroupDistanceM: Double? = null,
    val regroupRelation: Relation? = null,
)

/**
 * Keeps the group together. With the Group map setting on, every phone shares its GPS position
 * with the ride (every 5 s while moving, 15 s otherwise), shows where everyone is, announces when
 * someone gets separated and when they're back, and handles a shared regroup point: set it, see
 * who's arrived, hear when everyone's there. With the setting off it's completely silent.
 */
object GroupTracker {
    private const val TOPIC = "rc-loc"
    private const val SEND_MOVING_MS = 5_000L
    private const val SEND_SLOW_MS = 15_000L
    /** At least this fast counts as moving (km/h). */
    private const val MOVING_KMH = 15f
    private const val GPS_MIN_TIME_MS = 2_000L
    private const val GPS_MIN_DISTANCE_M = 5f
    /** Below this speed the GPS heading is noise. */
    private const val MIN_SPEED_FOR_HEADING_MS = 2f
    private const val STALE_AFTER_MS = 2 * 60_000L
    /** Heads-up when getting close to the regroup point. */
    private const val NEAR_REGROUP_M = 800.0
    /** Once everyone's at the regroup point it clears itself after this long. */
    private const val CLEAR_AFTER_ALL_MS = 2 * 60_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var sendJob: Job? = null
    private var listening = false

    private var me: Location? = null
    private var myHeading: Double? = null

    private class Remote(val name: String, val lat: Double, val lon: Double, val atMs: Long, val speedKmh: Float?, val heading: Float?)
    private val remotes = mutableMapOf<String, Remote>()
    private val separation = SeparationTracker()

    private var regroup: RegroupPoint? = null
    private var atRegroup = false
    private var announcedNear = false
    private var announcedAll = false
    private var clearJob: Job? = null

    private val listener = LocationListener { onMyLocation(it) }

    private fun enabled() = ::appContext.isInitialized && Prefs.shareLocation(appContext)
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
        startLocation()
        publish()
    }

    /** Joined (or rejoined) the ride: start sharing my position. Sending before this would fail. */
    fun onConnected() {
        sendJob?.cancel()
        sendJob = scope.launch {
            while (isActive) {
                sendMyPosition()
                val moving = (me?.speed ?: 0f) * 3.6f >= MOVING_KMH
                delay(if (moving && !DataSaver.active.value) SEND_MOVING_MS else SEND_SLOW_MS)
            }
        }
    }

    /** Back after a drop: ask for the current regroup point. */
    fun requestSync() {
        val r = room ?: return
        scope.launch { r.trySendText(JSONObject().put("t", "sync").toString(), TOPIC) }
    }

    /** Someone joined: they get the regroup point (from the rider who set it). */
    fun onRiderJoined(identity: Participant.Identity) {
        if (shouldShareRegroup()) sendRegroup(listOf(identity))
    }

    /** The Group map setting changed during a ride: start or stop right away. */
    fun applySettings() {
        if (!::appContext.isInitialized || room == null && sendJob == null && !listening) return
        if (enabled()) {
            startLocation()
        } else {
            stopLocation()
            me = null
            myHeading = null
        }
        recompute(announce = false)
    }

    /** Connection dropped: keep tracking my own position, resume sharing on reconnect. */
    fun detach() {
        room = null
        sendJob?.cancel()
        sendJob = null
    }

    fun release() {
        detach()
        stopLocation()
        me = null
        myHeading = null
        remotes.clear()
        separation.clear()
        regroup = null
        atRegroup = false
        clearJob?.cancel()
        _state.value = GroupState()
    }

    // ---- Regroup point ----

    /** Sets a regroup point for everyone (replacing any earlier one). */
    fun setRegroup(lat: Double, lon: Double, label: String) {
        if (!::appContext.isInitialized) return
        val point = RegroupPoint(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lon = lon,
            label = label.trim().ifBlank { "Regroup" },
            byName = myName(),
            byId = myId(),
            atMs = System.currentTimeMillis(),
        )
        adopt(point)
        sendRegroup(emptyList())
        say("Regroup point set: ${point.label}")
        recompute(announce = true)
    }

    /** Sets the regroup point where I am now. Returns false without a location fix. */
    fun setRegroupHere(label: String = "Regroup"): Boolean {
        val loc = me ?: return false
        setRegroup(loc.latitude, loc.longitude, label)
        return true
    }

    fun clearRegroup() {
        val point = regroup ?: return
        dropRegroup()
        val r = room
        if (r != null) scope.launch { r.trySendText(JSONObject().put("t", "regroup-clear").put("id", point.id).put("name", myName()).toString(), TOPIC) }
        say("Regroup point cleared")
    }

    private fun adopt(point: RegroupPoint) {
        regroup = point
        atRegroup = false
        announcedNear = false
        announcedAll = false
        clearJob?.cancel()
    }

    private fun dropRegroup() {
        regroup = null
        atRegroup = false
        clearJob?.cancel()
        publish()
    }

    /** The rider who set it answers for it; if they've left, the first remaining rider (by id) does. */
    private fun shouldShareRegroup(): Boolean {
        val point = regroup ?: return false
        val r = room ?: return false
        if (point.byId == myId()) return true
        val present = r.riderIds()
        if (point.byId in present) return false
        return (present + myId()).minOrNull() == myId()
    }

    private fun sendRegroup(to: List<Participant.Identity>) {
        val r = room ?: return
        val p = regroup ?: return
        val o = JSONObject()
            .put("t", "regroup")
            .put("id", p.id)
            .put("lat", p.lat)
            .put("lon", p.lon)
            .put("label", p.label)
            .put("name", p.byName)
            .put("by", p.byId)
            .put("at", p.atMs)
            .put("arrived", JSONArray(p.arrived.toList()))
        scope.launch { r.trySendText(o.toString(), TOPIC, to) }
    }

    private fun sendArrived(on: Boolean) {
        val r = room ?: return
        val p = regroup ?: return
        scope.launch { r.trySendText(JSONObject().put("t", "arrived").put("id", p.id).put("on", on).toString(), TOPIC) }
    }

    // ---- My location ----

    @SuppressLint("MissingPermission") // checked by LocationHelper.hasPermission
    private fun startLocation() {
        if (listening || !enabled() || !LocationHelper.hasPermission(appContext)) return
        val lm = appContext.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return
        try {
            providers.forEach { lm.requestLocationUpdates(it, GPS_MIN_TIME_MS, GPS_MIN_DISTANCE_M, listener, Looper.getMainLooper()) }
            listening = true
            LocationHelper.lastKnown(appContext)?.let { onMyLocation(it) }
        } catch (_: SecurityException) {
            listening = false
        }
        publish()
    }

    private fun stopLocation() {
        if (!listening) return
        appContext.getSystemService(LocationManager::class.java).removeUpdates(listener)
        listening = false
        publish()
    }

    private fun onMyLocation(location: Location) {
        val previous = me
        // Prefer the more accurate fix when GPS and network both report.
        if (previous != null && location.provider != previous.provider &&
            location.accuracy > previous.accuracy && location.time - previous.time < GPS_MIN_TIME_MS * 3
        ) {
            return
        }
        myHeading = when {
            location.hasBearing() && location.speed >= MIN_SPEED_FOR_HEADING_MS -> location.bearing.toDouble()
            previous != null && GroupMath.distanceM(previous.latitude, previous.longitude, location.latitude, location.longitude) > 30 ->
                GroupMath.bearingDeg(previous.latitude, previous.longitude, location.latitude, location.longitude)
            else -> myHeading
        }
        val firstFix = previous == null
        me = location
        recompute(announce = true)
        if (firstFix && sendJob != null) scope.launch { sendMyPosition() }
    }

    private suspend fun sendMyPosition() {
        val r = room ?: return
        val loc = me ?: return
        if (!enabled()) return
        val o = JSONObject()
            .put("name", myName())
            .put("lat", loc.latitude)
            .put("lon", loc.longitude)
            .put("at", System.currentTimeMillis())
        if (loc.hasSpeed()) o.put("spd", (loc.speed * 3.6).toInt())
        myHeading?.let { o.put("hdg", it.toInt()) }
        // Family watching from home see me only if I said they may.
        val to = r.privateAudience(Prefs.familyWatch(appContext)) ?: return
        r.trySendText(o.toString(), TOPIC, to)
    }

    // ---- Messages ----

    private fun onMessage(o: JSONObject, from: String) {
        when (o.optString("t")) {
            // Older versions send positions without a type.
            "", "pos" -> {
                remotes[from] = Remote(
                    o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getLong("at"),
                    if (o.has("spd")) o.getInt("spd").toFloat() else null,
                    if (o.has("hdg")) o.getInt("hdg").toFloat() else null,
                )
                recompute(announce = true)
            }
            "regroup" -> {
                val arrived = o.optJSONArray("arrived")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
                val point = RegroupPoint(
                    id = o.getString("id"), lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                    label = o.getString("label"), byName = o.getString("name"), byId = o.optString("by", from),
                    atMs = o.getLong("at"), arrived = arrived,
                )
                val current = regroup
                if (current?.id == point.id) {
                    regroup = current.copy(arrived = current.arrived + point.arrived)
                    recompute(announce = false)
                    return
                }
                if (!RegroupLogic.newer(current, point)) return
                adopt(point)
                recompute(announce = false)
                val s = _state.value
                say("${point.byName} set a regroup point: ${RegroupLogic.spokenWhere(point, s.regroupDistanceM, s.regroupRelation)}")
            }
            "regroup-clear" -> {
                if (regroup?.id != o.getString("id")) return
                dropRegroup()
                say("${o.optString("name", "A rider")} cleared the regroup point")
            }
            "arrived" -> {
                val point = regroup ?: return
                if (point.id != o.getString("id")) return
                regroup = point.copy(arrived = if (o.getBoolean("on")) point.arrived + from else point.arrived - from)
                checkEveryoneThere()
                publish()
            }
            "sync" -> if (shouldShareRegroup()) sendRegroup(listOf(Participant.Identity(from)))
        }
    }

    /** A rider left the ride: stop tracking them. */
    fun forget(identity: String) {
        remotes.remove(identity)
        separation.forget(identity)
        regroup?.let { regroup = it.copy(arrived = it.arrived - identity) }
        recompute(announce = false)
    }

    private fun recompute(announce: Boolean) {
        val mine = me
        // Riders who stopped sharing (or lost signal for a while) drop off instead of showing an old distance.
        val now = System.currentTimeMillis()
        remotes.entries.removeAll { now - it.value.atMs > STALE_AFTER_MS }
        val positions = remotes.mapValues { (id, r) ->
            if (mine == null) {
                RiderPosition(r.lat, r.lon, r.atMs, null, Relation.NEARBY, r.name, r.speedKmh, r.heading)
            } else {
                val d = GroupMath.distanceM(mine.latitude, mine.longitude, r.lat, r.lon)
                val relation = GroupMath.relation(d, myHeading, GroupMath.bearingDeg(mine.latitude, mine.longitude, r.lat, r.lon))
                if (announce) announceIfNeeded(id, r.name, d, relation)
                RiderPosition(r.lat, r.lon, r.atMs, d, relation, r.name, r.speedKmh, r.heading)
            }
        }
        _state.value = _state.value.copy(positions = positions)
        if (announce) checkRegroupArrival()
        publish()
    }

    private fun checkRegroupArrival() {
        val point = regroup ?: return
        val mine = me ?: return
        val d = GroupMath.distanceM(mine.latitude, mine.longitude, point.lat, point.lon)
        val nowAt = RegroupLogic.isAt(d, atRegroup)
        if (!nowAt && !atRegroup && !announcedNear && d < NEAR_REGROUP_M && d > RegroupLogic.ARRIVE_M) {
            announcedNear = true
            say("Regroup point in ${GroupMath.spokenDistance(d)}: ${point.label}")
        }
        if (nowAt == atRegroup) return
        atRegroup = nowAt
        val id = myId()
        regroup = point.copy(arrived = if (nowAt) point.arrived + id else point.arrived - id)
        sendArrived(nowAt)
        if (nowAt) {
            val here = regroup!!.arrived.size
            val total = riderIds().size
            say("You're at the regroup point. $here of $total riders here.")
        }
        checkEveryoneThere()
    }

    private fun checkEveryoneThere() {
        val point = regroup ?: return
        if (announcedAll || !RegroupLogic.everyoneThere(point, riderIds())) return
        announcedAll = true
        say("Everyone is at the regroup point")
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_ALL_MS)
            if (regroup?.id == point.id) dropRegroup()
        }
    }

    private fun riderIds(): List<String> = (room?.riderIds().orEmpty()) + myId()

    private fun publish() {
        val mine = me
        val point = regroup
        val toPoint = if (mine != null && point != null) GroupMath.distanceM(mine.latitude, mine.longitude, point.lat, point.lon) else null
        val relation = if (mine != null && point != null && toPoint != null) {
            GroupMath.relation(toPoint, myHeading, GroupMath.bearingDeg(mine.latitude, mine.longitude, point.lat, point.lon))
        } else {
            null
        }
        _state.value = _state.value.copy(
            sharing = listening,
            enabled = enabled(),
            me = mine?.let { MyFix(it.latitude, it.longitude, myHeading, it.accuracy) },
            regroup = point,
            regroupDistanceM = toPoint,
            regroupRelation = relation,
        )
    }

    private fun announceIfNeeded(id: String, name: String, distanceM: Double, relation: Relation) {
        when (val alert = separation.update(id, distanceM)) {
            is SeparationTracker.Alert.Separated -> {
                val where = when (relation) {
                    Relation.AHEAD -> "ahead"
                    Relation.BEHIND -> "behind"
                    Relation.NEARBY -> "away"
                }
                say("$name is ${GroupMath.spokenDistance(alert.distanceM)} $where")
            }
            SeparationTracker.Alert.BackTogether -> say("$name is back with the group")
            null -> Unit
        }
    }

    /** For "RideComm, where is everyone": each rider's distance and direction. */
    fun whereIsEveryone(): String {
        if (!enabled()) return "Turn on Group map in Settings to see where everyone is"
        val s = _state.value
        if (s.me == null) return "Waiting for your location"
        if (s.positions.isEmpty()) return "Nobody else is sharing their location yet"
        return s.positions.values.sortedBy { it.distanceM ?: Double.MAX_VALUE }.joinToString(". ") { p ->
            val d = p.distanceM
            when {
                d == null -> p.name
                d < GroupMath.TOGETHER_M -> "${p.name} with you"
                else -> "${p.name} ${GroupMath.spokenDistance(d)} " + when (p.relation) {
                    Relation.AHEAD -> "ahead"
                    Relation.BEHIND -> "behind"
                    Relation.NEARBY -> "away"
                }
            }
        }
    }

    /** Spoken only with the Group map setting on. */
    private fun say(text: String) {
        if (enabled()) Announcer.speak(appContext, text)
    }

    fun mapsLink(position: RiderPosition) = LocationHelper.mapsLink(position.lat, position.lon)

    /** Google Maps directions to a point (opens the Maps app when installed). */
    fun directionsLink(lat: Double, lon: Double) = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=driving"
}
