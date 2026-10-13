package com.ridecomm.app.crash

import kotlin.math.sqrt

/**
 * Decides from accelerometer readings (and speed, when known) whether the rider has crashed:
 *
 * 1. **Impact**: total acceleration above [impactG] (a hard hit; road bumps on a phone mount stay
 *    well below this), or a **fall**: the phone in free fall (almost no acceleration) for
 *    [freeFallMs], then a landing above [landingG] within a second (a phone flying out of a pocket
 *    or off the mount, when the hit itself is soft).
 * 2. **Was riding**: if GPS speed is known, the rider must have been moving faster than
 *    [movingSpeedMs] shortly before the impact, so a phone dropped at a stop doesn't count.
 *    [requireRiding] = false skips this (the test in Settings, at home).
 * 3. **No movement**: after a [settleMs] pause for the bike/phone to come to rest, the phone stays
 *    almost perfectly still for [stillWindowMs] (riding, walking or picking the phone up shake it).
 *
 * Plain Kotlin with timestamps passed in, so it can be tested without a phone. [phase] and
 * [peakG] say what it is doing, for the test screen.
 */
class CrashLogic(
    private val impactG: Float = IMPACT_G,
    private val landingG: Float = 2.5f,
    private val freeFallG: Float = 0.35f,
    private val freeFallMs: Long = 180,
    private val settleMs: Long = 2_000,
    private val stillWindowMs: Long = 10_000,
    /** Std-dev of acceleration (m/s²) below which the phone counts as still. */
    private val stillStdDev: Float = 0.8f,
    private val movingSpeedMs: Float = 2.8f, // 10 km/h
    private val stoppedSpeedMs: Float = 2.0f,
    private val cooldownMs: Long = 60_000,
    private val requireRiding: Boolean = true,
) {
    enum class Phase { WAITING, SETTLING, STILL, MOVED, CRASH }

    /** What the logic is doing now. */
    var phase = Phase.WAITING
        private set

    /** The hardest hit felt so far, in g. */
    var peakG = 0f
        private set

    /** The biggest hit that started the current watch, in g. */
    var impactPeakG = 0f
        private set

    private var impactAt: Long? = null
    private var count = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var cooldownUntil = 0L

    private var freeFallSince: Long? = null
    private var fellAt = Long.MIN_VALUE / 2

    private var lastSpeed: Float? = null
    private var lastSpeedAt = 0L
    /** Last time the rider was clearly moving. */
    private var lastMovingAt = Long.MIN_VALUE / 2

    fun onSpeed(tMs: Long, speedMs: Float) {
        lastSpeed = speedMs
        lastSpeedAt = tMs
        if (speedMs >= movingSpeedMs) lastMovingAt = tMs
    }

    /** How far through the still window, 0..1 (only meaningful in [Phase.STILL]). */
    fun stillProgress(tMs: Long): Float {
        val impact = impactAt ?: return 0f
        return ((tMs - impact - settleMs).toFloat() / stillWindowMs).coerceIn(0f, 1f)
    }

    /** Feed one reading of total acceleration magnitude (m/s², gravity included). True = crash. */
    fun onSample(tMs: Long, magnitude: Float): Boolean {
        val g = magnitude / GRAVITY
        if (g > peakG) peakG = g
        val impact = impactAt
        if (impact == null) {
            trackFall(tMs, g)
            val hit = g >= impactG || (g >= landingG && tMs - fellAt <= LANDING_WINDOW_MS)
            if (hit && tMs >= cooldownUntil && wasRiding(tMs)) startWatching(tMs, g)
            return false
        }
        val windowStart = impact + settleMs
        val windowEnd = windowStart + stillWindowMs
        if (tMs < windowStart) {
            if (g > impactPeakG) impactPeakG = g
            return false
        }
        phase = Phase.STILL
        if (tMs <= windowEnd) {
            count++
            sum += magnitude
            sumSq += magnitude.toDouble() * magnitude
            // Picked up or still riding: give up early instead of waiting out the window.
            if (count >= MIN_SAMPLES && std() >= stillStdDev * 2) return moved()
            return false
        }
        // Window over: decide.
        impactAt = null
        if (count < MIN_SAMPLES) return moved()
        val crashed = std() < stillStdDev && !movingNow(tMs)
        if (!crashed) return moved()
        cooldownUntil = tMs + cooldownMs
        phase = Phase.CRASH
        return true
    }

    /** The rider dismissed a false alarm: ignore impacts for a while. */
    fun cancelled(tMs: Long) {
        impactAt = null
        phase = Phase.WAITING
        cooldownUntil = tMs + cooldownMs
    }

    private fun moved(): Boolean {
        impactAt = null
        phase = Phase.MOVED
        return false
    }

    private fun std(): Double {
        val mean = sum / count
        return sqrt((sumSq / count - mean * mean).coerceAtLeast(0.0))
    }

    private fun trackFall(tMs: Long, g: Float) {
        if (g < freeFallG) {
            val since = freeFallSince ?: tMs.also { freeFallSince = it }
            if (tMs - since >= freeFallMs) fellAt = tMs
        } else {
            freeFallSince = null
        }
    }

    private fun startWatching(tMs: Long, g: Float) {
        impactAt = tMs
        impactPeakG = g
        phase = Phase.SETTLING
        count = 0
        sum = 0.0
        sumSq = 0.0
        freeFallSince = null
        fellAt = Long.MIN_VALUE / 2
    }

    /** Unknown speed (no GPS) is given the benefit of the doubt. */
    private fun wasRiding(tMs: Long): Boolean {
        if (!requireRiding) return true
        if (lastSpeed == null || tMs - lastSpeedAt > SPEED_STALE_MS) return true
        return tMs - lastMovingAt <= RECENTLY_MOVING_MS
    }

    private fun movingNow(tMs: Long): Boolean {
        if (!requireRiding) return false
        val speed = lastSpeed ?: return false
        return tMs - lastSpeedAt <= SPEED_STALE_MS && speed > stoppedSpeedMs
    }

    companion object {
        const val GRAVITY = 9.81f
        /** A hit this hard (in g) starts the watch. */
        const val IMPACT_G = 4.0f
        private const val MIN_SAMPLES = 50
        private const val SPEED_STALE_MS = 20_000L
        private const val RECENTLY_MOVING_MS = 45_000L
        private const val LANDING_WINDOW_MS = 1_000L
    }
}
