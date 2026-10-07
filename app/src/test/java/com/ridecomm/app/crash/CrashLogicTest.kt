package com.ridecomm.app.crash

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
}
