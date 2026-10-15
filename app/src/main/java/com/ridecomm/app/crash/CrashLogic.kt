package com.ridecomm.app.crash

import kotlin.math.sqrt

/**
 * Decides whether the rider has crashed:
 *
 * 1. **Impact**: total acceleration above [impactG] (a hard hit; road bumps on a phone mount stay
 *    well below this), or a **fall**: the phone in free fall (almost no acceleration) for
 *    [freeFallMs], then a landing above [landingG] within a second (a phone flying out of a pocket
 *    or off the mount, when the hit itself is soft).
 * 2. Then, after a [settleMs] pause, a [windowMs] look at what happens next:
 *    - **GPS says the bike moved on** (riding speed, or well away from where the hit was): a pothole
 *      or a dropped phone while riding. Not a crash.
 *    - **GPS says the bike stayed put**: a crash, even if the rider got up and walked around with the
 *      phone (the owner's rule: a rider who gets up can still be hurt, and the countdown can be cancelled).
 *    - **No GPS** (location off, tunnel, indoors): the phone must lie still, as before.
 *
 * 3. **Was riding**: on a ride, when GPS is working, the hit only counts if I rode over 10 km/h in the last
 *    minute (walking about with the phone set it off, Oct 2026). Without GPS it gets the benefit of the
 *    doubt. The test screen skips this, so it can be tried at home.
 *
 * Plain Kotlin with timestamps passed in, so it can be tested without a phone. [phase], [reason] and
 * [peakG] say what it is doing, for the test screen.
 */
class CrashLogic(
    private val impactG: Float = IMPACT_G,
    /** A landing after a fall counts from this hard (a fall is real free fall, not a swinging hand). */
    private val landingG: Float = maxOf(2.5f, impactG * 0.6f),
    private val freeFallG: Float = 0.3f,
    private val freeFallMs: Long = 200,
    /** Count a fall followed by a landing as a hit (off: only a hit of [impactG] counts). */
    private val falls: Boolean = true,
    private val settleMs: Long = 2_000,
    private val windowMs: Long = 10_000,
    /** Further than this (metres) from the hit means the bike moved on (a crash slide stays shorter). */
    private val movedM: Float = 50f,
    /** Std-dev of acceleration (m/s²) below which the phone counts as still. */
    private val stillStdDev: Float = 0.8f,
    private val cooldownMs: Long = 60_000,
    /**
     * On a ride: when GPS is working, a hit only counts if I was riding (over 10 km/h) in the last
     * minute, so walking about at a tea stop never sets it off. The test screen turns this off.
     */
    private val requireRiding: Boolean = true,
) {
    enum class Phase { WAITING, SETTLING, STILL, MOVED, CRASH }

    /** Why the last crash was called: the phone lay still, or GPS showed the bike had stopped. */
    enum class Reason { STILL, STOPPED }

    /** What the logic is doing now. */
    var phase = Phase.WAITING
        private set

    /** The hardest hit felt so far, in g. */
    var peakG = 0f
        private set

    /** The biggest hit that started the current watch, in g. */
    var impactPeakG = 0f
        private set

    /** The current watch started from a fall and a landing, not a single hard hit. */
    var byFall = false
        private set

    var reason: Reason? = null
        private set

    /** GPS has answered during the current watch (so the bike rule decides, not the phone's stillness). */
    val gpsWatching: Boolean get() = impactAt != null && fixesInWindow > 0

    private var impactAt: Long? = null
    private var count = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var cooldownUntil = 0L

    private var freeFallSince: Long? = null
    private var fellAt = Long.MIN_VALUE / 2

    private class Fix(val tMs: Long, val lat: Double, val lon: Double, val accuracyM: Float)
    private var lastFix: Fix? = null
    /** Where the bike was at the hit (the last good fix before it). */
    private var hitFix: Fix? = null
    private var fixesInWindow = 0
    private var bikeMoved = false
    /** Last time GPS showed riding speed. */
    private var ridingAt = Long.MIN_VALUE / 2

    /**
     * A GPS fix. [speedMs] is null when the phone doesn't know it; fixes worse than [GOOD_FIX_M] are
     * ignored (indoors GPS wanders tens of metres, which would look like riding off).
     */
    fun onLocation(tMs: Long, lat: Double, lon: Double, accuracyM: Float, speedMs: Float?) {
        if (accuracyM > GOOD_FIX_M) return
        val fix = Fix(tMs, lat, lon, accuracyM)
        // Riding: the speed GPS gives, or else worked out from the last fix.
        val prev = lastFix
        val speed = speedMs ?: prev?.takeIf { tMs - it.tMs in 1_000..FIX_STALE_MS }?.let { distanceM(it, fix) * 1_000f / (tMs - it.tMs) }
        if ((speed ?: 0f) >= RIDING_SPEED_MS) ridingAt = tMs
        val impact = impactAt
        if (impact != null && tMs >= impact + settleMs) {
            fixesInWindow++
            val from = hitFix ?: fix.also { hitFix = it }
            val away = distanceM(from, fix)
            if ((speedMs ?: 0f) >= RIDING_SPEED_MS || away > maxOf(movedM, from.accuracyM + fix.accuracyM)) bikeMoved = true
        }
        lastFix = fix
    }

    /** How far through the watch after the hit, 0..1 (only meaningful in [Phase.STILL]). */
    fun stillProgress(tMs: Long): Float {
        val impact = impactAt ?: return 0f
        return ((tMs - impact - settleMs).toFloat() / windowMs).coerceIn(0f, 1f)
    }

    /** Feed one reading of total acceleration magnitude (m/s², gravity included). True = crash. */
    fun onSample(tMs: Long, magnitude: Float): Boolean {
        val g = magnitude / GRAVITY
        if (g > peakG) peakG = g
        val impact = impactAt
        if (impact == null) {
            trackFall(tMs, g)
            val fall = falls && g >= landingG && tMs - fellAt <= LANDING_WINDOW_MS
            val hit = g >= impactG || fall
            if (hit && tMs >= cooldownUntil && wasRiding(tMs)) startWatching(tMs, g, fall && g < impactG)
            return false
        }
        val windowStart = impact + settleMs
        val windowEnd = windowStart + windowMs
        if (tMs < windowStart) {
            if (g > impactPeakG) impactPeakG = g
            return false
        }
        phase = Phase.STILL
        if (tMs <= windowEnd) {
            count++
            sum += magnitude
            sumSq += magnitude.toDouble() * magnitude
            // Rode on: no need to wait out the window.
            if (bikeMoved) return moved()
            return false
        }
        // Window over: decide.
        impactAt = null
        val why = when {
            bikeMoved -> null
            fixesInWindow > 0 -> Reason.STOPPED
            count >= MIN_SAMPLES && std() < stillStdDev -> Reason.STILL
            else -> null
        } ?: return moved()
        cooldownUntil = tMs + cooldownMs
        reason = why
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

    /** No GPS (or none lately): given the benefit of the doubt. */
    private fun wasRiding(tMs: Long): Boolean {
        if (!requireRiding) return true
        val fix = lastFix ?: return true
        if (tMs - fix.tMs > FIX_STALE_MS) return true
        return tMs - ridingAt <= RECENTLY_RIDING_MS
    }

    private fun startWatching(tMs: Long, g: Float, fromFall: Boolean) {
        impactAt = tMs
        impactPeakG = g
        byFall = fromFall
        reason = null
        hitFix = lastFix?.takeIf { tMs - it.tMs <= FIX_STALE_MS }
        fixesInWindow = 0
        bikeMoved = false
        phase = Phase.SETTLING
        count = 0
        sum = 0.0
        sumSq = 0.0
        freeFallSince = null
        fellAt = Long.MIN_VALUE / 2
    }

    companion object {
        const val GRAVITY = 9.81f
        /** A hit this hard (in g) starts the watch. */
        const val IMPACT_G = 4.0f

        /** A detector with the rider's own settings (Settings → Safety → Test crash detection → Fine-tune). */
        fun tuned(t: CrashTuning, requireRiding: Boolean = true) = CrashLogic(
            impactG = t.g, windowMs = t.waitS * 1_000L, movedM = t.moveM.toFloat(), falls = t.falls, requireRiding = requireRiding,
        )
        /**
         * Readings needed in the still window to judge it. Low on purpose: some phones slow the
         * sensor right down with the screen off (a few readings a second), and 50 used to make
         * those give up without deciding.
         */
        private const val MIN_SAMPLES = 15
        /** Faster than this (about 11 km/h) after the hit means riding on. Walking is well below. */
        private const val RIDING_SPEED_MS = 3.0f
        private const val GOOD_FIX_M = 30f
        private const val FIX_STALE_MS = 20_000L
        private const val RECENTLY_RIDING_MS = 60_000L

        private fun distanceM(a: Fix, b: Fix): Float {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon) * kotlin.math.cos(Math.toRadians((a.lat + b.lat) / 2))
            return (r * sqrt(dLat * dLat + dLon * dLon)).toFloat()
        }
        private const val LANDING_WINDOW_MS = 1_000L
    }
}

/**
 * The three numbers a rider can change on the test screen to fit their phone and bike: how hard a
 * hit must be, how long to watch afterwards, and how far the bike may go and still count as stopped.
 */
data class CrashTuning(val g: Float = CrashLogic.IMPACT_G, val waitS: Int = 10, val moveM: Int = 50, val falls: Boolean = true) {
    /** Kept inside sensible limits whatever is stored. */
    fun safe() = CrashTuning(g.coerceIn(G_MIN, G_MAX), waitS.coerceIn(WAIT_MIN, WAIT_MAX), moveM.coerceIn(MOVE_MIN, MOVE_MAX), falls)

    val isDefault: Boolean get() = this == CrashTuning()

    companion object {
        const val G_MIN = 2f
        /** Most phones' sensors stop counting around 8–16 g per axis. */
        const val G_MAX = 12f
        const val WAIT_MIN = 5
        const val WAIT_MAX = 60
        const val MOVE_MIN = 10
        const val MOVE_MAX = 500
    }
}
