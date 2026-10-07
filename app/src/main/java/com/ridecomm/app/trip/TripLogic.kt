package com.ridecomm.app.trip

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading. [speedMps] is the receiver's own speed, when it gives one. */
data class Fix(val timeMs: Long, val lat: Double, val lon: Double, val accuracyM: Float, val speedMps: Float?)

/**
 * Distance, moving time and speeds from my own GPS. GPS wanders a few metres even when standing
 * still, and now and then jumps; both are filtered so a stop at a dhaba doesn't add kilometres.
 */
class TripMeter {
    var distanceM = 0.0
        private set
    var movingMs = 0L
        private set
    var topKmh = 0f
        private set
    /** Latest speed, or null when GPS has nothing recent. */
    var speedKmh: Float? = null
        private set

    private var anchor: Fix? = null
    private var last: Fix? = null
    private val recentSpeeds = ArrayDeque<Float>()

    fun add(fix: Fix) {
        val prev = last
        if (prev != null && fix.timeMs <= prev.timeMs) return
        if (fix.accuracyM > MAX_ACCURACY_M) {
            speedKmh = null
            return
        }
        val dt = if (prev != null) fix.timeMs - prev.timeMs else 0L
        val derived = if (prev != null && dt > 0) metres(prev, fix) / (dt / 1000.0) else null
        val mps = fix.speedMps ?: derived?.toFloat()
        // A jump no motorbike can make: ignore this fix entirely.
        if (derived != null && derived > MAX_PLAUSIBLE_MPS && dt < 10_000) return
        last = fix

        val kmh = mps?.let { it * 3.6f }
        speedKmh = kmh
        if (kmh != null && kmh >= MOVING_KMH && dt in 1..MAX_GAP_MS) movingMs += dt
        if (kmh != null) {
            // Top speed from the middle of the last three readings, so one bad reading doesn't count.
            recentSpeeds.addLast(kmh)
            if (recentSpeeds.size > 3) recentSpeeds.removeFirst()
            if (recentSpeeds.size == 3) topKmh = maxOf(topKmh, recentSpeeds.sorted()[1])
        }

        val a = anchor
        if (a == null) {
            anchor = fix
            return
        }
        // Count distance only once we've clearly left the last counted point.
        val moved = metres(a, fix)
        if (moved >= maxOf(MIN_STEP_M, fix.accuracyM.toDouble())) {
            distanceM += moved
            anchor = fix
        }
    }

    /** No GPS for a while (tunnel, lost signal). */
    fun noSignal() {
        speedKmh = null
    }

    /** Average while moving, km/h. */
    val averageKmh: Float
        get() = if (movingMs < 60_000) 0f else (distanceM / 1000 / (movingMs / 3_600_000.0)).toFloat()

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_STEP_M = 12.0
        const val MOVING_KMH = 5f
        const val MAX_GAP_MS = 5_000L
        const val MAX_PLAUSIBLE_MPS = 85.0 // ~300 km/h

        fun metres(a: Fix, b: Fix): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(sqrt(h))
        }
    }
}

/**
 * When to warn about speed: after [SUSTAIN_MS] above the limit, then every [REPEAT_MS] while
 * still above it. Dropping [RESET_BELOW_KMH] under the limit starts over.
 */
class SpeedWatch(var limitKmh: Int) {
    private var aboveSince: Long? = null
    private var lastWarned: Long? = null

    /** Returns the speed to announce, or null. */
    fun update(nowMs: Long, kmh: Float?): Int? {
        if (limitKmh <= 0 || kmh == null) return null
        if (kmh < limitKmh - RESET_BELOW_KMH) {
            aboveSince = null
            lastWarned = null
            return null
        }
        if (kmh <= limitKmh) return null
        val since = aboveSince ?: nowMs.also { aboveSince = it }
        if (nowMs - since < SUSTAIN_MS) return null
        val warned = lastWarned
        if (warned != null && nowMs - warned < REPEAT_MS) return null
        lastWarned = nowMs
        return kmh.roundToInt()
    }

    companion object {
        const val SUSTAIN_MS = 3_000L
        const val REPEAT_MS = 30_000L
        const val RESET_BELOW_KMH = 5
    }
}

/** How often to speak a ride update. */
enum class UpdateEvery(val label: String, val minutes: Int = 0, val km: Int = 0) {
    OFF("Off"),
    MIN_15("15 min", minutes = 15),
    MIN_30("30 min", minutes = 30),
    KM_10("10 km", km = 10),
    KM_25("25 km", km = 25),
}

/** Says when the next spoken update is due: each time a new block of time or distance is reached. */
class UpdateSchedule(var every: UpdateEvery) {
    private var lastBlock = 0L

    fun due(elapsedMs: Long, distanceM: Double): Boolean {
        val block = when {
            every.minutes > 0 -> elapsedMs / (every.minutes * 60_000L)
            every.km > 0 -> (distanceM / (every.km * 1000.0)).toLong()
            else -> return false
        }
        if (block <= lastBlock) return false
        lastBlock = block
        return true
    }

    /** Settings changed mid-ride: don't fire for everything already passed. */
    fun restart(elapsedMs: Long, distanceM: Double) {
        lastBlock = 0
        due(elapsedMs, distanceM)
    }
}

object TripSpeech {
    /** "42 kilometres. 1 hour 10 minutes riding. Average 56." */
    fun update(distanceM: Double, elapsedMs: Long, averageKmh: Float): String = buildString {
        append(distance(distanceM)).append(". ")
        append(duration(elapsedMs)).append(" riding")
        if (averageKmh >= 1f) append(". Average ").append(averageKmh.roundToInt())
        append(".")
    }

    fun distance(m: Double): String = when {
        m < 1_000 -> "${(m / 10).roundToInt() * 10} metres"
        m < 100_000 -> {
            val km = (m / 100).roundToInt() / 10.0
            if (km % 1.0 == 0.0) "${km.toInt()} kilometres" else "$km kilometres"
        }
        else -> "${(m / 1000).roundToInt()} kilometres"
    }

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m ${if (m == 1L) "minute" else "minutes"}"
            m == 0L -> "$h ${if (h == 1L) "hour" else "hours"}"
            else -> "$h ${if (h == 1L) "hour" else "hours"} $m ${if (m == 1L) "minute" else "minutes"}"
        }
    }
}
