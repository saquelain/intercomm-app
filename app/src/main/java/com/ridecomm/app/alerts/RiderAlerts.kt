package com.ridecomm.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** A phone's battery as last reported. */
data class BatteryInfo(val level: Int, val charging: Boolean)

/**
 * Spoken heads-ups about the other riders, so nobody has to look at the phone:
 * "Rahul dropped out" / "Rahul is back" / "Amit's phone battery is at 15 percent".
 * Each phone shares its battery level with the group (only when it changes by 5% or more) and
 * says "bye" when its rider taps Leave, so a deliberate leave isn't announced as a dropout.
 */
object RiderAlerts {
    private const val TOPIC = "rc-rider"
    /** Wait before announcing a dropout: if it was my own connection that died, say nothing. */
    private const val DROP_CONFIRM_MS = 2_500L

    private val scope = safeMainScope()
    private val _batteries = MutableStateFlow<Map<String, BatteryInfo>>(emptyMap())
    /** Rider identity → battery, mine included. */
    val batteries: StateFlow<Map<String, BatteryInfo>> = _batteries.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var presence = Presence()
    private val watches = mutableMapOf<String, BatteryWatch>()
    private var mine: BatteryInfo? = null
    private var lastSent: BatteryInfo? = null
    private var receiver: BroadcastReceiver? = null

    /** Ride starting: begin following my own battery. */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onBatteryChanged(intent)
        }
        receiver = r
        ContextCompat.registerReceiver(appContext, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { onBatteryChanged(it) }
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

    /** Connected: note who's already here and tell them my battery. */
    fun onConnected() {
        val r = room ?: return
        r.remoteParticipants.keys.forEach { presence.known(it.value) }
        sendBattery(to = emptyList())
    }

    fun onRiderJoined(participant: Participant) {
        val id = participant.identity ?: return
        val event = presence.joined(id.value)
        announce(Presence.spoken(nameOf(participant), event))
        sendBattery(to = listOf(id))
    }

    fun onRiderLeft(participant: Participant) {
        val id = participant.identity?.value ?: return
        val name = nameOf(participant)
        val r = room
        val event = presence.left(id, SystemClock.elapsedRealtime())
        _batteries.value = _batteries.value - id
        watches.remove(id)
        if (event == PresenceEvent.LEFT) {
            announce(Presence.spoken(name, event))
            return
        }
        scope.launch {
            delay(DROP_CONFIRM_MS)
            // If my own connection went down meanwhile, everyone "left" from my side: stay quiet.
            if (room === r && r != null && RideManager.state.value.status == RideStatus.CONNECTED) {
                announce(Presence.spoken(name, event))
            }
        }
    }

    /** I'm leaving on purpose: tell the others so they hear "left" rather than "dropped out". */
    suspend fun sayBye(r: Room) {
        withTimeoutOrNull(BYE_TIMEOUT_MS) { r.trySendText(JSONObject().put("t", "bye").toString(), TOPIC) }
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        presence = Presence()
        watches.clear()
        _batteries.value = emptyMap()
        lastSent = null
        receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        receiver = null
    }

    private fun onBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val info = BatteryInfo(level * 100 / scale, charging)
        if (info == mine) return
        mine = info
        val myId = Prefs.deviceId(appContext)
        onBattery(myId, null, info)
        val sent = lastSent
        if (sent == null || sent.charging != info.charging || sent.level / 5 != info.level / 5) sendBattery(to = emptyList())
    }

    private fun onMessage(o: JSONObject, from: String) {
        when (o.optString("t")) {
            "bye" -> presence.bye(from, SystemClock.elapsedRealtime())
            "battery" -> {
                val name = room?.remoteParticipants?.entries?.firstOrNull { it.key.value == from }?.value?.let { nameOf(it) } ?: return
                onBattery(from, name, BatteryInfo(o.getInt("level").coerceIn(0, 100), o.optBoolean("charging")))
            }
        }
    }

    /** [name] is null for my own phone. */
    private fun onBattery(id: String, name: String?, info: BatteryInfo) {
        _batteries.value = _batteries.value + (id to info)
        val level = watches.getOrPut(id) { BatteryWatch() }.update(info.level, info.charging) ?: return
        announce(BatteryWatch.spoken(name, level))
    }

    private fun sendBattery(to: List<Participant.Identity>) {
        val r = room ?: return
        val info = mine ?: return
        scope.launch {
            val o = JSONObject().put("t", "battery").put("level", info.level).put("charging", info.charging)
            if (r.trySendText(o.toString(), TOPIC, to) && to.isEmpty()) lastSent = info
        }
    }

    private fun announce(text: String) {
        if (::appContext.isInitialized && Prefs.riderAlerts(appContext)) Announcer.speak(appContext, text)
    }

    private fun nameOf(p: Participant) = p.name?.takeIf { it.isNotBlank() } ?: "A rider"

    private const val BYE_TIMEOUT_MS = 600L
}
