package com.ridecomm.app.audio

import kotlin.math.log10

/**
 * Lets the microphone through only while someone speaks, so riders don't hear each other's wind
 * and engine roar. Each chunk is split into a voice band (300–3400 Hz) and a low band (< 250 Hz):
 * wind and engines are loud mostly in the low band, speech in the voice band. The gate opens when
 * the voice band is loud enough *and* not much weaker than the low band, stays open briefly after
 * speech (so word endings aren't cut) and fades in and out to avoid clicks.
 *
 * Samples are floats in -1..1, processed in place.
 */
class NoiseGate(private val sampleRate: Int, var settings: GateSettings = GateSettings.MEDIUM) {

    private val voiceHigh = Biquad.highPass(300f, sampleRate)
    private val voiceLow = Biquad.lowPass(3400f, sampleRate)
    private val lowBand = Biquad.lowPass(250f, sampleRate)
    private val rampStep = 1f / (sampleRate * RAMP_MS / 1000f)

    /** True while voice is getting through. */
    var open = false
        private set
    /** Voice-band and low-band (wind, engine) levels the last decision was based on, in dBFS. */
    var voiceDb = -120f
        private set
    var lowDb = -120f
        private set
    private var voiceChunks = 0
    private var holdLeft = 0
    private var gain = 0f
    private var smoothVoice = 0.0
    private var smoothLow = 0.0

    fun process(samples: FloatArray, count: Int = samples.size) {
        if (count <= 0) return
        var voiceEnergy = 0.0
        var lowEnergy = 0.0
        for (i in 0 until count) {
            val s = samples[i]
            val v = voiceLow.filter(voiceHigh.filter(s))
            val l = lowBand.filter(s)
            voiceEnergy += v * v
            lowEnergy += l * l
        }
        // Smooth over ~30 ms: wind's low rumble swings a lot within a single 10 ms chunk.
        smoothVoice = smoothVoice * (1 - SMOOTHING) + (voiceEnergy / count) * SMOOTHING
        smoothLow = smoothLow * (1 - SMOOTHING) + (lowEnergy / count) * SMOOTHING
        voiceDb = db(smoothVoice)
        lowDb = db(smoothLow)
        val s = settings
        // Wind and engines are loud low down; speech is loud in the voice band.
        val voiceLike = voiceDb > s.thresholdDb && lowDb - voiceDb < s.rumbleAllowanceDb

        if (voiceLike) {
            voiceChunks++
            if (voiceChunks >= OPEN_AFTER_CHUNKS) {
                open = true
                holdLeft = sampleRate * s.holdMs / 1000
            }
        } else {
            voiceChunks = 0
            if (open) {
                holdLeft -= count
                if (holdLeft <= 0) open = false
            }
        }

        val target = if (open) 1f else s.floorGain
        for (i in 0 until count) {
            gain = when {
                gain < target -> (gain + rampStep).coerceAtMost(target)
                gain > target -> (gain - rampStep).coerceAtLeast(target)
                else -> gain
            }
            samples[i] *= gain
        }
    }

    private fun db(meanSquare: Double): Float = (10 * log10(meanSquare + 1e-12)).toFloat()

    private companion object {
        /** Three 10 ms chunks of voice in a row, so clicks and gusts don't open it. */
        const val OPEN_AFTER_CHUNKS = 3
        const val SMOOTHING = 0.35
        const val RAMP_MS = 5
    }
}
