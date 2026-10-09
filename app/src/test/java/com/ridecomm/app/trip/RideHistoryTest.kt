package com.ridecomm.app.trip

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RideHistoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun ride(start: Long) = RideSummary(
        code = "CQNQNE", startedAtMs = start, endedAtMs = start + 3_600_000, distanceM = 42_300.0, movingMs = 3_000_000,
        topKmh = 92f, averageKmh = 51f, riders = listOf("Amit", "Rahul"),
        route = listOf(RoutePoint(18.5, 73.8), RoutePoint(18.6, 73.7)),
        stops = listOf(RideStop(18.55, 73.75, start + 1_000_000, 600_000)),
    )

    @Test
    fun savesUpdatesListsAndDeletes() {
        RideHistory.clear(context)
        val id = RideHistory.save(context, ride(1_000))
        val back = RideHistory.get(context, id)!!
        assertEquals(42_300.0, back.distanceM, 0.1)
        assertEquals(listOf("Amit", "Rahul"), back.riders)
        assertEquals(2, back.route.size)
        assertEquals(600_000L, back.stops.single().durationMs)
        // Saving again with the id updates the same ride (the in-progress save every minute).
        RideHistory.save(context, back.copy(distanceM = 50_000.0))
        assertEquals(1, RideHistory.count(context))
        assertEquals(50_000.0, RideHistory.get(context, id)!!.distanceM, 0.1)
        RideHistory.save(context, ride(5_000))
        assertEquals(5_000L, RideHistory.list(context).first().startedAtMs)
        RideHistory.delete(context, id)
        assertNull(RideHistory.get(context, id))
        RideHistory.clear(context)
        assertEquals(0, RideHistory.count(context))
    }

    @Test
    fun keepsTheNewest100() {
        RideHistory.clear(context)
        repeat(105) { RideHistory.save(context, ride(it * 10_000L)) }
        assertEquals(100, RideHistory.count(context))
        assertEquals(104 * 10_000L, RideHistory.list(context).first().startedAtMs)
    }
}
