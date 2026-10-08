package com.ridecomm.app.hazard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardLogicTest {
    // Riding north up a straight road; 0.001° of latitude is about 111 m.
    private val myLat = 12.9000
    private val myLon = 77.6000

    @Test
    fun aheadOnlyAlongMyDirection() {
        assertEquals(true, HazardLogic.isAhead(myLat, myLon, 0.0, myLat + 0.003, myLon))
        assertEquals(false, HazardLogic.isAhead(myLat, myLon, 0.0, myLat - 0.003, myLon))
        // Off to the side (a parallel road): not ahead.
        assertEquals(false, HazardLogic.isAhead(myLat, myLon, 0.0, myLat + 0.001, myLon + 0.003))
        // Standing still: can't tell.
        assertNull(HazardLogic.isAhead(myLat, myLon, null, myLat + 0.003, myLon))
        // Heading 350° and the hazard due north: still ahead (angles wrap round).
        assertEquals(true, HazardLogic.isAhead(myLat, myLon, 350.0, myLat + 0.003, myLon))
    }

    @Test
    fun warnsWithinRangeAheadOnly() {
        assertTrue(HazardLogic.shouldWarn(330.0, ahead = true))
        assertFalse(HazardLogic.shouldWarn(800.0, ahead = true)) // too far yet
        assertFalse(HazardLogic.shouldWarn(20.0, ahead = true)) // on it already
        assertFalse(HazardLogic.shouldWarn(300.0, ahead = false)) // passed it
        assertFalse(HazardLogic.shouldWarn(300.0, ahead = null))
    }

    @Test
    fun warningText() {
        assertEquals("Pothole in 330 meters", HazardLogic.warning(HazardKind.POTHOLE, 333.0))
        assertEquals("Police check in 500 meters", HazardLogic.warning(HazardKind.POLICE, 498.0))
    }

    @Test
    fun sameSpotConfirmsInsteadOfAddingAnother() {
        val existing = Hazard("a", HazardKind.POTHOLE, myLat, myLon, "r", "Rahul", 0)
        assertEquals(existing, HazardLogic.sameSpot(listOf(existing), HazardKind.POTHOLE, myLat + 0.0003, myLon))
        assertNull(HazardLogic.sameSpot(listOf(existing), HazardKind.POLICE, myLat, myLon))
        assertNull(HazardLogic.sameSpot(listOf(existing), HazardKind.POTHOLE, myLat + 0.002, myLon))
    }

    @Test
    fun expiry() {
        val police = Hazard("p", HazardKind.POLICE, myLat, myLon, "r", "Rahul", 0)
        assertFalse(police.expired(29 * 60_000L))
        assertTrue(police.expired(31 * 60_000L))
        val pothole = police.copy(kind = HazardKind.POTHOLE)
        assertFalse(pothole.expired(90 * 60_000L))
    }
}
