package com.ridecomm.app.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleLogicTest {
    @Test
    fun alongTheDirectionOfTravel() {
        // Lead riding north; a rider 0.003° north (~333 m) is ahead, south is behind, east is beside.
        assertEquals(333.0, RoleLogic.alongM(12.9, 77.6, 0.0, 12.903, 77.6), 3.0)
        assertEquals(-333.0, RoleLogic.alongM(12.9, 77.6, 0.0, 12.897, 77.6), 3.0)
        assertEquals(0.0, RoleLogic.alongM(12.9, 77.6, 0.0, 12.9, 77.603), 3.0)
        // Riding east: the rider to the east is ahead.
        assertTrue(RoleLogic.alongM(12.9, 77.6, 90.0, 12.9, 77.603) > 300)
    }

    @Test
    fun newestRolesWin() {
        val a = RideRolesState("x", null, 5, "a")
        assertTrue(RoleLogic.newer(RideRolesState(), a))
        assertFalse(RoleLogic.newer(a, a.copy(atMs = 4)))
        assertTrue(RoleLogic.newer(a, a.copy(byId = "b")))
    }

    @Test
    fun aheadOfLeadOnceUntilBackInPlace() {
        val w = RoleWatch()
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", 200.0, null))
        assertEquals(listOf(RoleWatch.Alert.AHEAD_OF_LEAD), w.check("v", 350.0, null))
        // Still ahead, or wobbling: no repeat.
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", 600.0, null))
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", 250.0, null))
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", 350.0, null))
        // Dropped back behind the lead, then got ahead again: a new alert.
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", -50.0, null))
        assertEquals(listOf(RoleWatch.Alert.AHEAD_OF_LEAD), w.check("v", 400.0, null))
    }

    @Test
    fun behindSweep() {
        val w = RoleWatch()
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", null, -300.0))
        assertEquals(listOf(RoleWatch.Alert.BEHIND_SWEEP), w.check("v", null, -650.0))
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", null, -900.0))
        assertEquals(emptyList<RoleWatch.Alert>(), w.check("v", null, 20.0)) // caught up
        assertEquals(listOf(RoleWatch.Alert.BEHIND_SWEEP), w.check("v", null, -700.0))
        // Each rider is tracked separately.
        assertEquals(listOf(RoleWatch.Alert.BEHIND_SWEEP), w.check("k", null, -700.0))
    }
}
