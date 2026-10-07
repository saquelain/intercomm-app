package com.ridecomm.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentRidesTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun roundTripsAndSkipsJunk() {
        val rides = listOf(RecentRide("XCQGCW", 5), RecentRide("CQNQNE", 3))
        assertEquals(rides, RecentRides.parse(RecentRides.format(rides)))
        assertEquals(emptyList<RecentRide>(), RecentRides.parse(""))
        assertEquals(listOf(RecentRide("XCQGCW", 5)), RecentRides.parse("XCQGCW:5,bad,AB:3,CQNQNE:x"))
    }

    @Test
    fun addKeepsNewestFirstWithoutDuplicates() {
        var rides = emptyList<RecentRide>()
        listOf("AAAAAA", "BBBBBB", "CCCCCC", "AAAAAA", "DDDDDD", "EEEEEE").forEachIndexed { i, code ->
            rides = RecentRides.add(rides, code, i.toLong())
        }
        assertEquals(listOf("EEEEEE", "DDDDDD", "AAAAAA", "CCCCCC"), rides.map { it.code })
    }

    @Test
    fun shownDropsOldAndTheRejoinOne() {
        val now = 10 * 24 * hour
        val rides = listOf(RecentRide("AAAAAA", now - hour), RecentRide("BBBBBB", now - 2 * hour), RecentRide("CCCCCC", now - 8 * 24 * hour))
        assertEquals(listOf("BBBBBB"), RecentRides.shown(rides, now, except = "AAAAAA").map { it.code })
    }

    @Test
    fun rejoinOnlyWhenRecent() {
        val now = 100 * hour
        assertEquals("AAAAAA", RecentRides.rejoin(RecentRide("AAAAAA", now - 2 * hour), now)?.code)
        assertNull(RecentRides.rejoin(RecentRide("AAAAAA", now - 13 * hour), now))
        assertNull(RecentRides.rejoin(null, now))
    }

    @Test
    fun agoText() {
        assertEquals("just now", RecentRides.ago(0, 30_000))
        assertEquals("12 min ago", RecentRides.ago(0, 12 * 60_000))
        assertEquals("3 h ago", RecentRides.ago(0, 3 * hour + 5))
        assertEquals("2 d ago", RecentRides.ago(0, 50 * hour))
    }
}
