package com.ridecomm.app.trip

import com.ridecomm.app.group.GroupMath
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

data class RoutePoint(val lat: Double, val lon: Double)

/** Somewhere I stayed a while during a ride (a dhaba, a fuel stop). */
data class RideStop(val lat: Double, val lon: Double, val atMs: Long, val durationMs: Long)

/** One ride as kept in the history on this phone. */
data class RideSummary(
    val id: Long = 0,
    val code: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val topKmh: Float,
    val averageKmh: Float,
    val riders: List<String> = emptyList(),
    val route: List<RoutePoint> = emptyList(),
    val stops: List<RideStop> = emptyList(),
) {
    val totalMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)

    /** Too short to keep (a test, or a ride left straight away). */
    val worthKeeping: Boolean get() = distanceM >= RideLog.MIN_DISTANCE_M || totalMs >= RideLog.MIN_DURATION_MS
}

/**
 * The route and stops of my ride, from my own GPS, kept small enough to store hundreds of rides:
 * a point only every 25 m, and every other point dropped once a long ride passes 3000.
 */
class RouteRecorder {
    private val points = ArrayList<RoutePoint>()
    private val stops = ArrayList<RideStop>()
    private var stay: Stay? = null
    private var lastMs = 0L

    private data class Stay(val lat: Double, val lon: Double, val sinceMs: Long, val firstPointIndex: Int)

    val route: List<RoutePoint> get() = points.toList()

    fun add(lat: Double, lon: Double, atMs: Long, accuracyM: Float) {
        if (accuracyM > RideLog.MAX_ACCURACY_M || atMs < lastMs) return
        lastMs = atMs
        val last = points.lastOrNull()
        if (last == null || GroupMath.distanceM(last.lat, last.lon, lat, lon) >= RideLog.POINT_EVERY_M) {
            points += RoutePoint(lat, lon)
            if (points.size > RideLog.MAX_POINTS) thin()
        }
        val s = stay
        if (s == null) {
            stay = Stay(lat, lon, atMs, points.size)
            return
        }
        val away = GroupMath.distanceM(s.lat, s.lon, lat, lon)
        val stopped = atMs - s.sinceMs >= RideLog.STOP_MIN_MS
        if (stopped && away > RideLog.STOP_LEAVE_M) {
            // Rode on after a stop. A wait at the very start (the meeting point) isn't a stop.
            if (s.firstPointIndex > 1) stops += RideStop(s.lat, s.lon, s.sinceMs, lastStayMs - s.sinceMs)
            stay = Stay(lat, lon, atMs, points.size)
        } else if (!stopped && away > RideLog.STOP_RADIUS_M) {
            stay = Stay(lat, lon, atMs, points.size)
        }
        if (away <= RideLog.STOP_LEAVE_M) lastStayMs = atMs
    }

    private var lastStayMs = 0L

    /** Stops so far. One still going at the end of the ride is where it ended, not a stop. */
    fun stops(): List<RideStop> = stops.toList()

    private fun thin() {
        val kept = points.filterIndexed { i, _ -> i % 2 == 0 || i == points.lastIndex }
        points.clear()
        points.addAll(kept)
    }
}

object RideLog {
    const val MIN_DISTANCE_M = 300.0
    const val MIN_DURATION_MS = 5 * 60_000L
    const val MAX_ACCURACY_M = 50f
    const val POINT_EVERY_M = 25.0
    const val MAX_POINTS = 3000
    const val STOP_RADIUS_M = 60.0
    const val STOP_LEAVE_M = 100.0
    const val STOP_MIN_MS = 3 * 60_000L

    /** Google's encoded polyline (5 decimals): a long route in a short string, readable by map tools. */
    fun encode(points: List<RoutePoint>): String {
        val out = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lon = (p.lon * 1e5).roundToLong()
            encodeValue(lat - lastLat, out)
            encodeValue(lon - lastLon, out)
            lastLat = lat
            lastLon = lon
        }
        return out.toString()
    }

    private fun encodeValue(value: Long, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        out.append((v + 63).toInt().toChar())
    }

    fun decode(encoded: String): List<RoutePoint> {
        val points = ArrayList<RoutePoint>()
        var i = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long? {
            var result = 0L
            var shift = 0
            while (i < encoded.length) {
                val b = encoded[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                if (b < 0x20) return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            }
            return null
        }
        while (i < encoded.length) {
            lat += next() ?: break
            lon += next() ?: break
            points += RoutePoint(lat / 1e5, lon / 1e5)
        }
        return points
    }

    fun stopsJson(stops: List<RideStop>): String = JSONArray().apply {
        stops.forEach { put(JSONObject().put("lat", it.lat).put("lon", it.lon).put("at", it.atMs).put("dur", it.durationMs)) }
    }.toString()

    fun stopsFrom(json: String?): List<RideStop> = runCatching {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            RideStop(it.getDouble("lat"), it.getDouble("lon"), it.getLong("at"), it.getLong("dur"))
        }
    }.getOrDefault(emptyList())

    fun namesJson(names: List<String>): String = JSONArray(names).toString()

    fun namesFrom(json: String?): List<String> = runCatching {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    /** "1:14 h" or "25 min". */
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return if (min < 60) "$min min" else "${min / 60}:${(min % 60).toString().padStart(2, '0')} h"
    }
}
