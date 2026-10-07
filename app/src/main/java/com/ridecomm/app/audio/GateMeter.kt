package com.ridecomm.app.audio

import kotlin.math.log10

/** What the wind filter is doing right now. */
enum class GateStatus {
    /** Voice detected: the mic goes to the group. */
    VOICE,
    /** Sound that isn't voice (wind, engine, traffic): blocked. */
    NOISE,
    /** Nothing much to block. */
    QUIET,
}

/** One meter reading (every [GateMeter.FRAME_MS]). Levels in dBFS. */
data class GateFrame(
    /** What the mic picked up. */
    val micDb: Float,
    /** What was sent to the group. */
    val sentDb: Float,
    /** Speech-band level, compared with [thresholdDb]. */
    val voiceDb: Float,
    /** Low rumble level: wind and engine live here. */
    val lowDb: Float,
    /** Voice must be louder than this to get through; null when the filter is off. */
    val thresholdDb: Float?,
    val status: GateStatus,
)

/** Running totals: how long voice was sent and noise was blocked, and how much quieter the noise got. */
data class GateTotals(
    val voiceMs: Long = 0,
    val blockedMs: Long = 0,
    val quietMs: Long = 0,
    /** Mic level while blocking noise, and what went out meanwhile (energy averages, dBFS). */
    val noiseInDb: Float? = null,
    val noiseOutDb: Float? = null,
) {
    /** How much the blocked noise was turned down, in dB (bigger = quieter). */
    val noiseCutDb: Float? get() = if (noiseInDb != null && noiseOutDb != null) noiseInDb - noiseOutDb else null
}

/**
 * Measures the mic before and after the [NoiseGate], for the meters on screen. Feed every chunk to
 * [beforeGate] and [afterGate]; every [FRAME_MS] [afterGate] returns a [GateFrame].
 */
class GateMeter(sampleRate: Int) {
    private val frameSamples = sampleRate * FRAME_MS / 1000
    private var micEnergy = 0.0
    private var sentEnergy = 0.0
    private var samples = 0

    /** The gate let something through somewhere in this frame (or just before it). */
    private var openInFrame = false
    private var wasOpen = false

    private var noiseIn = 0.0
    private var noiseOut = 0.0
    private var noiseFrames = 0

    var totals = GateTotals()
        private set

    fun beforeGate(chunk: FloatArray, count: Int) {
        for (i in 0 until count) micEnergy += chunk[i] * chunk[i]
    }

    /** [gate] is null when the filter is off (everything is sent). */
    fun afterGate(chunk: FloatArray, count: Int, gate: NoiseGate?): GateFrame? {
        for (i in 0 until count) sentEnergy += chunk[i] * chunk[i]
        samples += count
        val openNow = gate == null || gate.open
        // A chunk that closes the gate still starts open (it fades out), so count the one before too.
        openInFrame = openInFrame || openNow || wasOpen
        wasOpen = openNow
        if (samples < frameSamples) return null

        val mic = micEnergy / samples
        val sent = sentEnergy / samples
        val ms = samples * FRAME_MS.toLong() / frameSamples
        micEnergy = 0.0
        sentEnergy = 0.0
        samples = 0
        val fullyBlocked = !openInFrame
        openInFrame = false

        val micDb = db(mic)
        val status = when {
            gate == null || gate.open -> if (micDb > QUIET_DB) GateStatus.VOICE else GateStatus.QUIET
            micDb > QUIET_DB -> GateStatus.NOISE
            else -> GateStatus.QUIET
        }
        totals = when (status) {
            GateStatus.VOICE -> totals.copy(voiceMs = totals.voiceMs + ms)
            GateStatus.QUIET -> totals.copy(quietMs = totals.quietMs + ms)
            GateStatus.NOISE -> if (!fullyBlocked) {
                // Noise right as the gate closes: blocked from here on, but too mixed to measure.
                totals.copy(blockedMs = totals.blockedMs + ms)
            } else {
                noiseIn += mic
                noiseOut += sent
                noiseFrames++
                totals.copy(
                    blockedMs = totals.blockedMs + ms,
                    noiseInDb = db(noiseIn / noiseFrames),
                    noiseOutDb = db(noiseOut / noiseFrames),
                )
            }
        }
        return GateFrame(
            micDb = micDb,
            sentDb = db(sent),
            voiceDb = gate?.voiceDb ?: micDb,
            lowDb = gate?.lowDb ?: micDb,
            thresholdDb = gate?.sensitivity?.thresholdDb,
            status = status,
        )
    }

    companion object {
        const val FRAME_MS = 50
        /** Below this the mic is practically silent: nothing to block. */
        const val QUIET_DB = -55f
        const val FLOOR_DB = -90f

        fun db(meanSquare: Double): Float = (10 * log10(meanSquare + 1e-12)).toFloat().coerceAtLeast(FLOOR_DB)
    }
}

/** A recorded clip run through the filter: what was sent, plus the meter frames along the way. */
class GateAnalysis(val filtered: FloatArray, val frames: List<GateFrame>, val totals: GateTotals) {
    companion object {
        /** [sensitivity] null = filter off. */
        fun run(clip: FloatArray, sampleRate: Int, sensitivity: NoiseGate.Sensitivity?): GateAnalysis {
            val out = clip.copyOf()
            val gate = sensitivity?.let { NoiseGate(sampleRate, it) }
            val meter = GateMeter(sampleRate)
            val frames = mutableListOf<GateFrame>()
            val chunkSize = sampleRate / 100
            val chunk = FloatArray(chunkSize)
            var at = 0
            while (at < out.size) {
                val n = minOf(chunkSize, out.size - at)
                out.copyInto(chunk, 0, at, at + n)
                meter.beforeGate(chunk, n)
                gate?.process(chunk, n)
                chunk.copyInto(out, at, 0, n)
                meter.afterGate(chunk, n, gate)?.let { frames += it }
                at += n
            }
            return GateAnalysis(out, frames, meter.totals)
        }
    }
}
