package com.ridecomm.app.group

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.riderIds
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.trip.TripTracker
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class DestinationState(
    /** The Shared destination setting is on. */
    val enabled: Boolean = false,
    val destination: Destination? = null,
    /** Straight-line distance from me, when my position is known. */
    val distanceM: Double? = null,
    val arrived: Boolean = false,
)

/**
 * Where the group is heading. Any rider sets it (search a place, paste a maps link, or long-press
 * the group map); everyone hears it, sees how far it is and gets one Navigate button. My own
 * position is used only on my phone (for the distance and "You've reached Lonavala"); it isn't
 * shared by this feature. Works without the Group map.
 */
object RideDestination {
    private const val TOPIC = "rc-dest"

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(DestinationState())
    val state: StateFlow<DestinationState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var locationJob: Job? = null
    private var destination: Destination? = null
    private var arrived = false

    private fun enabled() = ::appContext.isInitialized && Prefs.sharedDestination(appContext)
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
        if (locationJob == null) locationJob = scope.launch { TripTracker.location.collect { publish(announce = true) } }
        publish(announce = false)
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        locationJob?.cancel()
        locationJob = null
        destination = null
        arrived = false
        _state.value = DestinationState()
    }

    /** Just joined or back after a drop: ask for the destination. */
    fun requestSync() {
        val r = room ?: return
        if (!enabled()) return
        scope.launch { r.trySendText(JSONObject().put("t", "sync").toString(), TOPIC) }
    }

    fun onRiderJoined(identity: Participant.Identity) {
        if (shouldShare()) send(listOf(identity))
    }

    /** The setting changed mid-ride. */
    fun applySettings() {
        if (!::appContext.isInitialized) return
        if (!enabled()) {
            destination = null
            arrived = false
        } else {
            requestSync()
        }
        publish(announce = false)
    }

    /** Sets where the group is heading, for everyone. */
    fun set(lat: Double, lon: Double, label: String) {
        if (!enabled()) return
        val d = Destination(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lon = lon,
            label = label.trim().ifBlank { "Destination" }.take(DestinationLogic.MAX_LABEL),
            byName = myName(),
            byId = myId(),
            atMs = System.currentTimeMillis(),
        )
        adopt(d)
        send(emptyList())
        say("Destination set: ${spokenWhere(d)}")
    }

    fun clear() {
        val d = destination ?: return
        destination = null
        arrived = false
        publish(announce = false)
        val r = room
        if (r != null) scope.launch { r.trySendText(JSONObject().put("t", "dest-clear").put("id", d.id).put("name", myName()).toString(), TOPIC) }
        say("Destination cleared")
    }

    private fun adopt(d: Destination) {
        destination = d
        arrived = false
        publish(announce = false)
        // Already there (e.g. set while standing at it): no "you've reached" right away.
        arrived = _state.value.distanceM?.let { it < DestinationLogic.ARRIVE_M } == true
        publish(announce = false)
    }

    /** The rider who set it answers for it; if they've left, the first remaining rider (by id) does. */
    private fun shouldShare(): Boolean {
        val d = destination ?: return false
        val r = room ?: return false
        if (d.byId == myId()) return true
        val present = r.riderIds()
        if (d.byId in present) return false
        return (present + myId()).minOrNull() == myId()
    }

    private fun send(to: List<Participant.Identity>) {
        val r = room ?: return
        val d = destination ?: return
        val o = JSONObject()
            .put("t", "dest")
            .put("id", d.id)
            .put("lat", d.lat)
            .put("lon", d.lon)
            .put("label", d.label)
            .put("name", d.byName)
            .put("by", d.byId)
            .put("at", d.atMs)
        scope.launch { r.trySendText(o.toString(), TOPIC, to) }
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (!enabled()) return
        when (o.optString("t")) {
            "dest" -> {
                val d = Destination(
                    id = o.getString("id"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    label = o.optString("label").ifBlank { "Destination" }.take(DestinationLogic.MAX_LABEL),
                    byName = o.optString("name", "A rider"),
                    byId = o.optString("by", from),
                    atMs = o.getLong("at"),
                )
                if (destination?.id == d.id || !DestinationLogic.newer(destination, d)) return
                adopt(d)
                say("${d.byName} set the destination: ${spokenWhere(d)}")
            }
            "dest-clear" -> {
                if (destination?.id != o.optString("id")) return
                destination = null
                arrived = false
                publish(announce = false)
                say("${o.optString("name", "A rider")} cleared the destination")
            }
            "sync" -> if (shouldShare()) send(listOf(Participant.Identity(from)))
        }
    }

    private fun myPosition(): Pair<Double, Double>? {
        TripTracker.location.value?.let { return it.latitude to it.longitude }
        return GroupTracker.state.value.me?.let { it.lat to it.lon }
    }

    private fun publish(announce: Boolean) {
        val d = destination
        val me = myPosition()
        val distance = if (d != null && me != null) GroupMath.distanceM(me.first, me.second, d.lat, d.lon) else null
        if (announce && d != null && distance != null && !arrived && distance < DestinationLogic.ARRIVE_M) {
            arrived = true
            say("You've reached ${d.label}")
        }
        _state.value = DestinationState(enabled = enabled(), destination = d, distanceM = distance, arrived = arrived)
    }

    /** "Lonavala, 42 kilometers away". */
    private fun spokenWhere(d: Destination): String {
        val me = myPosition() ?: return d.label
        return "${d.label}, ${GroupMath.spokenDistance(GroupMath.distanceM(me.first, me.second, d.lat, d.lon))} away"
    }

    private fun say(text: String) {
        if (enabled()) Announcer.speak(appContext, text)
    }
}
