package com.ridecomm.app.weather

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.BuildConfig
import com.ridecomm.app.Prefs
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.group.RideDestination
import com.ridecomm.app.nearby.AheadLogic
import com.ridecomm.app.nearby.TravelDirection
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.trip.TripTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Rain expected [inMinutes] from now, here or on the road ahead. */
data class RainOutlook(val hereInMin: Int? = null, val aheadInMin: Int? = null, val aheadKm: Int = 0)

/**
 * Rain rules (same as the web page, PROTOCOL.md): a 15-minute step in the next 2 hours with at
 * least 0.3 mm, in an hour with at least a 40 % chance. Not news if it's already raining.
 */
object RainLogic {
    const val MIN_MM = 0.3
    const val MIN_CHANCE = 40
    const val REPEAT_MS = 45 * 60_000L

    /** Minutes until rain at one place, from Open-Meteo's answer for it; null when none (or raining now). */
    fun minutesToRain(place: JSONObject, nowMs: Long): Int? {
        val m = place.optJSONObject("minutely_15") ?: return null
        val times = m.optJSONArray("time") ?: return null
        val mm = m.optJSONArray("precipitation") ?: return null
        val hourly = place.optJSONObject("hourly")
        val hourTimes = hourly?.optJSONArray("time")
        val chances = hourly?.optJSONArray("precipitation_probability")
        var first = true
        for (i in 0 until times.length()) {
            val t = parseUtc(times.optString(i)) ?: continue
            if (t + 15 * 60_000L <= nowMs) continue
            if (t - nowMs > 2 * 3_600_000L) break
            val wet = mm.optDouble(i, 0.0) >= MIN_MM
            if (first) {
                first = false
                if (wet) return null // already raining: not news
            }
            if (!wet) continue
            val chance = chanceAt(t, hourTimes, chances)
            if (chance != null && chance < MIN_CHANCE) continue
            return (((t - nowMs).coerceAtLeast(0) / 60_000.0 / 15).roundToInt() * 15).coerceAtLeast(15)
        }
        return null
    }

    private fun chanceAt(t: Long, times: JSONArray?, chances: JSONArray?): Int? {
        times ?: return null
        chances ?: return null
        for (i in 0 until times.length()) {
            val h = parseUtc(times.optString(i)) ?: continue
            if (t >= h && t < h + 3_600_000L) return chances.optInt(i, 0)
        }
        return null
    }

    /** Open-Meteo's "2026-10-09T14:15" in UTC (timezone=GMT). */
    fun parseUtc(s: String): Long? = runCatching {
        java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()

    fun outlook(json: String, aheadKm: Int, nowMs: Long): RainOutlook {
        val places = runCatching { JSONArray(json) }.getOrNull()
            ?: runCatching { JSONArray().put(JSONObject(json)) }.getOrNull() ?: return RainOutlook()
        return RainOutlook(
            hereInMin = places.optJSONObject(0)?.let { minutesToRain(it, nowMs) },
            aheadInMin = places.optJSONObject(1)?.let { minutesToRain(it, nowMs) },
            aheadKm = aheadKm,
        )
    }

    fun spoken(o: RainOutlook): String? = when {
        o.hereInMin != null -> "Rain expected here in about ${o.hereInMin} minutes"
        o.aheadInMin != null -> "Rain ahead in the next ${if (o.aheadInMin <= 60) "hour" else "2 hours"}, about ${o.aheadKm} kilometers on"
        else -> null
    }

    /** Coordinates sent to the weather service: about 1 km, no closer. */
    fun rough(v: Double) = String.format(Locale.US, "%.2f", v)
}

/** Rain alerts (Settings, off by default): checks the forecast here and ahead every 15 minutes in a ride. */
object RainWatch {
    private const val EVERY_MS = 15 * 60_000L
    private const val AHEAD_M = 25_000.0

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(RainOutlook())
    val state: StateFlow<RainOutlook> = _state.asStateFlow()
    private var job: Job? = null
    private var lastSaid = ""
    private var lastSaidMs = 0L

    fun start(context: Context) {
        stop()
        val app = context.applicationContext
        job = scope.launch {
            // Wait for a first GPS fix, then every 15 minutes.
            while (isActive) {
                if (Prefs.rainAlerts(app)) TripTracker.location.value?.let { check(app, it.latitude, it.longitude) }
                delay(if (_state.value == RainOutlook() && TripTracker.location.value == null) 30_000 else EVERY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = RainOutlook()
    }

    private suspend fun check(context: Context, lat: Double, lon: Double) {
        val bearing = TravelDirection.current(lat, lon)
        val dest = RideDestination.state.value.destination
        val (aLat, aLon, km) = when {
            dest != null && GroupMath.distanceM(lat, lon, dest.lat, dest.lon) < AHEAD_M ->
                Triple(dest.lat, dest.lon, (GroupMath.distanceM(lat, lon, dest.lat, dest.lon) / 1000).roundToInt())
            bearing != null -> AheadLogic.pointAhead(lat, lon, bearing, AHEAD_M).let { Triple(it.first, it.second, 25) }
            else -> Triple(lat, lon, 0)
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${RainLogic.rough(lat)},${RainLogic.rough(aLat)}" +
            "&longitude=${RainLogic.rough(lon)},${RainLogic.rough(aLon)}" +
            "&minutely_15=precipitation&hourly=precipitation_probability&forecast_minutely_15=9&forecast_hours=3&timezone=GMT"
        val json = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15_000
                    c.readTimeout = 15_000
                    c.setRequestProperty("User-Agent", "RideComm/${BuildConfig.VERSION_NAME}")
                    if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
                } finally {
                    c.disconnect()
                }
            }.getOrNull()
        } ?: return
        val o = RainLogic.outlook(json, km, System.currentTimeMillis()).let { if (km == 0) it.copy(aheadInMin = null) else it }
        _state.value = o
        val text = RainLogic.spoken(o) ?: return
        val kind = if (o.hereInMin != null) "here" else "ahead"
        val now = System.currentTimeMillis()
        if (kind == lastSaid && now - lastSaidMs < RainLogic.REPEAT_MS) return
        lastSaid = kind
        lastSaidMs = now
        Announcer.speak(context, text)
    }
}
