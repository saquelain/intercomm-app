package com.ridecomm.app.ride

/** A ride joined before: its code and when. */
data class RecentRide(val code: String, val atMs: Long)

/** Recent ride codes and the "rejoin" offer after a ride ended without the rider leaving. Pure logic, stored as text in Prefs. */
object RecentRides {
    const val MAX = 4
    /** Recent codes older than this aren't offered (the group has long moved on). */
    const val KEEP_MS = 7 * 24 * 60 * 60 * 1000L
    /** A ride cut off longer ago than this isn't offered for rejoining. */
    const val REJOIN_WINDOW_MS = 12 * 60 * 60 * 1000L

    fun parse(text: String): List<RecentRide> = text.split(',').mapNotNull { entry ->
        val code = entry.substringBefore(':')
        val at = entry.substringAfter(':', "").toLongOrNull()
        if (at != null && code.length == RideCode.LENGTH) RecentRide(code, at) else null
    }

    fun format(rides: List<RecentRide>): String = rides.joinToString(",") { "${it.code}:${it.atMs}" }

    /** Newest first, each code once, at most [MAX]. */
    fun add(rides: List<RecentRide>, code: String, nowMs: Long): List<RecentRide> =
        (listOf(RecentRide(code, nowMs)) + rides.filter { it.code != code }).take(MAX)

    fun shown(rides: List<RecentRide>, nowMs: Long, except: String?): List<RecentRide> =
        rides.filter { it.code != except && nowMs - it.atMs in 0..KEEP_MS }

    /** The cut-off ride to offer for rejoining, if it's recent enough. */
    fun rejoin(unfinished: RecentRide?, nowMs: Long): RecentRide? =
        unfinished?.takeIf { nowMs - it.atMs in 0..REJOIN_WINDOW_MS }

    /** "just now", "12 min ago", "3 h ago", "2 d ago". */
    fun ago(atMs: Long, nowMs: Long): String {
        val min = (nowMs - atMs).coerceAtLeast(0) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 24 * 60 -> "${min / 60} h ago"
            else -> "${min / (24 * 60)} d ago"
        }
    }
}
