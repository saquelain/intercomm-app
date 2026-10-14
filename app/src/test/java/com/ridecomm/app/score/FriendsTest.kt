package com.ridecomm.app.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FriendsTest {
    private val me = Friend("me", "You", 2, 900, 400, 0)

    @Test
    fun bestFirstWithMeAmongThem() {
        val friends = listOf(
            Friend("a", "Amit", 3, 1_800, 900, 1),
            Friend("r", "Rahul", 1, 250, 250, 2),
            Friend("v", "Vikram", 2, 900, 300, 3),
        )
        assertEquals(listOf("a", "me", "v", "r"), Friends.board(friends, me).map { it.id })
    }

    @Test
    fun anOlderPhoneWithoutATotalRanksByItsLevel() {
        // Level 4 (Road Captain) starts at 3,000 points.
        val old = Friend("o", "Old phone", 4, null, 100, 1)
        assertEquals(3_000, old.rankPoints)
        assertEquals("o", Friends.board(listOf(old), me).first().id)
    }

    @Test
    fun theLatestRideTogetherReplacesTheOldEntry() {
        val first = listOf(Friend("a", "Amit", 1, 300, 300, 10))
        val later = Friends.update(first, Friend("a", "Amit S", 2, 700, 700, 20))
        assertEquals(1, later.size)
        assertEquals("Amit S", later[0].name)
        assertEquals(700, later[0].total)
    }

    @Test
    fun keepsTheFiftyMostRecent() {
        var list = emptyList<Friend>()
        repeat(60) { list = Friends.update(list, Friend("f$it", "F$it", 0, it, it, it.toLong())) }
        assertEquals(Friends.KEEP, list.size)
        assertEquals("f59", list.first().id)
    }

    @Test
    fun jsonRoundTrip() {
        val list = listOf(Friend("a", "Amit", 3, 1_800, 900, 123), Friend("o", "Old", 1, null, 50, 456))
        val back = Friends.fromJson(Friends.toJson(list))
        assertEquals(list, back)
        assertNull(back[1].total)
        assertEquals(emptyList<Friend>(), Friends.fromJson("nonsense"))
        assertEquals(emptyList<Friend>(), Friends.fromJson(null))
    }
}
