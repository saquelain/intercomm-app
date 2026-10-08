package com.ridecomm.app.trip

/** How much riding before "Time for a break?". */
enum class BreakEvery(val label: String, val minutes: Int) {
    OFF("Off", 0),
    H1("1 h", 60),
    H1_5("1½ h", 90),
    H2("2 h", 120),
    H3("3 h", 180),
}

/**
 * Decides when to suggest a break: after [everyMin] minutes of riding since the last break, then
 * again every [SNOOZE_MS] of riding if nobody stops. A break is a Break vote that passed, or a stop
 * of [STOP_RESETS_MS] or more (riding time not going up). Riding time is the time actually moving
 * when GPS is on, otherwise the time in the ride. Pure logic, for tests.
 */
class BreakWatch(var everyMin: Int) {
    private var breakAtMs = 0L
    private var lastRidingMs = 0L
    private var remindedAtMs: Long? = null
    private var stillSinceMs: Long? = null

    /** Riding since the last break. */
    val sinceBreakMs: Long get() = lastRidingMs - breakAtMs

    /** Call regularly with the wall clock and riding time so far; true means "remind now". */
    fun update(nowMs: Long, ridingMs: Long): Boolean {
        if (ridingMs < lastRidingMs) {
            // The time source changed (GPS came or went): keep the riding done since the break.
            breakAtMs = (ridingMs - sinceBreakMs).coerceAtLeast(0)
            remindedAtMs = null
        }
        stillSinceMs = if (ridingMs > lastRidingMs) null else stillSinceMs ?: nowMs
        lastRidingMs = ridingMs
        val still = stillSinceMs
        if (still != null && nowMs - still >= STOP_RESETS_MS && sinceBreakMs > 0) tookBreak()
        if (everyMin <= 0) return false
        val due = remindedAtMs?.let { it + SNOOZE_MS } ?: (breakAtMs + everyMin * 60_000L)
        if (ridingMs < due) return false
        remindedAtMs = ridingMs
        return true
    }

    /** The group stopped (a Break vote passed): start counting again. */
    fun tookBreak() {
        breakAtMs = lastRidingMs
        remindedAtMs = null
    }

    companion object {
        const val SNOOZE_MS = 30 * 60_000L
        const val STOP_RESETS_MS = 10 * 60_000L

        /** "You've been riding for 2 hours. Time for a break?" */
        fun spoken(ridingMs: Long): String = "You've been riding for ${TripSpeech.duration(ridingMs)}. Time for a break?"
    }
}
