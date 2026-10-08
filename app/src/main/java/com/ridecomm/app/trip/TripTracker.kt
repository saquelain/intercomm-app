package com.ridecomm.app.trip

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.sos.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** My ride so far, from my own GPS. */
data class TripState(
    val active: Boolean = false,
    /** Wall-clock start of the ride. */
    val startedAtMs: Long = 0,
    val distanceM: Double = 0.0,
    val movingMs: Long = 0,
    val speedKmh: Float? = null,
    val topKmh: Float = 0f,
    val averageKmh: Float = 0f,
    /** False when location isn't allowed or GPS is switched off. */
    val gps: Boolean = false,
)

/**
 * Follows my own speed and distance during a ride (nothing is shared), warns by voice when I go
 * over my speed limit, and speaks a short update every so often if I asked for one.
 */
object TripTracker {
    private const val GPS_INTERVAL_MS = 1_000L
    private const val NO_SIGNAL_MS = 5_000L
    private const val TICK_MS = 2_000L

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var meter = TripMeter()
    private val speedWatch = SpeedWatch(0)
    private val updates = UpdateSchedule(UpdateEvery.OFF)
    private var startElapsed = 0L
    private var lastFixElapsed = 0L
    private var ticker: Job? = null
    private var listening = false

    private val _location = MutableStateFlow<Location?>(null)
    /** My latest GPS fix during the ride (stays on my phone; hazard alerts use it). */
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val listener = LocationListener { location ->
        _location.value = location
        lastFixElapsed = SystemClock.elapsedRealtime()
        meter.add(
            Fix(
                timeMs = location.elapsedRealtimeNanos / 1_000_000,
                lat = location.latitude,
                lon = location.longitude,
                accuracyM = if (location.hasAccuracy()) location.accuracy else 50f,
                speedMps = if (location.hasSpeed()) location.speed else null,
            ),
        )
        speedWatch.update(lastFixElapsed, meter.speedKmh)?.let { say("Speed $it") }
        publish()
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        stop()
        meter = TripMeter()
        startElapsed = SystemClock.elapsedRealtime()
        _state.value = TripState(active = true, startedAtMs = System.currentTimeMillis())
        applySettings(appContext)
        startGps()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                if (now - lastFixElapsed > NO_SIGNAL_MS) meter.noSignal()
                if (updates.due(now - startElapsed, meter.distanceM)) say(updateText(withSpeed = false))
                // Permission may have been granted after the ride started.
                if (!listening) startGps()
                publish()
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        if (::appContext.isInitialized && listening) {
            appContext.getSystemService(LocationManager::class.java).removeUpdates(listener)
        }
        listening = false
        _location.value = null
        _state.value = _state.value.copy(active = false, speedKmh = null)
    }

    /** Speed limit or update interval changed (mid-ride too). */
    fun applySettings(context: Context) {
        speedWatch.limitKmh = Prefs.speedLimit(context)
        val every = Prefs.rideUpdates(context)
        if (every != updates.every) {
            updates.every = every
            updates.restart(SystemClock.elapsedRealtime() - startElapsed, meter.distanceM)
        }
    }

    /** For "RideComm, speed": speed now plus the ride so far. */
    fun speakNow() {
        val s = _state.value
        if (!s.gps) {
            say("Speed and distance need location. Allow it for RideComm.")
            return
        }
        say(updateText(withSpeed = true))
    }

    private fun updateText(withSpeed: Boolean): String {
        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        val speed = meter.speedKmh?.takeIf { withSpeed }?.let { "Speed ${it.toInt()}. " }.orEmpty()
        return speed + TripSpeech.update(meter.distanceM, elapsed, meter.averageKmh)
    }

    @SuppressLint("MissingPermission") // checked by LocationHelper.hasPermission
    private fun startGps() {
        if (listening || !LocationHelper.hasPermission(appContext)) return
        val lm = appContext.getSystemService(LocationManager::class.java)
        if (!runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) return
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, listener, Looper.getMainLooper())
            listening = true
        }
    }

    private fun publish() {
        _state.value = _state.value.copy(
            distanceM = meter.distanceM,
            movingMs = meter.movingMs,
            speedKmh = meter.speedKmh,
            topKmh = meter.topKmh,
            averageKmh = meter.averageKmh,
            gps = listening,
        )
    }

    private fun say(text: String) {
        if (::appContext.isInitialized) Announcer.speak(appContext, text)
    }
}
