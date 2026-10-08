package com.ridecomm.app.ride

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkButtonTest {
    @Test
    fun holdToTalk() {
        val b = TalkButton()
        b.press(0)
        assertTrue(b.talking)
        b.release(2_000)
        assertFalse(b.talking)
        assertFalse(b.latched)
    }

    @Test
    fun tapLocksTapAgainStops() {
        val b = TalkButton()
        b.press(0)
        b.release(150)
        assertTrue(b.talking)
        assertTrue(b.latched)
        // Holding while locked keeps talking; letting go ends it.
        b.press(10_000)
        assertTrue(b.talking)
        b.release(10_100)
        assertFalse(b.talking)
        assertFalse(b.latched)
    }

    @Test
    fun toggleFromHeadset() {
        val b = TalkButton()
        b.toggle(0)
        assertTrue(b.talking && b.latched)
        b.toggle(5_000)
        assertFalse(b.talking)
    }

    @Test
    fun forgottenOpenMicClosesItself() {
        val b = TalkButton(maxLatchedMs = 60_000)
        b.toggle(0)
        assertFalse(b.timedOut(59_000))
        assertTrue(b.timedOut(61_000))
        assertFalse(b.talking)
        // Holding (not locked) never times out.
        b.press(100_000)
        assertFalse(b.timedOut(500_000))
        assertTrue(b.talking)
    }
}
