package com.ridecomm.app.sos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ridecomm.app.Announcer
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.overlay.Haptics
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/** An SOS received from another rider. */
data class SosAlert(
    val identity: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    /** Metres from me, if both locations are known. */
    val distanceM: Float?,
    val atMs: Long,
)

data class SosState(
    /** Seconds left before my SOS goes out, or null when no countdown is running. */
    val countdown: Int? = null,
    /** My SOS has been sent and I haven't said I'm OK yet. */
    val mySosActive: Boolean = false,
    /** How my SOS went out, e.g. "Sent to the group" or "Sent by SMS to 2 numbers". */
    val mySosStatus: String? = null,
    val alerts: List<SosAlert> = emptyList(),
)

/**
 * Emergency stop. The rider confirms through a short cancellable countdown, then every phone in
 * the ride gets a siren, a spoken alert and the rider's location. Without internet it falls back
 * to an SMS to the rider's emergency numbers, ready to send in the Messages app.
 */
object SosManager {
    private const val TOPIC = "rc-sos"
    const val COUNTDOWN_SECONDS = 5
    private const val SIREN_SECONDS = 5
    private const val REMIND_EVERY_MS = 30_000L
    private const val MAX_REMINDERS = 5
    private const val CHANNEL_ID = "sos"
    private const val NOTIFICATION_ID = 2

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(SosState())
    val state: StateFlow<SosState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var countdownJob: Job? = null
    private var remindJob: Job? = null
    private val siren = Siren()

    /** True while an incoming alert is sounding; shared music goes silent. */
    private val _alarming = MutableStateFlow(false)
    val alarming: StateFlow<Boolean> = _alarming.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val channel = NotificationChannel(CHANNEL_ID, "SOS alerts", NotificationManager.IMPORTANCE_HIGH)
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        countdownJob?.cancel()
        stopAlarm()
        _state.value = SosState()
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    // ---- Sending ----

    /** Starts the countdown; [cancelCountdown] (tap anywhere) stops it. */
    fun startCountdown() {
        if (countdownJob != null || _state.value.mySosActive) return
        val haptics = Haptics(appContext)
        countdownJob = scope.launch {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                _state.update { it.copy(countdown = left) }
                haptics.longPress()
                Announcer.speak(appContext, if (left == COUNTDOWN_SECONDS) "SOS in $left seconds. Tap to cancel." else "$left")
                delay(1_000)
            }
            _state.update { it.copy(countdown = null) }
            countdownJob = null
            send()
        }
    }

    fun cancelCountdown() {
        if (countdownJob == null) return
        countdownJob?.cancel()
        countdownJob = null
        _state.update { it.copy(countdown = null) }
        Announcer.speak(appContext, "SOS cancelled")
    }

    private fun send() {
        val name = Prefs.riderName(appContext).ifBlank { "Rider" }
        val quick = LocationHelper.lastKnown(appContext)
        // Both the first message and the GPS follow-up carry this time, so other phones treat
        // the follow-up as the same SOS (new position, no second alarm).
        val startedAt = System.currentTimeMillis()
        val online = room != null && RideManager.state.value.status == RideStatus.CONNECTED
        _state.update { it.copy(mySosActive = true, mySosStatus = "Sending…") }
        scope.launch {
            val sentOnline = online && broadcast(sosMessage(name, quick, startedAt))
            var status = if (sentOnline) "Sent to the group" else null
            if (!sentOnline) {
                val numbers = SmsSender.parseNumbers(Prefs.emergencyNumbers(appContext))
                val opened = SmsSender.compose(appContext, numbers, smsText(name, quick))
                status = when {
                    opened -> "No internet: SOS text is ready in Messages, tap Send"
                    numbers.isEmpty() -> "No internet and no emergency numbers set"
                    else -> "No internet and couldn't open Messages"
                }
            }
            _state.update { it.copy(mySosStatus = status) }
            Announcer.speak(appContext, if (sentOnline) "SOS sent to the group" else status ?: "SOS failed")

            // Follow up with a fresh GPS fix so the group gets an accurate position.
            val precise = LocationHelper.fresh(appContext) ?: return@launch
            if (!_state.value.mySosActive) return@launch
            if (sentOnline) broadcast(sosMessage(name, precise, startedAt))
        }
    }

    /** Tells everyone I'm fine and stops their alarms. */
    fun imOk() {
        if (!_state.value.mySosActive) return
        _state.update { it.copy(mySosActive = false, mySosStatus = null) }
        val name = Prefs.riderName(appContext).ifBlank { "Rider" }
        scope.launch { broadcast(JSONObject().put("t", "ok").put("name", name)) }
        Announcer.speak(appContext, "Told the group you're OK")
    }

    private fun sosMessage(name: String, location: Location?, startedAt: Long) = JSONObject()
        .put("t", "sos")
        .put("name", name)
        .put("at", startedAt)
        .apply {
            if (location != null) {
                put("lat", location.latitude)
                put("lon", location.longitude)
            }
        }

    private fun smsText(name: String, location: Location?) = buildString {
        append("SOS from $name (RideComm). Needs help.")
        if (location != null) append(" Location: ${LocationHelper.mapsLink(location.latitude, location.longitude)}")
    }

    private suspend fun broadcast(o: JSONObject): Boolean {
        val r = room ?: return false
        return r.trySendText(o.toString(), TOPIC)
    }

    // ---- Receiving ----

    private fun onMessage(o: JSONObject, from: String) {
        when (o.getString("t")) {
            "sos" -> {
                val lat = if (o.has("lat")) o.getDouble("lat") else null
                val lon = if (o.has("lon")) o.getDouble("lon") else null
                val alert = SosAlert(
                    identity = from,
                    name = o.getString("name"),
                    lat = lat,
                    lon = lon,
                    distanceM = distanceTo(lat, lon),
                    atMs = o.getLong("at"),
                )
                val isNew = _state.value.alerts.none { it.identity == from }
                _state.update { s -> s.copy(alerts = s.alerts.filterNot { it.identity == from } + alert) }
                showNotification(alert)
                if (isNew) startAlarm(alert)
            }
            "ok" -> {
                if (_state.value.alerts.none { it.identity == from }) return
                dismiss(from)
                Announcer.speak(appContext, "${o.getString("name")} is OK")
            }
        }
    }

    /** Stops the alarm for one alert (I've seen it); the card stays until dismissed again or they're OK. */
    fun acknowledge() {
        stopAlarm()
    }

    fun dismiss(identity: String) {
        _state.update { s -> s.copy(alerts = s.alerts.filterNot { it.identity == identity }) }
        if (_state.value.alerts.isEmpty()) {
            stopAlarm()
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
    }

    private fun startAlarm(alert: SosAlert) {
        _alarming.value = true
        Haptics(appContext).sos()
        siren.play(SIREN_SECONDS)
        remindJob?.cancel()
        remindJob = scope.launch {
            delay(SIREN_SECONDS * 1_000L)
            repeat(MAX_REMINDERS) {
                val current = _state.value.alerts.lastOrNull() ?: return@launch
                Announcer.speak(appContext, spokenAlert(current))
                delay(REMIND_EVERY_MS)
            }
            _alarming.value = false
        }
        if (alert.lat == null) Announcer.speak(appContext, "Location not available yet.")
    }

    private fun stopAlarm() {
        siren.stop()
        remindJob?.cancel()
        remindJob = null
        _alarming.value = false
    }

    private fun spokenAlert(alert: SosAlert) = buildString {
        append("S O S. ${alert.name} needs help.")
        alert.distanceM?.let { append(" ${formatDistance(it)} away.") }
    }

    private fun distanceTo(lat: Double?, lon: Double?): Float? {
        if (lat == null || lon == null) return null
        val me = LocationHelper.lastKnown(appContext) ?: return null
        val result = FloatArray(1)
        Location.distanceBetween(me.latitude, me.longitude, lat, lon, result)
        return result[0]
    }

    private fun showNotification(alert: SosAlert) {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            appContext, 10, Intent(appContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SOS: ${alert.name} needs help")
            .setContentText(alert.distanceM?.let { "${formatDistance(it)} away" } ?: "Location not available yet")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        mapIntent(alert)?.let { builder.addAction(0, "Open map", it) }
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
    }

    private fun mapIntent(alert: SosAlert): PendingIntent? {
        val uri = mapsUri(alert) ?: return null
        return PendingIntent.getActivity(
            appContext, 11, Intent(Intent.ACTION_VIEW, uri), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun mapsUri(alert: SosAlert): Uri? {
        if (alert.lat == null || alert.lon == null) return null
        return Uri.parse(LocationHelper.mapsLink(alert.lat, alert.lon))
    }

    fun formatDistance(meters: Float): String =
        if (meters < 1_000) "${(meters / 10).roundToInt() * 10} meters" else "%.1f kilometers".format(meters / 1_000)
}
