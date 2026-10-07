package com.ridecomm.app.crash

import kotlin.math.sqrt

/**
 * Decides from accelerometer readings (and speed, when known) whether the rider has crashed:
 *
 * 1. **Impact**: total acceleration above [impactG] (a hard hit; road bumps on a phone mount stay
 *    well below this).
 * 2. **Was riding**: if GPS speed is known, the rider must have been moving faster than
 *    [movingSpeedMs] shortly before the impact, so a phone dropped at a stop doesn't count.
 * 3. **No movement**: after a [settleMs] pause for the bike/phone to come to rest, the phone stays
 *    almost perfectly still for [stillWindowMs] (riding, walking or picking the phone up shake it).
 *
 * Plain Kotlin with timestamps passed in, so it can be tested without a phone.
 */
class CrashLogic(
    private val impactG: Float = 4.0f,
    private val settleMs: Long = 2_000,
    private val stillWindowMs: Long = 10_000,
    /** Std-dev of acceleration (m/s²) below which the phone counts as still. */
    private val stillStdDev: Float = 0.8f,
    private val movingSpeedMs: Float = 4.2f, // 15 km/h
    private val stoppedSpeedMs: Float = 2.0f,
    private val cooldownMs: Long = 60_000,
) {
    private var impactAt: Long? = null
    private var count = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var cooldownUntil = 0L

    private var lastSpeed: Float? = null
    private var lastSpeedAt = 0L
    /** Last time the rider was clearly moving. */
    private var lastMovingAt = Long.MIN_VALUE / 2

    fun onSpeed(tMs: Long, speedMs: Float) {
        lastSpeed = speedMs
        lastSpeedAt = tMs
        if (speedMs >= movingSpeedMs) lastMovingAt = tMs
    }

    /** Feed one reading of total acceleration magnitude (m/s², gravity included). True = crash. */
    fun onSample(tMs: Long, magnitude: Float): Boolean {
        val impact = impactAt
        if (impact == null) {
            if (tMs >= cooldownUntil && magnitude >= impactG * GRAVITY && wasRiding(tMs)) startWatching(tMs)
            return false
        }
        val windowStart = impact + settleMs
        val windowEnd = windowStart + stillWindowMs
        if (tMs < windowStart) return false
        if (tMs <= windowEnd) {
            count++
            sum += magnitude
            sumSq += magnitude.toDouble() * magnitude
            return false
        }
        // Window over: decide.
        impactAt = null
        if (count < MIN_SAMPLES) return false
        val mean = sum / count
        val std = sqrt((sumSq / count - mean * mean).coerceAtLeast(0.0))
        val crashed = std < stillStdDev && !movingNow(tMs)
        if (crashed) cooldownUntil = tMs + cooldownMs
        return crashed
    }

    /** The rider dismissed a false alarm: ignore impacts for a while. */
    fun cancelled(tMs: Long) {
        impactAt = null
        cooldownUntil = tMs + cooldownMs
    }

    private fun startWatching(tMs: Long) {
        impactAt = tMs
        count = 0
        sum = 0.0
        sumSq = 0.0
    }

    /** Unknown speed (no GPS) is given the benefit of the doubt. */
    private fun wasRiding(tMs: Long): Boolean {
        if (lastSpeed == null || tMs - lastSpeedAt > SPEED_STALE_MS) return true
        return tMs - lastMovingAt <= RECENTLY_MOVING_MS
    }

    private fun movingNow(tMs: Long): Boolean {
        val speed = lastSpeed ?: return false
        return tMs - lastSpeedAt <= SPEED_STALE_MS && speed > stoppedSpeedMs
    }

    companion object {
        const val GRAVITY = 9.81f
        private const val MIN_SAMPLES = 50
        private const val SPEED_STALE_MS = 20_000L
        private const val RECENTLY_MOVING_MS = 30_000L
    }
}
