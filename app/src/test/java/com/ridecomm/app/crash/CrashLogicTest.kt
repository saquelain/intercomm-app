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

    private val lat = 18.7546
    private val lon = 73.4062
    /** About [m] metres north of the start. */
    private fun north(m: Double) = lat + m / 111_000.0

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
    fun withoutGpsPickingThePhoneUpIsNotACrash() {
        val logic = CrashLogic()
        logic.onSample(1_000, 6 * g)
        assertEquals(CrashLogic.Phase.SETTLING, logic.phase)
        assertFalse(logic.feed(1_020, 4_000) { still() })
        assertEquals(CrashLogic.Phase.STILL, logic.phase)
        assertFalse(logic.feed(5_020, 10_000) { g + random.nextFloat() * 10f - 5f })
        assertEquals(CrashLogic.Phase.MOVED, logic.phase)
    }

    @Test
    fun withoutGpsADropCountsToo() {
        // No GPS: no way to tell riding from standing, so the hit gets the benefit of the doubt.
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
    fun aThrowOntoABedCounts() {
        // A quarter of a second in the air (real free fall), a 2.7 g landing on the bed.
        val logic = CrashLogic()
        assertFalse(logic.feed(0, 1_000) { still() })
        assertFalse(logic.feed(1_000, 260) { 0.15f * g })
        assertFalse(logic.onSample(1_260, 2.7f * g))
        assertTrue(logic.byFall)
        assertTrue(logic.feed(1_280, 14_000) { still() })
    }

    @Test
    fun aSwingingHandIsNotAFall() {
        // Walking with the phone in hand: brief dips to 0.4 g and 2.2 g steps. The old rule (0.5 g, 2 g) fired.
        val logic = CrashLogic()
        var t = 0L
        repeat(20) {
            logic.feed(t, 150) { 0.4f * g }
            logic.onSample(t + 150, 2.2f * g)
            t += 600
        }
        assertEquals(CrashLogic.Phase.WAITING, logic.phase)
    }

    @Test
    fun withFallsOffOnlyAHardHitCounts() {
        val logic = CrashLogic.tuned(CrashTuning(falls = false))
        logic.feed(1_000, 300) { 0.1f * g }
        logic.onSample(1_300, 3f * g)
        assertEquals(CrashLogic.Phase.WAITING, logic.phase)
    }

    @Test
    fun walkingAboutOnARideIsIgnored() {
        // At a tea stop, GPS shows walking pace for minutes; the phone bangs on a table (6 g).
        val logic = CrashLogic()
        var t = 0L
        while (t < 120_000) {
            logic.onLocation(t, north(t / 1_000 * 1.2), lon, 6f, 1.2f)
            t += 2_000
        }
        logic.onSample(120_500, 6 * g)
        assertEquals(CrashLogic.Phase.WAITING, logic.phase)
        // The test screen skips this check.
        val test = CrashLogic(requireRiding = false)
        test.onLocation(119_000, lat, lon, 6f, 1.2f)
        test.onSample(120_500, 6 * g)
        assertEquals(CrashLogic.Phase.SETTLING, test.phase)
    }

    @Test
    fun riderGetsUpAndWalksAroundButTheBikeStaysIsACrash() {
        val logic = CrashLogic()
        logic.onLocation(9_000, lat, lon, 8f, 12f) // riding at 43 km/h
        logic.onSample(10_000, 7 * g) // the crash
        // Up on their feet, phone in hand (shaking), walking about near the bike.
        var fired = false
        var t = 10_020L
        while (t < 24_000) {
            if (logic.onSample(t, g + random.nextFloat() * 8f - 4f)) fired = true
            if (t % 2_000 == 20L) logic.onLocation(t, north(((t - 10_000) / 1_000) * 0.8), lon, 6f, 1.1f)
            t += 20
        }
        assertTrue(fired)
        assertEquals(CrashLogic.Reason.STOPPED, logic.reason)
    }

    @Test
    fun aPotholeAndRidingOnIsNotACrash() {
        val logic = CrashLogic()
        logic.onLocation(9_000, lat, lon, 8f, 14f)
        logic.onSample(10_000, 5 * g)
        var fired = false
        var t = 10_020L
        while (t < 24_000) {
            if (logic.onSample(t, still())) fired = true // even a phone that reads calm on the mount
            if (t % 2_000 == 20L) logic.onLocation(t, north((t - 9_000) / 1_000 * 14.0), lon, 6f, 14f)
            t += 20
        }
        assertFalse(fired)
        assertEquals(CrashLogic.Phase.MOVED, logic.phase)
    }

    @Test
    fun ridingOffSlowlyWithoutASpeedReadingIsNotACrash() {
        // No speed from GPS, but 60 m away within the window: rode on.
        val logic = CrashLogic()
        logic.onLocation(8_500, north(-10.0), lon, 8f, null)
        logic.onLocation(9_500, lat, lon, 8f, null) // 10 m in a second: riding
        logic.onSample(10_000, 5 * g)
        logic.onLocation(13_000, north(20.0), lon, 8f, null)
        logic.onLocation(17_000, north(60.0), lon, 8f, null)
        assertFalse(logic.feed(10_020, 14_000) { still() })
        assertEquals(CrashLogic.Phase.MOVED, logic.phase)
    }

    @Test
    fun wanderingIndoorGpsIsIgnored() {
        // Poor fixes (60 m accuracy) jumping about: ignored, so the phone's stillness decides.
        val logic = CrashLogic()
        logic.onSample(10_000, 6 * g)
        logic.onLocation(13_000, lat, lon, 60f, 4f)
        logic.onLocation(15_000, north(120.0), lon, 60f, 5f)
        assertTrue(logic.feed(10_020, 14_000) { still() })
        assertEquals(CrashLogic.Reason.STILL, logic.reason)
    }

    // ---- The rider's own numbers (test screen → Fine-tune) ----

    @Test
    fun aLowerHitStrengthCatchesASofterHit() {
        val usual = CrashLogic()
        val soft = CrashLogic.tuned(CrashTuning(g = 2.5f))
        for (l in listOf(usual, soft)) l.onSample(1_000, 3 * g)
        assertFalse(usual.feed(1_020, 14_000) { still() })
        assertTrue(soft.feed(1_020, 14_000) { still() })
    }

    @Test
    fun aShorterWatchDecidesSooner() {
        val quick = CrashLogic.tuned(CrashTuning(waitS = 5))
        quick.onSample(1_000, 6 * g)
        // 2 s to settle + 5 s watching: decided by 8.1 s, well before the usual 13 s.
        assertTrue(quick.feed(1_020, 7_100) { still() })
    }

    @Test
    fun aLargerDistanceStillCountsAsStopped() {
        // 80 m from the crash spot: "rode on" with the usual 50 m, "stayed put" with 120 m.
        fun run(t: CrashTuning): Boolean {
            val l = CrashLogic.tuned(t)
            l.onLocation(9_500, lat, lon, 8f, 8f)
            l.onSample(10_000, 6 * g)
            l.onLocation(14_000, north(80.0), lon, 8f, 1f)
            return l.feed(10_020, 14_000) { still() }
        }
        assertFalse(run(CrashTuning()))
        assertTrue(run(CrashTuning(moveM = 120)))
    }

    @Test
    fun storedNumbersAreKeptSensible() {
        assertEquals(CrashTuning(2f, 60, 500), CrashTuning(0.5f, 99, 5_000).safe())
        assertTrue(CrashTuning().isDefault)
    }
}
