package com.ridecomm.app.group

/** A meeting point someone set for the group ("Regroup at the Shell pump"). */
data class RegroupPoint(
    val id: String,
    val lat: Double,
    val lon: Double,
    val label: String,
    /** Name and identity of the rider who set it. */
    val byName: String,
    val byId: String,
    /** Wall clock when it was set; a newer point replaces an older one. */
    val atMs: Long,
    /** Identities of riders who have reached it. */
    val arrived: Set<String> = emptySet(),
)

/**
 * Regroup point rules, kept free of Android so they can be tested: which point wins, when I've
 * arrived, and when the whole group is there.
 */
object RegroupLogic {
    /** Closer than this to the point counts as arrived. */
    const val ARRIVE_M = 150.0
    /** Further than this again (after arriving) and I've left it. */
    const val LEAVE_M = 400.0

    /** Labels offered when setting a point. */
    val LABELS = listOf("Regroup", "Petrol pump", "Dhaba", "Tea stop", "Toll plaza", "Turn here")

    /** Keeps the newer of two points (ties broken by id so every phone agrees). */
    fun newer(current: RegroupPoint?, incoming: RegroupPoint): Boolean =
        current == null || incoming.atMs > current.atMs || (incoming.atMs == current.atMs && incoming.id > current.id)

    /** Whether I'm at the point, with a margin so GPS wobble at the edge doesn't flip it. */
    fun isAt(distanceM: Double, wasAt: Boolean): Boolean = if (wasAt) distanceM < LEAVE_M else distanceM < ARRIVE_M

    /** Everyone currently in the ride (including me) has arrived. */
    fun everyoneThere(point: RegroupPoint, riderIds: Collection<String>): Boolean =
        riderIds.isNotEmpty() && riderIds.all { it in point.arrived }

    /** "Petrol pump, 4.2 kilometers ahead" / "Dhaba" when my position isn't known. */
    fun spokenWhere(point: RegroupPoint, distanceM: Double?, relation: Relation?): String = buildString {
        append(point.label)
        if (distanceM != null) {
            append(", ").append(GroupMath.spokenDistance(distanceM))
            when (relation) {
                Relation.AHEAD -> append(" ahead")
                Relation.BEHIND -> append(" behind")
                else -> append(" away")
            }
        }
    }
}
