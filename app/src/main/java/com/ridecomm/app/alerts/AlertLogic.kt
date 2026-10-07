package com.ridecomm.app.alerts

/** What happened to a rider, as announced to the group. */
enum class PresenceEvent { JOINED, BACK, LEFT, DROPPED }

/**
 * Tells "left the ride" (they tapped Leave and said bye) from "dropped out" (signal lost), and
 * "is back" from a first join. Pure logic; ids are rider identities.
 */
class Presence {
    private val seen = mutableSetOf<String>()
    private val dropped = mutableSetOf<String>()
    private val byeAt = mutableMapOf<String, Long>()

    /** Riders already in the ride when I joined: no announcement, but remembered. */
    fun known(id: String) {
        seen += id
    }

    fun bye(id: String, nowMs: Long) {
        byeAt[id] = nowMs
    }

    fun joined(id: String): PresenceEvent {
        byeAt.remove(id)
        val event = if (dropped.remove(id)) PresenceEvent.BACK else PresenceEvent.JOINED
        seen += id
        return event
    }

    fun left(id: String, nowMs: Long): PresenceEvent {
        val bye = byeAt.remove(id)
        return if (bye != null && nowMs - bye <= BYE_VALID_MS) {
            PresenceEvent.LEFT
        } else {
            dropped += id
            PresenceEvent.DROPPED
        }
    }

    companion object {
        /** A "bye" counts for the disconnect that follows within this long. */
        const val BYE_VALID_MS = 30_000L

        fun spoken(name: String, event: PresenceEvent): String = when (event) {
            PresenceEvent.JOINED -> "$name joined the ride"
            PresenceEvent.BACK -> "$name is back"
            PresenceEvent.LEFT -> "$name left the ride"
            PresenceEvent.DROPPED -> "$name dropped out. Probably no signal."
        }
    }
}

/**
 * Decides when one phone's battery is worth announcing: once each time it falls to 20%, 10% and
 * 5% while not charging. Charging or going back above [RESET_ABOVE] starts over.
 */
class BatteryWatch {
    private var lowestAnnounced: Int? = null

    /** Returns the level to announce, or null. */
    fun update(level: Int, charging: Boolean): Int? {
        if (charging || level > RESET_ABOVE) {
            lowestAnnounced = null
            return null
        }
        val threshold = THRESHOLDS.lastOrNull { level <= it } ?: return null
        val last = lowestAnnounced
        if (last != null && threshold >= last) return null
        lowestAnnounced = threshold
        return level
    }

    companion object {
        val THRESHOLDS = listOf(20, 10, 5)
        const val RESET_ABOVE = 25
        /** Riders' battery shows on their card at or below this. */
        const val SHOW_AT_OR_BELOW = 30

        fun spoken(name: String?, level: Int): String =
            if (name == null) "Your phone battery is at $level percent" else "$name's phone battery is at $level percent"
    }
}
