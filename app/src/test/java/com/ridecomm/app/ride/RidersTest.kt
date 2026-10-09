package com.ridecomm.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RidersTest {
    @Test
    fun familyAndCheckInsArentRiders() {
        assertTrue(Riders.isRider("3f2a-uuid"))
        assertTrue(Riders.isRider("web-1234"))
        assertFalse(Riders.isRider("watch-1234"))
        assertFalse(Riders.isRider("web-1234~home"))
        assertTrue(Riders.isWatcher("watch-1234"))
    }

    @Test
    fun myPositionGoesToFamilyOnlyIfILetThem() {
        val ids = listOf("a", "web-b", "watch-c")
        // Nobody watching, or I allow it: everyone (empty list).
        assertEquals(emptyList<String>(), Riders.privateAudience(listOf("a", "web-b"), familyMayWatch = false))
        assertEquals(emptyList<String>(), Riders.privateAudience(ids, familyMayWatch = true))
        // Watched and not allowed: only the riders.
        assertEquals(listOf("a", "web-b"), Riders.privateAudience(ids + "x~home", familyMayWatch = false))
        // Only family here: nobody to send it to.
        assertNull(Riders.privateAudience(listOf("watch-c"), familyMayWatch = false))
    }
}
