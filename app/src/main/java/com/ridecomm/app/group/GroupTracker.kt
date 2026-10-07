package com.ridecomm.app.group

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Another rider's last shared position, as seen from my phone. */
data class RiderPosition(
    val lat: Double,
    val lon: Double,
    val atMs: Long,
    /** Null until my own location is known. */
    val distanceM: Double?,
    val relation: Relation,
)

data class GroupState(
    /** Rider identity → position. */
    val positions: Map<String, RiderPosition> = emptyMap(),
    /** True while my phone is sharing its location with the group. */
    val sharing: Boolean = false,
)

/**
 * Keeps the group together: every phone shares its GPS position with the ride every
 * [SEND_EVERY_MS], shows how far each rider is (ahead or behind), and announces when someone
 * gets separated and when they're back.
 */
object GroupTracker {
    private const val TOPIC = "rc-loc"
    private const val SEND_EVERY_MS = 15_000L
    private const val GPS_MIN_TIME_MS = 5_000L
    private const val GPS_MIN_DISTANCE_M = 10f
    /** Below this speed the GPS heading is noise. */
    private const val MIN_SPEED_FOR_HEADING_MS = 2f
    private const val STALE_AFTER_MS = 2 * 60_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var sendJob: Job? = null
    private var listening = false

    private var me: Location? = null
    private var myHeading: Double? = null

    private class Remote(val name: String, val lat: Double, val lon: Double, val atMs: Long)
    private val remotes = mutableMapOf<String, Remote>()
    private val separation = SeparationTracker()

    private val listener = LocationListener { onMyLocation(it) }

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
    }

    /** Joined (or rejoined) the ride: start sharing my position. Sending before this would fail. */
    fun onConnected() {
        sendJob?.cancel()
        sendJob = scope.launch {
            while (isActive) {
                sendMyPosition()
                delay(SEND_EVERY_MS)
            }
        }
    }

    /** "Share my location" changed in Settings during a ride: start or stop right away. */
    fun applySettings() {
        if (!::appContext.isInitialized || room == null && sendJob == null && !listening) return
        if (Prefs.shareLocation(appContext)) {
            startLocation()
        } else {
            stopLocation()
            me = null
            myHeading = null
            recompute(announce = false)
        }
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
        _state.value = GroupState()
    }

    // ---- My location ----

    @SuppressLint("MissingPermission") // checked by LocationHelper.hasPermission
    private fun startLocation() {
        if (listening || !Prefs.shareLocation(appContext) || !LocationHelper.hasPermission(appContext)) return
        val lm = appContext.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return
        try {
            providers.forEach { lm.requestLocationUpdates(it, GPS_MIN_TIME_MS, GPS_MIN_DISTANCE_M, listener, Looper.getMainLooper()) }
            listening = true
            LocationHelper.lastKnown(appContext)?.let { onMyLocation(it) }
            _state.value = _state.value.copy(sharing = true)
        } catch (_: SecurityException) {
            listening = false
        }
    }

    private fun stopLocation() {
        if (!listening) return
        appContext.getSystemService(LocationManager::class.java).removeUpdates(listener)
        listening = false
        _state.value = _state.value.copy(sharing = false)
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
        if (!Prefs.shareLocation(appContext)) return
        val o = JSONObject()
            .put("name", Prefs.riderName(appContext).ifBlank { "Rider" })
            .put("lat", loc.latitude)
            .put("lon", loc.longitude)
            .put("at", System.currentTimeMillis())
        r.trySendText(o.toString(), TOPIC)
    }

    // ---- Other riders ----

    private fun onMessage(o: JSONObject, from: String) {
        remotes[from] = Remote(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getLong("at"))
        recompute(announce = true)
    }

    /** A rider left the ride: stop tracking them. */
    fun forget(identity: String) {
        remotes.remove(identity)
        separation.forget(identity)
        recompute(announce = false)
    }

    private fun recompute(announce: Boolean) {
        val mine = me
        // Riders who stopped sharing (or lost signal for a while) drop off instead of showing an old distance.
        val now = System.currentTimeMillis()
        remotes.entries.removeAll { now - it.value.atMs > STALE_AFTER_MS }
        val positions = remotes.mapValues { (id, r) ->
            if (mine == null) {
                RiderPosition(r.lat, r.lon, r.atMs, null, Relation.NEARBY)
            } else {
                val d = GroupMath.distanceM(mine.latitude, mine.longitude, r.lat, r.lon)
                val relation = GroupMath.relation(d, myHeading, GroupMath.bearingDeg(mine.latitude, mine.longitude, r.lat, r.lon))
                if (announce) announceIfNeeded(id, r.name, d, relation)
                RiderPosition(r.lat, r.lon, r.atMs, d, relation)
            }
        }
        _state.value = _state.value.copy(positions = positions)
    }

    private fun announceIfNeeded(id: String, name: String, distanceM: Double, relation: Relation) {
        when (val alert = separation.update(id, distanceM)) {
            is SeparationTracker.Alert.Separated -> {
                val where = when (relation) {
                    Relation.AHEAD -> "ahead"
                    Relation.BEHIND -> "behind"
                    Relation.NEARBY -> "away"
                }
                Announcer.speak(appContext, "$name is ${GroupMath.spokenDistance(alert.distanceM)} $where")
            }
            SeparationTracker.Alert.BackTogether -> Announcer.speak(appContext, "$name is back with the group")
            null -> Unit
        }
    }

    fun mapsLink(position: RiderPosition) = LocationHelper.mapsLink(position.lat, position.lon)
}
