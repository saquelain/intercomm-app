package com.ridecomm.app.nearby

import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.group.RideDestination
import com.ridecomm.app.ride.Riders
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.trip.TripTracker
import com.ridecomm.app.overlay.Haptics
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Which way I'm riding: my GPS bearing while moving, else towards the group's destination. */
object TravelDirection {
    private const val KEEP_MS = 5 * 60_000L
    private var bearing: Double? = null
    private var atMs = 0L

    fun update(location: Location) {
        if (location.hasBearing() && location.hasSpeed() && location.speed >= 2.8f) {
            bearing = location.bearing.toDouble()
            atMs = SystemClock.elapsedRealtime()
        }
    }

    fun current(lat: Double, lon: Double): Double? {
        bearing?.takeIf { SystemClock.elapsedRealtime() - atMs <= KEEP_MS }?.let { return it }
        val d = RideDestination.state.value.destination ?: return null
        return GroupMath.bearingDeg(lat, lon, d.lat, d.lon)
    }

    fun reset() {
        bearing = null
    }
}

data class LowFuelState(
    val active: Boolean = false,
    val searching: Boolean = false,
    /** The petrol pump I'm being guided to. */
    val target: AheadPoi? = null,
    val haveDirection: Boolean = false,
    /** "No petrol pump found…" or a search problem. */
    val note: String? = null,
)

/**
 * "Low fuel": finds the next petrol pump on the road ahead (not one behind me), says how far it is,
 * and warns at 2 km and 500 m. Ends by itself when I stop at a pump. The group hears that I'm low.
 */
object LowFuel {
    private const val TOPIC = "rc-fuel"
    private const val RESEARCH_AFTER_M = 8_000.0
    private const val MIN_SEARCH_GAP_MS = 60_000L
    private const val FILLED_STOP_MS = 90_000L
    private const val AT_PUMP_M = 120.0

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(LowFuelState())
    val state: StateFlow<LowFuelState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var fixJob: Job? = null
    private var pumps: List<Poi> = emptyList()
    private var searchedAt: Pair<Double, Double>? = null
    private var lastSearchMs = -MIN_SEARCH_GAP_MS
    private var told2km = ""
    private var told500 = ""
    private var stoppedSinceMs: Long? = null

    private fun enabled() = ::appContext.isInitialized && Prefs.finder(appContext)

    fun attach(context: Context, r: Room) {
        appContext = context.applicationContext
        room = r
        r.registerTextStreamHandler(TOPIC) { reader, from ->
            scope.launch {
                val text = runCatching { reader.readAll().joinToString("") }.getOrNull() ?: return@launch
                runCatching { onMessage(JSONObject(text), from.value) }
            }
        }
        if (fixJob == null) fixJob = scope.launch { TripTracker.location.collect { it?.let(::onFix) } }
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        fixJob?.cancel()
        fixJob = null
        pumps = emptyList()
        searchedAt = null
        stoppedSinceMs = null
        TravelDirection.reset()
        _state.value = LowFuelState()
    }

    /** "Low fuel" tapped or said. */
    fun start() {
        if (!enabled() || _state.value.active) return
        _state.value = LowFuelState(active = true)
        told2km = ""
        told500 = ""
        stoppedSinceMs = null
        tellGroup(true)
        say("Looking for petrol ahead")
        search(announce = true, force = true)
    }

    /** "Got fuel", or I stopped at a pump. */
    fun stop(filled: Boolean = true) {
        if (!_state.value.active) return
        _state.value = LowFuelState()
        pumps = emptyList()
        searchedAt = null
        if (filled) tellGroup(false)
        say("Low fuel alert off")
    }

    private fun tellGroup(on: Boolean) {
        val r = room ?: return
        val o = JSONObject().put("t", "low").put("name", Prefs.riderName(appContext).ifBlank { "Rider" }).put("on", on)
        scope.launch { r.trySendText(o.toString(), TOPIC) }
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (!Riders.isRider(from) || o.optString("t") != "low") return
        val name = o.optString("name").ifBlank { "A rider" }
        Haptics(appContext).confirm()
        Announcer.speak(appContext, if (o.optBoolean("on", true)) "$name is low on fuel" else "$name has filled up")
    }

    private fun here(): Location? = TripTracker.location.value ?: LocationHelper.lastKnown(appContext)

    private fun search(announce: Boolean, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSearchMs < MIN_SEARCH_GAP_MS) return
        val me = here()
        if (me == null) {
            _state.value = _state.value.copy(note = "Waiting for your location…")
            return
        }
        lastSearchMs = now
        _state.value = _state.value.copy(searching = true)
        scope.launch {
            val bearing = TravelDirection.current(me.latitude, me.longitude)
            val found = runCatching { PoiSearch.search(appContext, PoiKind.FUEL, me.latitude, me.longitude, bearing) }
            if (!_state.value.active) return@launch
            found.onSuccess {
                pumps = it
                searchedAt = me.latitude to me.longitude
            }
            if (found.isFailure && pumps.isEmpty()) {
                _state.value = _state.value.copy(searching = false, note = "Couldn't search. Check your internet. Trying again as you ride.")
                if (announce) say("Couldn't search for petrol pumps. I'll try again as you ride.")
                return@launch
            }
            _state.value = _state.value.copy(searching = false, note = null)
            pick(me, announce)
        }
    }

    /** Chooses the best pump ahead from what was found, and says it when it's a new one. */
    private fun pick(me: Location, announce: Boolean) {
        val bearing = TravelDirection.current(me.latitude, me.longitude)
        val best = AheadLogic.ahead(me.latitude, me.longitude, bearing, pumps).firstOrNull()
        val old = _state.value.target
        _state.value = _state.value.copy(
            target = best,
            haveDirection = bearing != null,
            note = if (best == null) "No petrol pump found in the next 20 km. Still looking." else null,
        )
        if (best == null) {
            if (announce) say("No petrol pump found in the next 20 kilometers. I'll keep looking.")
            return
        }
        if (announce || old?.poi?.id != best.poi.id) {
            val where = if (bearing != null) "ahead" else "nearby"
            say("${if (old != null && old.poi.id != best.poi.id) "Next" else "Nearest"} petrol pump $where: ${best.poi.name}, ${GroupMath.spokenDistance(best.distanceM)}")
        }
    }

    private fun onFix(location: Location) {
        TravelDirection.update(location)
        if (!_state.value.active) return
        val bearing = TravelDirection.current(location.latitude, location.longitude)
        val target = _state.value.target
        val now = SystemClock.elapsedRealtime()

        // Stopped at a pump for a while: filled up.
        val atPump = pumps.any { GroupMath.distanceM(location.latitude, location.longitude, it.lat, it.lon) <= AT_PUMP_M }
        val still = !location.hasSpeed() || location.speed < 1.5f
        stoppedSinceMs = if (atPump && still) stoppedSinceMs ?: now else null
        if (stoppedSinceMs?.let { now - it >= FILLED_STOP_MS } == true) {
            stop(filled = true)
            return
        }

        if (target != null) {
            val d = GroupMath.distanceM(location.latitude, location.longitude, target.poi.lat, target.poi.lon)
            val now0 = AheadLogic.ahead(location.latitude, location.longitude, bearing, listOf(target.poi), maxM = Double.MAX_VALUE).firstOrNull()
            val behind = bearing != null && now0 == null && d > 150
            if (behind) {
                pumps = pumps.filter { it.id != target.poi.id }
                _state.value = _state.value.copy(target = null)
                pick(location, announce = true)
            } else {
                val fresh = now0 ?: AheadPoi(target.poi, d, target.deltaDeg, target.side)
                _state.value = _state.value.copy(target = fresh, haveDirection = bearing != null)
                val side = fresh.side?.let { ", on your $it" }.orEmpty()
                if (d <= 500 && told500 != target.poi.id) {
                    told500 = target.poi.id
                    told2km = target.poi.id
                    Haptics(appContext).confirm()
                    say("Petrol pump in ${GroupMath.spokenDistance(d)}$side")
                } else if (d <= 2_000 && d > 500 && told2km != target.poi.id) {
                    told2km = target.poi.id
                    say("Petrol pump in ${GroupMath.spokenDistance(d)}")
                }
            }
        }
        val from = searchedAt
        val movedFar = from != null && GroupMath.distanceM(from.first, from.second, location.latitude, location.longitude) > RESEARCH_AFTER_M
        if (movedFar || _state.value.target == null && !_state.value.searching) search(announce = false)
    }

    private fun say(text: String) {
        if (::appContext.isInitialized) Announcer.speak(appContext, text)
    }
}
