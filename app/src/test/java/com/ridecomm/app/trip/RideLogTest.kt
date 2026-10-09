package com.ridecomm.app.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideLogTest {
    @Test
    fun encodesLikeGoogle() {
        // The example from Google's polyline documentation.
        val points = listOf(RoutePoint(38.5, -120.2), RoutePoint(40.7, -120.95), RoutePoint(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", RideLog.encode(points))
        val back = RideLog.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, back.size)
        back.zip(points).forEach { (a, b) ->
            assertEquals(b.lat, a.lat, 1e-5)
            assertEquals(b.lon, a.lon, 1e-5)
        }
        assertTrue(RideLog.decode("").isEmpty())
    }

    /** 1e-4 degrees of latitude is about 11 m. */
    private fun RouteRecorder.ride(fromLat: Double, steps: Int, stepDeg: Double, startMs: Long, everyMs: Long = 5_000): Pair<Double, Long> {
        var lat = fromLat
        var t = startMs
        repeat(steps) {
            add(lat, 73.85, t, 8f)
            lat += stepDeg
            t += everyMs
        }
        return lat to t
    }

    @Test
    fun keepsAPointEvery25Metres() {
        val r = RouteRecorder()
        r.ride(18.5, 100, 0.0001, 0) // 100 fixes 11 m apart: every third one is 33 m on
        assertTrue(r.route.size in 32..36)
    }

    @Test
    fun ignoresPoorFixes() {
        val r = RouteRecorder()
        r.add(18.5, 73.85, 0, 120f)
        assertTrue(r.route.isEmpty())
    }

    @Test
    fun findsAStopInTheMiddleButNotAtStartOrEnd() {
        val r = RouteRecorder()
        // Waiting 5 min at the meeting point: not a stop.
        var (lat, t) = r.ride(18.5, 60, 0.0, 0)
        // Ride 2 km.
        r.ride(lat, 40, 0.0005, t).also { lat = it.first; t = it.second }
        // 10 min at a dhaba, wandering a few metres.
        r.ride(lat, 120, 0.000002, t).also { lat = it.first; t = it.second }
        // Ride on.
        r.ride(lat, 20, 0.0005, t).also { lat = it.first; t = it.second }
        val stops = r.stops()
        assertEquals(1, stops.size)
        assertTrue(stops[0].durationMs in 9 * 60_000L..11 * 60_000L)
        // Parked at the end: still not a stop.
        r.ride(lat, 100, 0.0, t)
        assertEquals(1, r.stops().size)
    }

    @Test
    fun shortRidesArentKept() {
        val s = RideSummary(code = "ABCDEF", startedAtMs = 0, endedAtMs = 60_000, distanceM = 100.0, movingMs = 0, topKmh = 0f, averageKmh = 0f)
        assertFalse(s.worthKeeping)
        assertTrue(s.copy(distanceM = 500.0).worthKeeping)
        assertTrue(s.copy(endedAtMs = 6 * 60_000).worthKeeping)
    }

    @Test
    fun longRoutesStaySmall() {
        val r = RouteRecorder()
        r.ride(18.5, 8000, 0.0003, 0)
        assertTrue(r.route.size <= RideLog.MAX_POINTS)
    }

    @Test
    fun durations() {
        assertEquals("25 min", RideLog.duration(25 * 60_000L))
        assertEquals("1:05 h", RideLog.duration(65 * 60_000L))
    }
}
