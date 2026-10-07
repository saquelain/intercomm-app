package com.ridecomm.app.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegroupLogicTest {
    private fun point(id: String, at: Long, arrived: Set<String> = emptySet()) =
        RegroupPoint(id, 12.9, 77.6, "Dhaba", "Rahul", "r", at, arrived)

    @Test
    fun newerPointWins() {
        assertTrue(RegroupLogic.newer(null, point("a", 1)))
        assertTrue(RegroupLogic.newer(point("a", 1), point("b", 2)))
        assertFalse(RegroupLogic.newer(point("b", 2), point("a", 1)))
        // Same moment: every phone picks the same one.
        assertTrue(RegroupLogic.newer(point("a", 5), point("b", 5)))
        assertFalse(RegroupLogic.newer(point("b", 5), point("a", 5)))
    }

    @Test
    fun arrivalHasAMarginAgainstGpsWobble() {
        assertFalse(RegroupLogic.isAt(200.0, wasAt = false))
        assertTrue(RegroupLogic.isAt(120.0, wasAt = false))
        // Once there, wandering to 300 m (parking, the far end of the dhaba) still counts.
        assertTrue(RegroupLogic.isAt(300.0, wasAt = true))
        assertFalse(RegroupLogic.isAt(450.0, wasAt = true))
    }

    @Test
    fun everyoneThere() {
        val p = point("a", 1, arrived = setOf("me", "r", "v"))
        assertTrue(RegroupLogic.everyoneThere(p, listOf("me", "r", "v")))
        assertFalse(RegroupLogic.everyoneThere(p, listOf("me", "r", "v", "a")))
        assertFalse(RegroupLogic.everyoneThere(p, emptyList()))
    }

    @Test
    fun spokenWhere() {
        val p = point("a", 1)
        assertEquals("Dhaba, 4.2 kilometers ahead", RegroupLogic.spokenWhere(p, 4_200.0, Relation.AHEAD))
        assertEquals("Dhaba, 350 meters away", RegroupLogic.spokenWhere(p, 348.0, Relation.NEARBY))
        assertEquals("Dhaba", RegroupLogic.spokenWhere(p, null, null))
    }
}
