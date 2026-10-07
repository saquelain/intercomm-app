package com.ridecomm.app.headset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PressCounterTest {

    @Test
    fun singlePress() {
        val c = PressCounter()
        c.press(0)
        assertNull(c.settle(300)) // still within the gap: might become a double press
        assertEquals(1, c.settle(600))
    }

    @Test
    fun doubleAndTriplePress() {
        val c = PressCounter()
        c.press(0)
        c.press(300)
        assertEquals(2, c.settle(900))
        c.press(2_000)
        c.press(2_350)
        c.press(2_700)
        assertEquals(3, c.settle(3_300))
    }

    @Test
    fun slowPressesAreSeparateGestures() {
        val c = PressCounter()
        c.press(0)
        assertEquals(1, c.settle(700))
        c.press(1_000)
        assertEquals(1, c.settle(1_700))
    }

    @Test
    fun nothingToSettleWithoutPresses() {
        assertNull(PressCounter().settle(10_000))
    }
}
