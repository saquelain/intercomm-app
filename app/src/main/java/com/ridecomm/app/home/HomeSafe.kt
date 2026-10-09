package com.ridecomm.app.home

import com.ridecomm.app.score.Score
import com.ridecomm.app.ride.riderIds
import com.ridecomm.app.ride.Riders
import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.ride.RecentRide
import com.ridecomm.app.ride.RidePass
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.trip.TripTracker
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class HomeSafeState(
    /** The Home safe setting is on. */
    val enabled: Boolean = false,
    /** I told the group I'm home. */
    val meHome: Boolean = false,
    /** Riders who said they're home (id → name). */
    val home: Map<String, String> = emptyMap(),
    /** Riders who left the ride without saying they got home (id → name). */
    val leftNotHome: Map<String, String> = emptyMap(),
)

/**
 * "Home safe": at the end of the ride each rider taps I'm home safe (or it happens by itself when
 * they reach the home they saved in Settings), and the riders still in the ride hear "Asha is home
 * safe" and see who's still on the road. Home's position stays on the phone; only "home" is sent.
 * A rider who already left can still check in from the home screen (see [checkInLater]).
 */
object HomeSafe {
    private const val TOPIC = "rc-home"
    private const val CHECK_IN_TIMEOUT_MS = 20_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(HomeSafeState())
    val state: StateFlow<HomeSafeState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var locationJob: Job? = null
    private var arrival = HomeArrival()
    /** Riders seen in this ride (id → name), to know who left without getting home. */
    private val seen = mutableMapOf<String, String>()
    /** Said home but my "home" didn't go out yet (no connection). */
    private var homeUnsent = false

    private fun enabled() = ::appContext.isInitialized && Prefs.homeSafe(appContext)
    private fun myName() = Prefs.riderName(appContext).ifBlank { "Rider" }

    /** Ride starting: watch for arriving home. */
    fun start(context: Context) {
        appContext = context.applicationContext
        arrival = HomeArrival()
        locationJob?.cancel()
        locationJob = scope.launch {
            TripTracker.location.collect { loc ->
                val home = Prefs.homeSpot(appContext) ?: return@collect
                if (loc == null || !enabled() || _state.value.meHome) return@collect
                if (arrival.update(GroupMath.distanceM(loc.latitude, loc.longitude, home.first, home.second))) {
                    imHome()
                    Announcer.speak(appContext, "You're home. Told the group you're home safe.")
                }
            }
        }
        publish()
    }

    fun attach(r: Room) {
        room = r
        r.registerTextStreamHandler(TOPIC) { reader, from ->
            scope.launch {
                val text = runCatching { reader.readAll().joinToString("") }.getOrNull() ?: return@launch
                runCatching { onMessage(JSONObject(text), from.value) }
            }
        }
    }

    /** Connected: note who's here, and send a "home" that waited for the connection. */
    fun onConnected() {
        val r = room ?: return
        r.remoteParticipants.values.filter { Riders.isRider(it) }.forEach { noteRider(it) }
        if (homeUnsent) sendHome()
    }

    fun onRiderJoined(p: Participant) = noteRider(p)

    fun onRiderLeft(p: Participant) {
        val id = p.identity?.value ?: return
        if (HomeLogic.isCheckIn(id)) return
        if (id !in _state.value.home) {
            val name = seen[id] ?: p.name?.takeIf { it.isNotBlank() } ?: "A rider"
            _state.value = _state.value.copy(leftNotHome = _state.value.leftNotHome + (id to name))
        }
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        locationJob?.cancel()
        locationJob = null
        seen.clear()
        homeUnsent = false
        _state.value = HomeSafeState()
    }

    fun applySettings() = publish()

    /** Tell the riders still in the ride that I got home. */
    fun imHome() {
        if (!enabled()) return
        _state.value = _state.value.copy(meHome = true)
        Score.noteHome(appContext)
        homeUnsent = true
        sendHome()
    }

    private fun sendHome() {
        val r = room ?: return
        scope.launch {
            if (r.trySendText(JSONObject().put("t", "home").put("name", myName()).toString(), TOPIC)) homeUnsent = false
        }
    }

    /** Leaving the ride: remember it for a check-in later, unless I already said I'm home. */
    fun onLeaving(context: Context, code: String) {
        val home = _state.value.meHome
        Prefs.setPendingHomeCheckIn(context, if (Prefs.homeSafe(context) && !home) RecentRide(code, System.currentTimeMillis()) else null)
    }

    /**
     * Already left the ride, now home: joins it for a moment (no mic) to say so to whoever is still
     * riding. Returns how many riders heard it, or null when it couldn't connect.
     */
    suspend fun checkInLater(context: Context, code: String): Int? {
        val app = context.applicationContext
        val r = LiveKit.create(app)
        return try {
            withTimeout(CHECK_IN_TIMEOUT_MS) {
                val pass = RidePass.fetch(app, code, HomeLogic.checkInIdentity(Prefs.deviceId(app)))
                r.connect(pass.serverUrl, pass.participantToken)
                // Give the room a moment to list who's there.
                delay(1_000)
                val name = Prefs.riderName(app).ifBlank { "Rider" }
                r.trySendText(JSONObject().put("t", "home").put("name", name).toString(), TOPIC)
                delay(500)
                Prefs.setPendingHomeCheckIn(app, null)
                Score.noteHome(app)
                r.riderIds().size
            }
        } catch (e: Exception) {
            null
        } finally {
            r.disconnect()
            r.release()
        }
    }

    private fun noteRider(p: Participant) {
        val id = p.identity?.value ?: return
        if (HomeLogic.isCheckIn(id)) return
        seen[id] = p.name?.takeIf { it.isNotBlank() } ?: "A rider"
        // Back in the ride: they're not "left" any more.
        if (id in _state.value.leftNotHome) _state.value = _state.value.copy(leftNotHome = _state.value.leftNotHome - id)
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (o.optString("t") != "home" || !enabled()) return
        val id = HomeLogic.riderOf(from)
        val name = o.optString("name").ifBlank { seen[id] ?: "A rider" }
        val s = _state.value
        if (id in s.home) return
        _state.value = s.copy(home = s.home + (id to name), leftNotHome = s.leftNotHome - id)
        Announcer.speak(appContext, "$name is home safe")
    }

    private fun publish() {
        _state.value = _state.value.copy(enabled = enabled())
    }
}
