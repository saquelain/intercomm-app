package com.ridecomm.app.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    @Test
    fun startingAtHomeDoesNotCount() {
        val a = HomeArrival()
        assertFalse(a.update(20.0))
        assertFalse(a.update(600.0))
        assertFalse(a.update(50.0))
    }

    @Test
    fun arrivingAfterBeingAwayCountsOnce() {
        val a = HomeArrival()
        assertFalse(a.update(30_000.0))
        assertFalse(a.update(800.0))
        assertTrue(a.update(150.0))
        assertFalse(a.update(20.0))
    }

    @Test
    fun checkInIdentities() {
        val id = HomeLogic.checkInIdentity("device-1")
        assertTrue(HomeLogic.isCheckIn(id))
        assertFalse(HomeLogic.isCheckIn("device-1"))
        assertEquals("device-1", HomeLogic.riderOf(id))
        assertEquals("web-2", HomeLogic.riderOf("web-2"))
    }
}
