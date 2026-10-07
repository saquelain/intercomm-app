package com.ridecomm.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertLogicTest {

    @Test
    fun joinDropAndBack() {
        val p = Presence()
        assertEquals(PresenceEvent.JOINED, p.joined("a"))
        assertEquals(PresenceEvent.DROPPED, p.left("a", 1_000))
        assertEquals(PresenceEvent.BACK, p.joined("a"))
    }

    @Test
    fun byeMeansLeft() {
        val p = Presence()
        p.known("a")
        p.bye("a", 1_000)
        assertEquals(PresenceEvent.LEFT, p.left("a", 2_000))
        // Rejoining after leaving on purpose is a plain join.
        assertEquals(PresenceEvent.JOINED, p.joined("a"))
    }

    @Test
    fun oldByeDoesNotCount() {
        val p = Presence()
        p.bye("a", 0)
        assertEquals(PresenceEvent.DROPPED, p.left("a", Presence.BYE_VALID_MS + 1))
    }

    @Test
    fun batteryAnnouncesEachThresholdOnce() {
        val w = BatteryWatch()
        assertNull(w.update(40, charging = false))
        assertNull(w.update(21, charging = false))
        assertEquals(20, w.update(20, charging = false))
        assertNull(w.update(19, charging = false))
        assertNull(w.update(15, charging = false))
        assertEquals(10, w.update(10, charging = false))
        assertEquals(4, w.update(4, charging = false))
        assertNull(w.update(3, charging = false))
    }

    @Test
    fun batteryFirstReportAlreadyLow() {
        val w = BatteryWatch()
        assertEquals(15, w.update(15, charging = false))
        assertNull(w.update(14, charging = false))
    }

    @Test
    fun chargingResets() {
        val w = BatteryWatch()
        assertEquals(18, w.update(18, charging = false))
        assertNull(w.update(18, charging = true))
        assertEquals(17, w.update(17, charging = false)) // unplugged while still low
        assertNull(w.update(30, charging = false))
        assertEquals(20, w.update(20, charging = false))
    }

    @Test
    fun spokenLines() {
        assertEquals("Rahul dropped out. Probably no signal.", Presence.spoken("Rahul", PresenceEvent.DROPPED))
        assertEquals("Amit's phone battery is at 15 percent", BatteryWatch.spoken("Amit", 15))
        assertEquals("Your phone battery is at 9 percent", BatteryWatch.spoken(null, 9))
    }
}
