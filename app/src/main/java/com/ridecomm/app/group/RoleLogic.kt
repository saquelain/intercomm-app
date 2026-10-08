package com.ridecomm.app.group

import kotlin.math.cos

/** Who leads the group and who rides last (the sweep), shared by everyone in the ride. */
data class RideRolesState(
    val leadId: String? = null,
    val sweepId: String? = null,
    /** When they were last set (wall clock), so the newest choice wins everywhere. */
    val atMs: Long = 0,
    val byId: String = "",
)

/** Plain logic for lead & sweep alerts (no Android types, so it can be unit-tested). */
object RoleLogic {
    /**
     * How far [to] is ahead (+) or behind (−) [from] along [headingDeg], the direction [from] is
     * riding. A rider 1 km away to the side is neither ahead nor behind.
     */
    fun alongM(fromLat: Double, fromLon: Double, headingDeg: Double, toLat: Double, toLon: Double): Double {
        val d = GroupMath.distanceM(fromLat, fromLon, toLat, toLon)
        if (d < 1) return 0.0
        val bearing = GroupMath.bearingDeg(fromLat, fromLon, toLat, toLon)
        return d * cos(Math.toRadians(bearing - headingDeg))
    }

    /** Newer wins; equal times are settled by who set them, so every phone agrees. */
    fun newer(current: RideRolesState, incoming: RideRolesState): Boolean =
        incoming.atMs > current.atMs || incoming.atMs == current.atMs && incoming.byId > current.byId
}

/**
 * Decides when to speak a lead or sweep alert: once when a rider gets more than [aheadM] ahead
 * of the lead or [behindM] behind the sweep, and again only after they've come back into place
 * (within [backM]), so GPS jitter doesn't repeat it.
 */
class RoleWatch(
    private val aheadM: Double = 300.0,
    private val behindM: Double = 500.0,
    private val backM: Double = 100.0,
) {
    enum class Alert { AHEAD_OF_LEAD, BEHIND_SWEEP }

    private val ahead = mutableSetOf<String>()
    private val behind = mutableSetOf<String>()

    /** [alongFromLead]: the rider's distance ahead of the lead; [alongFromSweep]: ahead of the sweep. */
    fun check(riderId: String, alongFromLead: Double?, alongFromSweep: Double?): List<Alert> = buildList {
        if (alongFromLead != null) {
            if (alongFromLead > aheadM && ahead.add(riderId)) add(Alert.AHEAD_OF_LEAD)
            if (alongFromLead < backM) ahead.remove(riderId)
        }
        if (alongFromSweep != null) {
            if (alongFromSweep < -behindM && behind.add(riderId)) add(Alert.BEHIND_SWEEP)
            if (alongFromSweep > -backM) behind.remove(riderId)
        }
    }

    fun forget(riderId: String) {
        ahead.remove(riderId)
        behind.remove(riderId)
    }

    fun clear() {
        ahead.clear()
        behind.clear()
    }
}
