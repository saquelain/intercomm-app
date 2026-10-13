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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Watches the accelerometer during a ride and starts the SOS countdown (15 s, cancellable) when
 * [CrashLogic] sees a hard impact followed by no movement. GPS speed, when allowed, helps tell a
 * crash from a phone dropped at a stop.
 */
object CrashDetector {
    const val COUNTDOWN_SECONDS = 15
    private const val SPEED_UPDATE_MS = 3_000L
    /**
     * 100 readings a second. A hard hit lasts only a few hundredths of a second, so the old 50 a
     * second ("game" rate) often read either side of the peak and missed it.
     */
    private const val SAMPLING_US = 10_000

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
        sensors.registerListener(sensorListener, accelerometer, SAMPLING_US, handler)
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

    // ---- Test mode (Settings → Safety → Test crash detection): same logic, no speed check, never sends ----

    /** What the test screen shows. */
    data class TestState(
        val running: Boolean = false,
        val phase: CrashLogic.Phase = CrashLogic.Phase.WAITING,
        val peakG: Float = 0f,
        val impactG: Float = 0f,
        val stillProgress: Float = 0f,
        val noSensor: Boolean = false,
    )

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test.asStateFlow()
    private var testThread: HandlerThread? = null
    private var testLogic = CrashLogic(requireRiding = false)
    private var testShownAt = 0L

    private val testListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            val now = SystemClock.elapsedRealtime()
            val crashed = testLogic.onSample(now, sqrt(x * x + y * y + z * z))
            // The screen needs a few updates a second, not a hundred.
            if (crashed || now - testShownAt >= 150) {
                testShownAt = now
                _test.value = TestState(true, testLogic.phase, testLogic.peakG, testLogic.impactPeakG, testLogic.stillProgress(now))
            }
            if (crashed) main.post { appContext?.let { com.ridecomm.app.overlay.Haptics(it).sos() } }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Starts (or restarts) the test. */
    fun startTest(context: Context) {
        val app = context.applicationContext
        appContext = appContext ?: app
        stopTest(app)
        val sensors = app.getSystemService(SensorManager::class.java)
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            _test.value = TestState(noSensor = true)
            return
        }
        testLogic = CrashLogic(requireRiding = false)
        val t = HandlerThread("RideComm-crash-test").apply { start() }
        testThread = t
        _test.value = TestState(running = true)
        sensors.registerListener(testListener, accelerometer, SAMPLING_US, Handler(t.looper))
    }

    fun stopTest(context: Context) {
        context.applicationContext.getSystemService(SensorManager::class.java).unregisterListener(testListener)
        testThread?.quitSafely()
        testThread = null
        _test.value = _test.value.copy(running = false)
    }
}
