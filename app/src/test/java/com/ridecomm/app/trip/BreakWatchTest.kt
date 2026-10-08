package com.ridecomm.app.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakWatchTest {
    private val min = 60_000L

    @Test
    fun remindsAfterTheIntervalThenEveryHalfHour() {
        val w = BreakWatch(120)
        assertFalse(w.update(nowMs = 119 * min, ridingMs = 119 * min))
        assertTrue(w.update(nowMs = 120 * min, ridingMs = 120 * min))
        assertEquals(120 * min, w.sinceBreakMs)
        // Not again straight away.
        assertFalse(w.update(nowMs = 140 * min, ridingMs = 140 * min))
        assertTrue(w.update(nowMs = 150 * min, ridingMs = 150 * min))
    }

    @Test
    fun aPassedBreakVoteStartsAgain() {
        val w = BreakWatch(60)
        w.update(50 * min, 50 * min)
        w.tookBreak()
        assertFalse(w.update(100 * min, 100 * min))
        assertTrue(w.update(110 * min, 110 * min))
    }

    @Test
    fun aTenMinuteStopCountsAsABreak() {
        val w = BreakWatch(60)
        w.update(nowMs = 50 * min, ridingMs = 50 * min)
        // Stopped: riding time stays at 50 minutes while the clock goes on.
        w.update(nowMs = 52 * min, ridingMs = 50 * min)
        w.update(nowMs = 62 * min, ridingMs = 50 * min)
        assertEquals(0L, w.sinceBreakMs)
        // An hour of riding after the stop before the next reminder.
        assertFalse(w.update(nowMs = 100 * min, ridingMs = 100 * min))
        assertTrue(w.update(nowMs = 120 * min, ridingMs = 110 * min))
    }

    @Test
    fun aShortStopDoesNot() {
        val w = BreakWatch(60)
        w.update(nowMs = 50 * min, ridingMs = 50 * min)
        w.update(nowMs = 55 * min, ridingMs = 50 * min)
        assertTrue(w.update(nowMs = 66 * min, ridingMs = 61 * min))
    }

    @Test
    fun offNeverReminds() {
        val w = BreakWatch(0)
        assertFalse(w.update(500 * min, 500 * min))
    }

    @Test
    fun spokenReminder() {
        assertEquals("You've been riding for 2 hours. Time for a break?", BreakWatch.spoken(120 * min))
        assertEquals("You've been riding for 1 hour 30 minutes. Time for a break?", BreakWatch.spoken(90 * min))
    }
}
