package com.ridecomm.app.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TripLogicTest {
    /** Metres of latitude per degree. */
    private val degPerM = 1 / 111_195.0

    @Test
    fun countsDistanceAndSpeedWhileRiding() {
        val t = TripMeter()
        // 60 s north at 20 m/s (72 km/h), a fix per second.
        for (s in 0..60) t.add(Fix(s * 1000L, 12.9 + s * 20 * degPerM, 77.6, 5f, 20f))
        assertEquals(1_200.0, t.distanceM, 30.0)
        assertEquals(72f, t.speedKmh!!, 0.5f)
        assertEquals(72f, t.topKmh, 0.5f)
        assertEquals(60_000L, t.movingMs)
        assertEquals(72f, t.averageKmh, 2f)
    }

    @Test
    fun standingStillAddsNothing() {
        val t = TripMeter()
        val rnd = Random(1)
        // 10 minutes parked, GPS wandering a few metres.
        for (s in 0..600) {
            t.add(Fix(s * 1000L, 12.9 + (rnd.nextDouble() * 8 - 4) * degPerM, 77.6 + (rnd.nextDouble() * 8 - 4) * degPerM, 8f, 0.3f))
        }
        assertTrue("distance ${t.distanceM}", t.distanceM < 15)
        assertEquals(0L, t.movingMs)
    }

    @Test
    fun ignoresJumpsAndBadFixes() {
        val t = TripMeter()
        for (s in 0..10) t.add(Fix(s * 1000L, 12.9 + s * 15 * degPerM, 77.6, 5f, 15f))
        val before = t.distanceM
        t.add(Fix(11_000, 13.9, 77.6, 5f, null)) // 100 km in a second
        t.add(Fix(12_000, 12.9 + 11 * 15 * degPerM, 77.6, 80f, 15f)) // too inaccurate
        assertEquals(before, t.distanceM, 0.01)
        // A single crazy speed reading doesn't become the top speed.
        t.add(Fix(13_000, 12.9 + 12 * 15 * degPerM, 77.6, 5f, 70f))
        t.add(Fix(14_000, 12.9 + 13 * 15 * degPerM, 77.6, 5f, 15f))
        assertTrue("top ${t.topKmh}", t.topKmh < 60f)
    }

    @Test
    fun speedWarningNeedsAFewSecondsAndRepeatsSlowly() {
        val w = SpeedWatch(90)
        assertNull(w.update(0, 95f))
        assertNull(w.update(2_000, 95f))
        assertEquals(96, w.update(3_000, 96f))
        assertNull(w.update(10_000, 97f))
        assertEquals(98, w.update(33_000, 98f))
        // Easing off just under the limit doesn't reset; well under does.
        assertNull(w.update(34_000, 88f))
        assertNull(w.update(35_000, 80f))
        assertNull(w.update(36_000, 95f))
        assertEquals(95, w.update(39_000, 95f))
    }

    @Test
    fun speedWarningOffOrNoGps() {
        assertNull(SpeedWatch(0).update(10_000, 150f))
        assertNull(SpeedWatch(90).update(10_000, null))
    }

    @Test
    fun updatesFireEachBlock() {
        val time = UpdateSchedule(UpdateEvery.MIN_15)
        assertFalse(time.due(14 * 60_000L, 0.0))
        assertTrue(time.due(15 * 60_000L, 0.0))
        assertFalse(time.due(20 * 60_000L, 0.0))
        assertTrue(time.due(31 * 60_000L, 0.0))
        val dist = UpdateSchedule(UpdateEvery.KM_10)
        assertFalse(dist.due(0, 9_900.0))
        assertTrue(dist.due(0, 10_050.0))
        assertFalse(dist.due(0, 15_000.0))
        assertFalse(UpdateSchedule(UpdateEvery.OFF).due(99 * 60_000L, 99_000.0))
        // Switching on mid-ride: next update at the next block, not right away.
        val late = UpdateSchedule(UpdateEvery.MIN_30)
        late.restart(95 * 60_000L, 0.0)
        assertFalse(late.due(100 * 60_000L, 0.0))
        assertTrue(late.due(120 * 60_000L, 0.0))
    }

    @Test
    fun spokenUpdate() {
        assertEquals("42.3 kilometres. 1 hour 10 minutes riding. Average 56.", TripSpeech.update(42_300.0, 70 * 60_000L, 56.2f))
        assertEquals("800 metres. 4 minutes riding.", TripSpeech.update(803.0, 4 * 60_000L, 0f))
        assertEquals("10 kilometres", TripSpeech.distance(10_000.0))
        assertEquals("2 hours", TripSpeech.duration(120 * 60_000L))
        assertEquals("1 minute", TripSpeech.duration(60_000L))
    }
}
