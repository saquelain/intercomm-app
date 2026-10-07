package com.ridecomm.app.group

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Where another rider is relative to me along the road. */
enum class Relation { AHEAD, BEHIND, NEARBY }

/** Plain-Kotlin geo maths (no Android types), so it can be unit-tested. */
object GroupMath {
    private const val EARTH_RADIUS_M = 6_371_000.0
    /** Within this distance riders count as riding together. */
    const val TOGETHER_M = 150.0

    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial compass bearing from point 1 to point 2, 0–360° (0 = north). */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Ahead if the other rider lies within 90° of the direction I'm heading, behind otherwise.
     * Without a heading (standing still) or when close, they're just nearby.
     */
    fun relation(distanceM: Double, myHeadingDeg: Double?, bearingToThemDeg: Double): Relation {
        if (distanceM < TOGETHER_M || myHeadingDeg == null) return Relation.NEARBY
        val diff = abs(((bearingToThemDeg - myHeadingDeg) % 360 + 540) % 360 - 180)
        return if (diff <= 90) Relation.AHEAD else Relation.BEHIND
    }

    /** "350 m" / "1.2 km" for the screen. */
    fun shortDistance(m: Double): String =
        if (m < 1_000) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(m / 1_000)

    /** "350 meters" / "1.2 kilometers" for speech. */
    fun spokenDistance(m: Double): String =
        if (m < 1_000) "${(m / 10).roundToInt() * 10} meters" else "%.1f kilometers".format(m / 1_000)
}

/**
 * Decides when to announce that a rider got separated, so alerts are useful rather than chatty:
 * once when they pass 1 km, again at each further kilometre, and once when they're back within
 * 500 m. Distance jitter around a threshold doesn't repeat the alert.
 */
class SeparationTracker(
    private val alertEveryM: Double = 1_000.0,
    private val backTogetherM: Double = 500.0,
) {
    /** Rider id → highest kilometre step already announced (0 = together). */
    private val announcedStep = mutableMapOf<String, Int>()

    sealed class Alert {
        data class Separated(val distanceM: Double) : Alert()
        data object BackTogether : Alert()
    }

    fun update(riderId: String, distanceM: Double): Alert? {
        val previous = announcedStep[riderId] ?: 0
        val step = (distanceM / alertEveryM).toInt()
        return when {
            step > previous -> {
                announcedStep[riderId] = step
                Alert.Separated(distanceM)
            }
            previous > 0 && distanceM < backTogetherM -> {
                announcedStep[riderId] = 0
                Alert.BackTogether
            }
            else -> null
        }
    }

    fun forget(riderId: String) {
        announcedStep.remove(riderId)
    }

    fun clear() = announcedStep.clear()
}
