package com.ridecomm.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RingLayoutTest {

    private val rings = RingLayout(inner = 3, outer = 5, onLeftEdge = false, innerRadius = 125f, outerRadius = 235f, deadZone = 56f)

    private fun at(i: Int, distance: Float): Int {
        val a = Math.toRadians(rings.angleOf(i).toDouble())
        return rings.pick((distance * cos(a)).toFloat(), (distance * sin(a)).toFloat())
    }

    @Test
    fun shortSlidePicksInnerRing() {
        for (i in 0 until 3) assertEquals(i, at(i, 125f))
    }

    @Test
    fun longSlidePicksOuterRing() {
        for (i in 3 until 8) assertEquals(i, at(i, 235f))
    }

    @Test
    fun overshootingStillPicksOuterRing() {
        assertEquals(5, at(5, 400f))
    }

    @Test
    fun deadZonePicksNothing() {
        assertEquals(-1, at(0, 30f))
    }

    @Test
    fun outerOnlyMenuWorksAtAnyDistance() {
        val outerOnly = RingLayout(inner = 0, outer = 2, onLeftEdge = true, innerRadius = 125f, outerRadius = 235f, deadZone = 56f)
        val a = Math.toRadians(outerOnly.angleOf(1).toDouble())
        assertEquals(1, outerOnly.pick((100 * cos(a)).toFloat(), (100 * sin(a)).toFloat()))
    }
}
