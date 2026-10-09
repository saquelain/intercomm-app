package com.ridecomm.app.garage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class GarageLogicTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 10) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private val now = at(2026, 10, 9)

    private fun item(everyKm: Int, months: Int = 0, lastKm: Double = 10_000.0, lastAt: Long = at(2026, 9, 1)) =
        ServiceItem("i", "Oil change", everyKm, months, lastKm, lastAt)

    @Test
    fun newBikeGetsTheUsualItems() {
        val b = GarageLogic.newBike(" Classic 350 ", "mh12ab1234", 12_000.0, now)
        assertEquals("Classic 350", b.name)
        assertEquals("MH12AB1234", b.reg)
        assertEquals(listOf("Oil change", "Chain clean & lube", "Air filter", "General service"), b.items.map { it.name })
        assertEquals(listOf(3_000, 600, 10_000, 5_000), b.items.map { it.everyKm })
        assertEquals(listOf(6, 0, 0, 12), b.items.map { it.everyMonths })
        assertEquals(12_000.0, b.items[0].lastKm, 0.0)
    }

    @Test
    fun byKm() {
        assertEquals("Oil change in 1,500 km", GarageLogic.itemNote(item(3_000), 11_500.0, now).text)
        assertEquals(Urgency.OK, GarageLogic.urgency(item(3_000), 11_500.0, now))
        // Soon: 10 % of the interval left (300 km).
        assertEquals(Urgency.SOON, GarageLogic.urgency(item(3_000), 12_750.0, now))
        assertEquals("Oil change in 250 km", GarageLogic.itemNote(item(3_000), 12_750.0, now).text)
        assertEquals(Urgency.DUE, GarageLogic.urgency(item(3_000), 13_000.0, now))
        assertEquals("Oil change due", GarageLogic.itemNote(item(3_000), 13_200.0, now).text)
        // At least 100 km warning for short intervals.
        assertEquals(Urgency.SOON, GarageLogic.urgency(item(600), 10_520.0, now))
    }

    @Test
    fun byMonthsWhicheverComesFirst() {
        // Done 1 April, every 6 months: due 1 October, so due now even with km to spare.
        val i = item(3_000, 6, lastAt = at(2026, 4, 1))
        assertEquals(Urgency.DUE, GarageLogic.urgency(i, 10_500.0, now))
        // Done 15 April: 6 days left, km far away: say the days.
        val j = item(3_000, 6, lastAt = at(2026, 4, 15))
        assertEquals(Urgency.SOON, GarageLogic.urgency(j, 10_500.0, now))
        assertEquals("Oil change in 6 days", GarageLogic.itemNote(j, 10_500.0, now).text)
        // Months only.
        assertEquals("Oil change in 5 months", GarageLogic.itemNote(item(0, 6, lastAt = at(2026, 9, 1)), 10_000.0, now).text)
    }

    @Test
    fun usedFraction() {
        assertEquals(0.5f, GarageLogic.used(item(3_000), 11_500.0, now), 0.001f)
        assertEquals(1f, GarageLogic.used(item(3_000), 20_000.0, now), 0.001f)
    }

    @Test
    fun documents() {
        assertNull(GarageLogic.docNote("Insurance", 0, now))
        assertEquals("Insurance ends in 12 days", GarageLogic.docNote("Insurance", at(2026, 10, 21, h = 8), now)?.text)
        assertEquals(Urgency.SOON, GarageLogic.docNote("Insurance", at(2026, 10, 21), now)?.urgency)
        assertEquals("PUC ends tomorrow", GarageLogic.docNote("PUC", at(2026, 10, 10, h = 23), now)?.text)
        assertEquals("PUC ends today", GarageLogic.docNote("PUC", at(2026, 10, 9, h = 1), now)?.text)
        assertEquals("PUC expired yesterday", GarageLogic.docNote("PUC", at(2026, 10, 8), now)?.text)
        assertEquals("Licence expired 3 days ago", GarageLogic.docNote("Licence", at(2026, 10, 6), now)?.text)
        assertEquals(Urgency.OK, GarageLogic.docNote("Insurance", at(2027, 3, 1), now)?.urgency)
    }

    @Test
    fun alertsMostUrgentFirstAndTheRideStartReminder() {
        val b = Bike("b", "Classic 350", odoKm = 13_100.0, items = listOf(item(600, lastKm = 12_550.0).copy(id = "c", name = "Chain clean & lube"), item(3_000)), pucUntilMs = at(2026, 10, 20))
        val g = Garage(listOf(b), "b")
        assertEquals(listOf("Oil change due", "Chain clean & lube in 50 km", "PUC ends in 11 days"), GarageLogic.alerts(g, now).map { it.text })
        assertEquals("Reminder: oil change is due on your Classic 350", GarageLogic.rideStartReminder(g, now))
        val papers = Garage(listOf(b.copy(items = emptyList(), insuranceUntilMs = at(2026, 10, 1))), "b")
        assertEquals("Reminder: Insurance expired 8 days ago", GarageLogic.rideStartReminder(papers, now))
        assertNull(GarageLogic.rideStartReminder(Garage(listOf(b.copy(items = emptyList()))), now))
    }

    @Test
    fun ridesAddToTheCurrentBike() {
        val b1 = Bike("1", "Classic", odoKm = 1_000.0)
        val b2 = Bike("2", "Duke", odoKm = 5_000.0)
        val g = GarageLogic.addRide(Garage(listOf(b1, b2), "2"), 42.36)
        assertEquals(5_042.3, g.bikes[1].odometer, 0.001)
        assertEquals(1_000.0, g.bikes[0].odometer, 0.0)
        val same = Garage(listOf(b1), "1")
        assertSame(same, GarageLogic.addRide(same, 0.1))
        assertEquals(0.0, GarageLogic.setOdometer(g.bikes[1], 6_000.0).addedKm, 0.0)
        val done = GarageLogic.done(g.bikes[1].copy(items = listOf(item(3_000))), "i", now)
        assertEquals(5_042.3, done.items[0].lastKm, 0.001)
        assertEquals(now, done.items[0].lastAtMs)
    }

    @Test
    fun documentReminderTimes() {
        val until = at(2026, 11, 20)
        val times = GarageLogic.reminderTimes(until, now)
        assertEquals(listOf(at(2026, 10, 21, h = 9), at(2026, 11, 13, h = 9), at(2026, 11, 20, h = 9)), times)
        assertEquals(2, GarageLogic.reminderTimes(until, at(2026, 10, 22)).size)
        assertEquals(emptyList<Long>(), GarageLogic.reminderTimes(0, now))
    }

    @Test
    fun jsonRoundTrip() {
        val b = GarageLogic.newBike("Classic 350", "MH12", 12_000.0, now).copy(addedKm = 80.5, insuranceUntilMs = at(2027, 1, 1))
        val g = Garage(listOf(b), b.id, licenceUntilMs = at(2030, 5, 5))
        assertEquals(g, GarageLogic.fromJson(GarageLogic.toJson(g)))
        assertEquals(Garage(), GarageLogic.fromJson(""))
        assertEquals(Garage(), GarageLogic.fromJson("""{"bikes":[{"name":"no id"}]}"""))
    }
}
