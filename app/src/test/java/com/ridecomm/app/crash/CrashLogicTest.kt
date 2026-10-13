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
        assertFalse(logic.feed(0, 10_000) { riding() })
        assertFalse(logic.onSample(10_000, 6 * g)) // hard hit
        assertTrue(logic.feed(10_020, 14_000) { still() })
    }

    @Test
    fun impactButKeepsRidingIsNotACrash() {
        val logic = CrashLogic()
        logic.onSample(10_000, 5 * g) // pothole
        assertFalse(logic.feed(10_020, 14_000) { riding() })
    }

    @Test
    fun bumpsBelowThresholdAreIgnored() {
        val logic = CrashLogic()
        logic.onSample(10_000, 3 * g)
        assertFalse(logic.feed(10_020, 14_000) { still() })
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
        val logic = CrashLogic()
        assertFalse(logic.feed(0, 2_000) { still() })
        assertFalse(logic.feed(2_000, 300) { 0.05f * g })
        assertFalse(logic.onSample(2_300, 3 * g))
        assertTrue(logic.feed(2_320, 14_000) { still() })
        assertEquals(CrashLogic.Phase.CRASH, logic.phase)
    }

    @Test
    fun aSoftBumpWithoutAFallIsIgnored() {
        val logic = CrashLogic()
        logic.onSample(1_000, 3 * g)
        assertFalse(logic.feed(1_020, 14_000) { still() })
        assertEquals(CrashLogic.Phase.WAITING, logic.phase)
    }

    @Test
    fun pickingThePhoneUpEndsTheWatchEarly() {
        val logic = CrashLogic()
        logic.onSample(1_000, 6 * g)
        assertEquals(CrashLogic.Phase.SETTLING, logic.phase)
        assertFalse(logic.feed(1_020, 4_000) { still() })
        assertEquals(CrashLogic.Phase.STILL, logic.phase)
        assertFalse(logic.feed(5_020, 2_000) { g + random.nextFloat() * 10f - 5f })
        assertEquals(CrashLogic.Phase.MOVED, logic.phase)
    }


    @Test
    fun aDropAtAStopNowCountsToo() {
        // No speed check any more: GPS speed kept real tests from firing. The rider cancels the countdown.
        val logic = CrashLogic()
        logic.onSample(60_000, 6 * g)
        assertTrue(logic.feed(60_020, 14_000) { still() })
    }

    @Test
    fun aSlowSensorWithTheScreenOffStillDecides() {
        // Some phones send only a few readings a second with the screen off: 4 a second here.
        val logic = CrashLogic()
        logic.onSample(1_000, 6 * g)
        var fired = false
        var t = 1_250L
        while (t < 15_000) {
            if (logic.onSample(t, still())) fired = true
            t += 250
        }
        assertTrue(fired)
    }

    @Test
    fun aSpinningThrowOntoABedCounts() {
        // In the air the phone spins, so it reads a little above zero; a bed gives a soft 2.2 g landing.
        val logic = CrashLogic()
        assertFalse(logic.feed(0, 1_000) { still() })
        assertFalse(logic.feed(1_000, 200) { 0.4f * g })
        assertFalse(logic.onSample(1_200, 2.2f * g))
        assertTrue(logic.feed(1_220, 14_000) { still() })
    }
}
