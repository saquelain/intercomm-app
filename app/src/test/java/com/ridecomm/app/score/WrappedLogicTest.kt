package com.ridecomm.app.score

import com.ridecomm.app.trip.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class WrappedLogicTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun ride(id: String, atMs: Long, km: Double, min: Int = 60, names: List<String> = emptyList(), lead: Boolean = false, sweep: Boolean = false, hz: Int = 0) =
        ScoreEntry(id, atMs, km, min, others = names.size, names = names, lead = lead, sweep = sweep, hazards = hz)

    @Test
    fun whichYear() {
        assertEquals(2026, WrappedLogic.yearFor(at(2026, 10, 9), zone))
        assertEquals(2026, WrappedLogic.yearFor(at(2027, 1, 15), zone))
        assertEquals(2027, WrappedLogic.yearFor(at(2027, 1, 16), zone))
        assertTrue(WrappedLogic.season(at(2026, 12, 1), zone))
        assertTrue(WrappedLogic.season(at(2027, 1, 15), zone))
        assertFalse(WrappedLogic.season(at(2026, 11, 30), zone))
        assertFalse(WrappedLogic.season(at(2027, 1, 16), zone))
    }

    @Test
    fun comparisons() {
        assertEquals("That's 67% of the way from Mumbai to Pune", WrappedLogic.comparison(100.0))
        assertEquals("That's like riding Mumbai to Pune", WrappedLogic.comparison(150.0))
        assertEquals("That's like riding Mumbai to Goa 1.5 times", WrappedLogic.comparison(900.0))
        assertEquals("That's like riding Delhi to Leh 2 times", WrappedLogic.comparison(2_000.0))
        assertEquals("That's like riding Kashmir to Kanyakumari 3.5 times", WrappedLogic.comparison(13_000.0))
        assertEquals("3.1% of the way around the Earth", WrappedLogic.earth(1_250.0))
        assertEquals("32% of the way around the Earth", WrappedLogic.earth(13_000.0))
    }

    @Test
    fun riderTypes() {
        val sat = at(2026, 10, 3)
        val wed = at(2026, 10, 7)
        assertEquals(RiderType.LONG_HAULER, WrappedLogic.riderType(listOf(ride("a", wed, 200.0), ride("b", wed, 120.0)), zone))
        assertEquals(RiderType.EARLY_BIRD, WrappedLogic.riderType(listOf(ride("a", at(2026, 10, 7, h = 5), 50.0), ride("b", wed, 50.0)), zone))
        assertEquals(RiderType.ROAD_CAPTAIN, WrappedLogic.riderType(listOf(ride("a", wed, 50.0, lead = true), ride("b", wed, 50.0)), zone))
        assertEquals(RiderType.GUARDIAN, WrappedLogic.riderType(listOf(ride("a", wed, 50.0, hz = 1), ride("b", wed, 50.0)), zone))
        assertEquals(RiderType.PACK_RIDER, WrappedLogic.riderType(listOf(ride("a", wed, 50.0, names = listOf("A", "B", "C"))), zone))
        assertEquals(RiderType.WEEKEND_WARRIOR, WrappedLogic.riderType(listOf(ride("a", sat, 50.0), ride("b", sat + 86_400_000, 50.0)), zone))
        assertEquals(RiderType.EXPLORER, WrappedLogic.riderType(listOf(ride("a", wed, 50.0), ride("b", sat, 50.0)), zone))
    }

    @Test
    fun theYear() {
        val route = listOf(RoutePoint(18.5, 73.8), RoutePoint(18.6, 73.9))
        val entries = listOf(
            ride("a", at(2026, 3, 1, h = 6), 320.0, min = 400, names = listOf("Amit", "Rahul")), // a Sunday
            ride("b", at(2026, 3, 8, h = 6), 120.0, min = 150, names = listOf("Amit")), // a Sunday
            ride("c", at(2026, 7, 15, h = 17), 80.0, min = 100), // a Wednesday
            ride("old", at(2025, 12, 31), 500.0),
        )
        val w = WrappedLogic.build(entries, listOf(at(2026, 3, 1, h = 6) + 30_000 to route, at(2025, 12, 31) to route), 2026, zone)!!
        assertEquals(3, w.rides)
        assertEquals(520.0, w.km, 0.01)
        assertEquals(10, w.hours)
        assertEquals(Calendar.SUNDAY, w.favouriteDay)
        assertEquals(6, w.usualHour)
        assertEquals("a", w.longest.id)
        assertEquals(route, w.longestRoute)
        assertEquals(2, w.bestMonth)
        assertEquals(2, w.riders)
        assertEquals(listOf("Amit" to 2, "Rahul" to 1), w.buddies)
        assertEquals(1, w.routes.size)
        // First ride, Century and Long haul came with the 500 km ride in 2025; the 1,000 km club in July 2026.
        assertEquals(listOf(Badge.KM_1000), w.badges)
        assertEquals("That's like riding Delhi to Jaipur 1.8 times", w.comparison)
        assertNull(WrappedLogic.build(entries, emptyList(), 2024, zone))
        assertEquals("around 6 am", WrappedLogic.hourText(6))
        assertEquals("around 5 pm", WrappedLogic.hourText(17))
        assertEquals("around noon", WrappedLogic.hourText(12))
    }
}
