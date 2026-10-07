package com.ridecomm.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class NoiseGateTest {
    private val rate = 48_000
    private val chunk = 480 // 10 ms
    private val random = Random(7)

    /** Speech-like: a few harmonics in the voice band. */
    private fun voice(t: Int, amplitude: Float): Float {
        val s = t.toDouble() / rate
        return (amplitude * (sin(2 * PI * 220 * s) * 0.3 + sin(2 * PI * 660 * s) * 0.5 + sin(2 * PI * 1500 * s) * 0.4)).toFloat()
    }

    /** Wind-like: loud noise with most energy at low frequencies (noise through a 150 Hz low-pass). */
    private val windFilter = Biquad.lowPass(150f, rate)
    private fun wind(amplitude: Float) = windFilter.filter(amplitude * (random.nextFloat() * 2 - 1)) * 3f

    private fun run(gate: NoiseGate, ms: Int, sample: (Int) -> Float): Pair<Boolean, Float> {
        var t = 0
        var openedAny = false
        var lastPeak = 0f
        repeat(ms / 10) {
            val buf = FloatArray(chunk) { sample(t + it) }
            t += chunk
            gate.process(buf)
            if (gate.open) openedAny = true
            lastPeak = buf.maxOf { kotlin.math.abs(it) }
        }
        return openedAny to lastPeak
    }

    @Test
    fun speechOpensTheGate() {
        val (opened, peak) = run(NoiseGate(rate), 300) { voice(it, 0.2f) }
        assertTrue(opened)
        assertTrue("speech passes at full volume", peak > 0.15f)
    }

    @Test
    fun windAloneStaysClosed() {
        val (opened, peak) = run(NoiseGate(rate), 2_000) { wind(0.8f) }
        assertFalse("wind must not open the gate", opened)
        assertTrue("wind is silenced", peak < 0.001f)
    }

    @Test
    fun speechOverWindStillGetsThrough() {
        val (opened, _) = run(NoiseGate(rate), 500) { voice(it, 0.25f) + wind(0.3f) }
        assertTrue(opened)
    }

    @Test
    fun quietRoomStaysClosed() {
        val (opened, _) = run(NoiseGate(rate), 1_000) { (random.nextFloat() * 2 - 1) * 0.0005f }
        assertFalse(opened)
    }

    @Test
    fun closesShortlyAfterSpeechEnds() {
        val gate = NoiseGate(rate)
        run(gate, 300) { voice(it, 0.2f) }
        assertTrue(gate.open)
        run(gate, 300) { 0f }
        assertTrue("held open briefly so word endings aren't cut", gate.open)
        run(gate, 400) { 0f }
        assertFalse(gate.open)
    }

    @Test
    fun lowSensitivityIgnoresQuietVoices() {
        val (opened, _) = run(NoiseGate(rate, GateSettings.Preset.LOW.settings), 300) { voice(it, 0.01f) }
        assertFalse(opened)
        val (openedHigh, _) = run(NoiseGate(rate, GateSettings.Preset.HIGH.settings), 300) { voice(it, 0.01f) }
        assertTrue(openedHigh)
    }

    @Test
    fun partialReductionTurnsNoiseDownInsteadOfMuting() {
        // 20 dB quieter = a tenth of the level.
        val (opened, peak) = run(NoiseGate(rate, GateSettings(reductionDb = 20f)), 1_000) { 0.5f * kotlin.math.sin(2 * PI * 100 * it / rate).toFloat() }
        assertFalse(opened)
        assertTrue("peak $peak", peak in 0.04f..0.06f)
        assertEquals(0f, GateSettings(reductionDb = GateSettings.SILENCE_DB).floorGain)
    }

    @Test
    fun holdTimeIsAdjustable() {
        val gate = NoiseGate(rate, GateSettings(holdMs = 1_200))
        run(gate, 300) { voice(it, 0.2f) }
        run(gate, 1_000) { 0f }
        assertTrue("still open at 1 s of 1.2 s", gate.open)
        // The voice level takes ~0.1 s to fade below the threshold before the hold starts.
        run(gate, 500) { 0f }
        assertFalse(gate.open)
    }

    @Test
    fun strongWindRejectionBlocksVoiceDrownedInWind() {
        val speechOverWind = { t: Int -> voice(t, 0.25f) + wind(0.6f) }
        val (normal, _) = run(NoiseGate(rate), 500, speechOverWind)
        assertTrue(normal)
        val (strict, _) = run(NoiseGate(rate, GateSettings(rumbleAllowanceDb = GateSettings.RUMBLE_MIN)), 500, speechOverWind)
        assertFalse(strict)
    }

    @Test
    fun presetsAreRecognised() {
        assertEquals(GateSettings.Preset.MEDIUM, GateSettings().preset)
        assertEquals(null, GateSettings(holdMs = 700).preset)
    }
}
