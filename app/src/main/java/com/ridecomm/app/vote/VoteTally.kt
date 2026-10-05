package com.ridecomm.app.vote

/**
 * Decides a vote. Riders on the move may not get to vote, so a timed-out vote is decided by the
 * votes that were cast; it ends early once either side has a majority of everyone in the ride.
 */
object VoteTally {
    /** True = approved, false = rejected, null = still open. */
    fun outcome(yes: Int, no: Int, riders: Int, timedOut: Boolean): Boolean? {
        val eligible = riders.coerceAtLeast(yes + no).coerceAtLeast(1)
        return when {
            yes * 2 > eligible -> true
            no * 2 >= eligible -> false
            yes + no >= eligible || timedOut -> yes > no
            else -> null
        }
    }
}
