package com.ridecomm.app.vote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoteTallyTest {

    @Test
    fun soloRiderIsApprovedImmediately() {
        assertEquals(true, VoteTally.outcome(yes = 1, no = 0, riders = 1, timedOut = false))
    }

    @Test
    fun endsEarlyOnceYesHasMajorityOfRiders() {
        assertNull(VoteTally.outcome(yes = 2, no = 0, riders = 5, timedOut = false))
        assertEquals(true, VoteTally.outcome(yes = 3, no = 0, riders = 5, timedOut = false))
    }

    @Test
    fun endsEarlyWhenYesCanNoLongerWin() {
        // 4 riders, 2 no: best case is a 2–2 tie, which isn't approved.
        assertEquals(false, VoteTally.outcome(yes = 1, no = 2, riders = 4, timedOut = false))
    }

    @Test
    fun waitsForEveryoneOtherwise() {
        assertNull(VoteTally.outcome(yes = 2, no = 1, riders = 6, timedOut = false))
    }

    @Test
    fun timeoutDecidesByVotesCast() {
        // Riders busy on the road didn't vote: 2 yes vs 1 no still means stop.
        assertEquals(true, VoteTally.outcome(yes = 2, no = 1, riders = 6, timedOut = true))
        assertEquals(false, VoteTally.outcome(yes = 1, no = 1, riders = 6, timedOut = true))
    }

    @Test
    fun ridersWhoJoinedLateStillCount() {
        // More ballots than riders at the start: decided once everyone counted has voted.
        assertEquals(true, VoteTally.outcome(yes = 3, no = 1, riders = 2, timedOut = false))
    }
}
