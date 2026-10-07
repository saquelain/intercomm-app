package com.ridecomm.app.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupMathTest {

    // Two points on the Mumbai–Pune expressway, ~1.1 km apart, the second to the east-south-east.
    private val a = 18.9894 to 73.1175
    private val b = 18.9870 to 73.1275

    @Test
    fun distanceMatchesKnownValue() {
        val d = GroupMath.distanceM(a.first, a.second, b.first, b.second)
        assertEquals(1_090.0, d, 30.0)
    }

    @Test
    fun bearingPointsTheRightWay() {
        assertEquals(0.0, GroupMath.bearingDeg(0.0, 0.0, 1.0, 0.0), 0.01) // north
        assertEquals(90.0, GroupMath.bearingDeg(0.0, 0.0, 0.0, 1.0), 0.01) // east
        assertEquals(180.0, GroupMath.bearingDeg(1.0, 0.0, 0.0, 0.0), 0.01) // south
    }

    @Test
    fun aheadOrBehindDependsOnMyHeading() {
        // Rider due east of me.
        assertEquals(Relation.AHEAD, GroupMath.relation(800.0, myHeadingDeg = 80.0, bearingToThemDeg = 90.0))
        assertEquals(Relation.BEHIND, GroupMath.relation(800.0, myHeadingDeg = 270.0, bearingToThemDeg = 90.0))
        // Heading wraps around north.
        assertEquals(Relation.AHEAD, GroupMath.relation(800.0, myHeadingDeg = 350.0, bearingToThemDeg = 20.0))
    }

    @Test
    fun closeOrStandingStillIsNearby() {
        assertEquals(Relation.NEARBY, GroupMath.relation(100.0, myHeadingDeg = 0.0, bearingToThemDeg = 180.0))
        assertEquals(Relation.NEARBY, GroupMath.relation(900.0, myHeadingDeg = null, bearingToThemDeg = 180.0))
    }

    @Test
    fun formatsDistances() {
        assertEquals("350 m", GroupMath.shortDistance(347.0))
        assertEquals("1.2 km", GroupMath.shortDistance(1_240.0))
        assertEquals("1.2 kilometers", GroupMath.spokenDistance(1_240.0))
    }

    @Test
    fun separationAlertsOncePerKilometreAndWhenBack() {
        val t = SeparationTracker()
        assertNull(t.update("r", 400.0))
        assertEquals(SeparationTracker.Alert.Separated(1_100.0), t.update("r", 1_100.0))
        assertNull(t.update("r", 1_300.0)) // same kilometre: no repeat
        assertNull(t.update("r", 980.0)) // jitter below 1 km: no "back" yet
        assertEquals(SeparationTracker.Alert.Separated(2_050.0), t.update("r", 2_050.0))
        assertEquals(SeparationTracker.Alert.BackTogether, t.update("r", 420.0))
        assertNull(t.update("r", 300.0))
    }
}
