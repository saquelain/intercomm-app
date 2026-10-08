package com.ridecomm.app.home

/**
 * "Home safe" rules, free of Android so they can be tested.
 *
 * A rider who already left the ride can still check in: their phone joins the ride for a moment
 * under a second identity ending in [CHECK_IN_SUFFIX], says "home", and leaves. Other phones
 * don't show or announce that short visit as a rider joining.
 */
object HomeLogic {
    const val CHECK_IN_SUFFIX = "~home"
    /** A check-in after leaving is offered on the home screen for this long. */
    const val CHECK_IN_WINDOW_MS = 12 * 60 * 60 * 1000L

    fun isCheckIn(identity: String) = identity.endsWith(CHECK_IN_SUFFIX)

    /** The rider behind an identity (a check-in visit counts as the rider). */
    fun riderOf(identity: String) = identity.removeSuffix(CHECK_IN_SUFFIX)

    fun checkInIdentity(riderId: String) = riderId + CHECK_IN_SUFFIX
}

/**
 * Notices arriving home during a ride: only after having been [awayM] or more from home (so
 * starting the ride at home doesn't count), then once within [arriveM].
 */
class HomeArrival(private val awayM: Double = 1_000.0, private val arriveM: Double = 200.0) {
    private var wasAway = false
    private var arrived = false

    /** Distance from home on each GPS fix; true once, on arrival. */
    fun update(distanceM: Double): Boolean {
        if (distanceM >= awayM) wasAway = true
        if (!wasAway || arrived || distanceM > arriveM) return false
        arrived = true
        return true
    }
}
