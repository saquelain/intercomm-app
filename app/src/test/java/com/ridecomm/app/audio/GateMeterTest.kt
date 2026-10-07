package com.ridecomm.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class GateMeterTest {
    private val rate = 48_000
    private val random = Random(11)
    private val windFilter = Biquad.lowPass(150f, rate)

    private fun voice(t: Int) = (0.2 * sin(2 * PI * 660 * t / rate) + 0.15 * sin(2 * PI * 1500 * t / rate)).toFloat()
    private fun wind() = windFilter.filter(0.8f * (random.nextFloat() * 2 - 1)) * 3f

    /** 1 s quiet, 2 s wind, 1 s voice. */
    private val clip = FloatArray(rate * 4) { t ->
        when {
            t < rate -> 0f
            t < rate * 3 -> wind()
            else -> voice(t)
        }
    }

    @Test
    fun windIsBlockedAndVoiceSent() {
        val a = GateAnalysis.run(clip, rate, NoiseGate.Sensitivity.MEDIUM)
        assertEquals(80, a.frames.size) // 4 s / 50 ms
        val t = a.totals
        assertTrue("quiet ${t.quietMs}", t.quietMs in 900..1_100)
        assertTrue("blocked ${t.blockedMs}", t.blockedMs in 1_900..2_100)
        assertTrue("voice ${t.voiceMs}", t.voiceMs in 800..1_000)
        assertTrue("noise cut ${t.noiseCutDb}", t.noiseCutDb!! > 40f)
        // The wind part of the clip is silenced, the voice part kept.
        assertTrue(a.filtered.slice(rate * 3 / 2 until rate * 5 / 2).all { kotlin.math.abs(it) < 0.001f })
        assertTrue(a.filtered.slice(rate * 7 / 2 until rate * 4).any { kotlin.math.abs(it) > 0.2f })
        val windFrame = a.frames[40]
        assertEquals(GateStatus.NOISE, windFrame.status)
        assertTrue(windFrame.lowDb > windFrame.voiceDb)
        assertEquals(GateStatus.VOICE, a.frames[75].status)
        assertEquals(-42f, a.frames[75].thresholdDb)
    }

    @Test
    fun filterOffSendsEverything() {
        val a = GateAnalysis.run(clip, rate, null)
        assertEquals(0L, a.totals.blockedMs)
        assertNull(a.totals.noiseCutDb)
        assertTrue(a.filtered.contentEquals(clip))
        assertNull(a.frames[40].thresholdDb)
    }

    @Test
    fun noiseCutIgnoresTheMomentTheGateCloses() {
        // Voice straight into wind: the gate holds open briefly, then shuts.
        val clip = FloatArray(rate * 4) { t -> if (t < rate) voice(t) else wind() }
        val t = GateAnalysis.run(clip, rate, NoiseGate.Sensitivity.MEDIUM).totals
        assertTrue("blocked ${t.blockedMs}", t.blockedMs >= 2_300)
        assertTrue("noise cut ${t.noiseCutDb}", t.noiseCutDb!! > 40f)
    }
}
