package com.ridecomm.app.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CrashLogicTest {

    private val g = CrashLogic.GRAVITY
    private val random = Random(42)

    /** Feeds readings at 50 Hz from [fromMs] for [durationMs]; returns true if a crash fired. */
    private fun CrashLogic.feed(fromMs: Long, durationMs: Long, reading: (Long) -> Float): Boolean {
        var fired = false
        var t = fromMs
        while (t < fromMs + durationMs) {
            if (onSample(t, reading(t))) fired = true
            t += 20
        }
        return fired
    }

    private fun riding() = g + random.nextFloat() * 6f - 3f // road vibration, ±3 m/s²
    private fun still() = g + random.nextFloat() * 0.2f - 0.1f

    @Test
    fun impactThenLyingStillIsACrash() {
        val logic = CrashLogic()
        logic.onSpeed(0, 15f)
        assertFalse(logic.feed(0, 10_000) { riding() })
        assertFalse(logic.onSample(10_000, 6 * g)) // hard hit
        logic.onSpeed(11_000, 0f)
        assertTrue(logic.feed(10_020, 14_000) { still() })
    }

    @Test
    fun impactButKeepsRidingIsNotACrash() {
        val logic = CrashLogic()
        logic.onSpeed(0, 15f)
        logic.onSample(10_000, 5 * g) // pothole
        assertFalse(logic.feed(10_020, 14_000) { riding() })
    }

    @Test
    fun bumpsBelowThresholdAreIgnored() {
        val logic = CrashLogic()
        logic.onSpeed(0, 15f)
        logic.onSample(10_000, 3 * g)
        assertFalse(logic.feed(10_020, 14_000) { still() })
    }

    @Test
    fun phoneDroppedWhileStoppedIsIgnored() {
        val logic = CrashLogic()
        logic.onSpeed(0, 0f) // parked for a while
        logic.onSpeed(55_000, 0f)
        logic.onSample(60_000, 6 * g)
        assertFalse(logic.feed(60_020, 14_000) { still() })
    }

    @Test
    fun withoutGpsImpactAndStillnessStillCount() {
        val logic = CrashLogic()
        logic.onSample(10_000, 6 * g)
        assertTrue(logic.feed(10_020, 14_000) { still() })
    }

    @Test
    fun cancelledAlarmMutesNewImpactsForAMinute() {
        val logic = CrashLogic()
        logic.cancelled(0)
        logic.onSample(10_000, 6 * g)
        assertFalse(logic.feed(10_020, 14_000) { still() })
    }

    @Test
    fun aFallThenASoftLandingCounts() {
        // Thrown onto a bed: about 0.3 s in the air, a 3 g landing, then still.
        val logic = CrashLogic(requireRiding = false)
        assertFalse(logic.feed(0, 2_000) { still() })
        assertFalse(logic.feed(2_000, 300) { 0.05f * g })
        assertFalse(logic.onSample(2_300, 3 * g))
        assertTrue(logic.feed(2_320, 14_000) { still() })
        assertEquals(CrashLogic.Phase.CRASH, logic.phase)
    }

    @Test
    fun aSoftBumpWithoutAFallIsIgnored() {
        val logic = CrashLogic(requireRiding = false)
        logic.onSample(1_000, 3 * g)
        assertFalse(logic.feed(1_020, 14_000) { still() })
        assertEquals(CrashLogic.Phase.WAITING, logic.phase)
    }

    @Test
    fun pickingThePhoneUpEndsTheWatchEarly() {
        val logic = CrashLogic(requireRiding = false)
        logic.onSample(1_000, 6 * g)
        assertEquals(CrashLogic.Phase.SETTLING, logic.phase)
        assertFalse(logic.feed(1_020, 4_000) { still() })
        assertEquals(CrashLogic.Phase.STILL, logic.phase)
        assertFalse(logic.feed(5_020, 2_000) { g + random.nextFloat() * 10f - 5f })
        assertEquals(CrashLogic.Phase.MOVED, logic.phase)
    }

    @Test
    fun theTestSkipsTheSpeedCheck() {
        // At home: GPS says standing still, which on a ride means "dropped at a stop".
        val ride = CrashLogic()
        val test = CrashLogic(requireRiding = false)
        for (l in listOf(ride, test)) {
            l.onSpeed(0, 0f)
            l.onSpeed(9_000, 0f)
            l.onSample(10_000, 6 * g)
        }
        assertFalse(ride.feed(10_020, 14_000) { still() })
        assertTrue(test.feed(10_020, 14_000) { still() })
        assertEquals(6f, test.peakG, 0.01f)
    }

    @Test
    fun aSlowSpillStillCounts() {
        // 12 km/h a few seconds before the crash (the old rule needed 15 km/h).
        val logic = CrashLogic()
        logic.onSpeed(0, 3.4f)
        logic.onSpeed(3_000, 3.3f)
        logic.onSample(5_000, 5 * g)
        logic.onSpeed(6_000, 0f)
        assertTrue(logic.feed(5_020, 14_000) { still() })
    }
}
