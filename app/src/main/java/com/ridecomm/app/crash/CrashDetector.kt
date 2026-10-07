package com.ridecomm.app.crash

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.sos.SosManager
import kotlin.math.sqrt

/**
 * Watches the accelerometer during a ride and starts the SOS countdown (15 s, cancellable) when
 * [CrashLogic] sees a hard impact followed by no movement. GPS speed, when allowed, helps tell a
 * crash from a phone dropped at a stop.
 */
object CrashDetector {
    const val COUNTDOWN_SECONDS = 15
    private const val SPEED_UPDATE_MS = 3_000L

    private var appContext: Context? = null
    private var thread: HandlerThread? = null
    private var logic = CrashLogic()
    private val main = Handler(Looper.getMainLooper())

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val magnitude = sqrt(x * x + y * y + z * z)
            if (logic.onSample(SystemClock.elapsedRealtime(), magnitude)) {
                main.post { SosManager.startCountdown(COUNTDOWN_SECONDS, crash = true) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val speedListener = LocationListener { location ->
        if (location.hasSpeed()) logic.onSpeed(SystemClock.elapsedRealtime(), location.speed)
    }

    val running: Boolean get() = thread != null

    /** Called when a ride starts. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (running || !Prefs.crashDetection(app)) return
        val sensors = app.getSystemService(SensorManager::class.java)
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val t = HandlerThread("RideComm-crash").apply { start() }
        thread = t
        logic = CrashLogic()
        val handler = Handler(t.looper)
        sensors.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
        startSpeed(app, t.looper)
    }

    /** Called when the ride ends. */
    fun stop() {
        val app = appContext ?: return
        app.getSystemService(SensorManager::class.java).unregisterListener(sensorListener)
        app.getSystemService(LocationManager::class.java).removeUpdates(speedListener)
        thread?.quitSafely()
        thread = null
    }

    /** The countdown was cancelled: it was a false alarm, so ignore impacts for a minute. */
    fun falseAlarm() {
        logic.cancelled(SystemClock.elapsedRealtime())
    }

    /** The setting changed: apply it if a ride is on. */
    fun applySettings(context: Context) {
        val app = context.applicationContext
        if (Prefs.crashDetection(app)) {
            if (RideManager.state.value.status != RideStatus.IDLE) start(app)
        } else {
            stop()
        }
    }

    @SuppressLint("MissingPermission") // checked by LocationHelper.hasPermission
    private fun startSpeed(context: Context, looper: Looper) {
        if (!LocationHelper.hasPermission(context)) return
        val lm = context.getSystemService(LocationManager::class.java)
        if (!runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, SPEED_UPDATE_MS, 0f, speedListener, looper)
        } catch (_: SecurityException) {
        }
    }
}
