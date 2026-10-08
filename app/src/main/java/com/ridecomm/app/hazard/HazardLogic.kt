package com.ridecomm.app.hazard

import com.ridecomm.app.group.GroupMath
import kotlin.math.abs

/** Something on the road the riders behind should know about. */
enum class HazardKind(val label: String, val spoken: String, val keepMs: Long) {
    POTHOLE("Pothole", "Pothole", 2 * HOUR),
    SPEED_BREAKER("Speed breaker", "Speed breaker", 2 * HOUR),
    SLIPPERY("Slippery", "Slippery road", 2 * HOUR),
    POLICE("Police", "Police check", 30 * MINUTE),
    ACCIDENT("Accident", "Accident", HOUR),
    ANIMAL("Animal", "Animal on the road", 20 * MINUTE),
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

data class Hazard(
    val id: String,
    val kind: HazardKind,
    val lat: Double,
    val lon: Double,
    val byId: String,
    val byName: String,
    /** When it was marked, or last confirmed by another rider (wall clock). */
    val atMs: Long,
) {
    fun expired(nowMs: Long) = nowMs - atMs > kind.keepMs
}

/** When to warn about hazards (plain logic, so it can be unit-tested). */
object HazardLogic {
    /** Warn this far before reaching it. */
    const val WARN_M = 500.0
    /** Closer than this I'm on it or past it: too late to warn. */
    const val PASSED_M = 40.0
    /** Marking the same kind this close to an existing one confirms it instead of adding another. */
    const val SAME_SPOT_M = 80.0
    /** Within this angle of my direction it's on the road ahead. */
    private const val AHEAD_DEG = 45.0
    /** Announce newly marked hazards up to this far ahead. */
    const val ANNOUNCE_WITHIN_M = 30_000.0

    /** Ahead of me along my direction of travel (null heading: can't tell). */
    fun isAhead(myLat: Double, myLon: Double, myHeadingDeg: Double?, lat: Double, lon: Double, withinDeg: Double = AHEAD_DEG): Boolean? {
        myHeadingDeg ?: return null
        val bearing = GroupMath.bearingDeg(myLat, myLon, lat, lon)
        return abs(((bearing - myHeadingDeg) % 360 + 540) % 360 - 180) <= withinDeg
    }

    /** Time to say "Pothole in 300 meters". */
    fun shouldWarn(distanceM: Double, ahead: Boolean?): Boolean =
        ahead == true && distanceM in PASSED_M..WARN_M

    /** An existing hazard of the same kind at this spot, if any. */
    fun sameSpot(hazards: Collection<Hazard>, kind: HazardKind, lat: Double, lon: Double): Hazard? =
        hazards.firstOrNull { it.kind == kind && GroupMath.distanceM(it.lat, it.lon, lat, lon) < SAME_SPOT_M }

    fun warning(kind: HazardKind, distanceM: Double) = "${kind.spoken} in ${GroupMath.spokenDistance(distanceM)}"
}
