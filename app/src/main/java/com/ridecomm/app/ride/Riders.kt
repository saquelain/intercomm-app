package com.ridecomm.app.ride

import com.ridecomm.app.home.HomeLogic
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant

/**
 * Who in a ride is a rider. Family following the map from home join as `watch-…`, and a rider who
 * already left can drop in as `…~home` to say they got home: neither is listed, counted, announced
 * or asked anything (see PROTOCOL.md).
 */
object Riders {
    const val WATCHER_PREFIX = "watch-"

    fun isWatcher(identity: String) = identity.startsWith(WATCHER_PREFIX)

    fun isRider(identity: String) = !isWatcher(identity) && !HomeLogic.isCheckIn(identity)

    fun isRider(p: Participant) = p.identity?.value?.let { isRider(it) } == true

    /**
     * Who my position or SOS goes to: everyone (an empty list) when I let family watch or nobody is
     * watching; otherwise only the riders. Null when no rider is there to send it to.
     */
    fun privateAudience(ids: Collection<String>, familyMayWatch: Boolean): List<String>? {
        if (familyMayWatch || ids.none { isWatcher(it) }) return emptyList()
        return ids.filter { isRider(it) }.takeIf { it.isNotEmpty() }
    }
}

/** The other riders in the room (no family watchers, no home-safe check-ins). */
fun Room.riderIds(): List<String> = remoteParticipants.keys.map { it.value }.filter { Riders.isRider(it) }

/** Names of family members watching the ride map. */
fun Room.watcherNames(): List<String> =
    remoteParticipants.values.filter { p -> p.identity?.value?.let { Riders.isWatcher(it) } == true }
        .map { it.name?.takeIf { n -> n.isNotBlank() } ?: "Family" }

/** [Riders.privateAudience] for this room, as LiveKit identities. */
fun Room.privateAudience(familyMayWatch: Boolean): List<Participant.Identity>? =
    Riders.privateAudience(remoteParticipants.keys.map { it.value }, familyMayWatch)?.map { Participant.Identity(it) }
