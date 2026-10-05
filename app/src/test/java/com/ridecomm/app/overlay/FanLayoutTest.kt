package com.ridecomm.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FanLayoutTest {

    private val deadZone = 50f

    private fun pointAt(fan: FanLayout, i: Int, distance: Float = 200f): Pair<Float, Float> {
        val a = Math.toRadians(fan.angleOf(i).toDouble())
        return (distance * cos(a)).toFloat() to (distance * sin(a)).toFloat()
    }

    @Test
    fun rightEdgeFanOpensLeftwardWithFirstOptionOnTop() {
        val fan = FanLayout(count = 4, onLeftEdge = false)
        val (x0, y0) = pointAt(fan, 0)
        val (x3, y3) = pointAt(fan, 3)
        assertTrue("options should be left of a right-edge button", x0 < 0 && x3 < 0)
        assertTrue("first option above, last below", y0 < 0 && y3 > 0)
    }

    @Test
    fun leftEdgeFanOpensRightward() {
        val fan = FanLayout(count = 3, onLeftEdge = true)
        (0 until 3).forEach { i -> assertTrue(pointAt(fan, i).first > 0) }
        assertTrue(pointAt(fan, 0).second < 0)
    }

    @Test
    fun slidingTowardEachOptionPicksIt() {
        for (count in 2..6) for (left in listOf(true, false)) {
            val fan = FanLayout(count, left)
            for (i in 0 until count) {
                val (dx, dy) = pointAt(fan, i)
                assertEquals("count=$count left=$left option=$i", i, fan.pick(dx, dy, deadZone))
            }
        }
    }

    @Test
    fun roughDirectionIsEnough() {
        // With gloves the finger drifts: 20° off target still picks the nearest option.
        val fan = FanLayout(count = 3, onLeftEdge = false) // options at 255°, 180°, 105°
        val a = Math.toRadians(200.0)
        assertEquals(1, fan.pick((150 * cos(a)).toFloat(), (150 * sin(a)).toFloat(), deadZone))
    }

    @Test
    fun nothingPickedInsideDeadZone() {
        val fan = FanLayout(count = 3, onLeftEdge = false)
        assertEquals(-1, fan.pick(-30f, 0f, deadZone))
    }

    @Test
    fun nothingPickedWhenSlidingAwayFromTheFan() {
        // Right-edge button: sliding right (off screen) points at no option.
        val fan = FanLayout(count = 3, onLeftEdge = false)
        assertEquals(-1, fan.pick(200f, 0f, deadZone))
    }
}
