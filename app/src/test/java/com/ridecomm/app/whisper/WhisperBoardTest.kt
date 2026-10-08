package com.ridecomm.app.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperBoardTest {
    private val me = "me"

    @Test
    fun othersAreSilentOnlyForRidersNotTalkedTo() {
        val b = WhisperBoard()
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = true, nowMs = 0)
        // I'm not Bilal: Asha is silent for me.
        assertEquals(setOf("asha"), b.hushed(me, 100))
        // Bilal hears her and sees who's talking to him.
        assertEquals(emptySet<String>(), b.hushed("bilal", 100))
        assertEquals("Asha", b.toMe("bilal", 100)?.fromName)
        assertNull(b.toMe(me, 100))
    }

    @Test
    fun endingKeepsTheSenderSilentAMomentLonger() {
        val b = WhisperBoard(timeoutMs = 5_000, tailMs = 700)
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = true, nowMs = 0)
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = false, nowMs = 3_000)
        assertEquals(emptyList<WhisperView>(), b.active(3_100))
        // Her last words are still arriving: keep her quiet for the tail.
        assertEquals(setOf("asha"), b.hushed(me, 3_500))
        assertEquals(emptySet<String>(), b.hushed(me, 3_800))
        assertFalse(b.busy(3_800))
    }

    @Test
    fun aDroppedSenderTimesOut() {
        val b = WhisperBoard(timeoutMs = 5_000, tailMs = 700)
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = true, nowMs = 0)
        // Repeats keep it going.
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = true, nowMs = 2_000)
        assertEquals(setOf("asha"), b.hushed(me, 6_500))
        // No repeat for longer than the timeout: she's heard again.
        assertEquals(emptySet<String>(), b.hushed(me, 7_800))
        assertTrue(b.active(7_100).isEmpty())
    }

    @Test
    fun forgettingARiderUnsilencesThem() {
        val b = WhisperBoard()
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = true, nowMs = 0)
        b.forget("asha")
        assertEquals(emptySet<String>(), b.hushed(me, 10))
        assertFalse(b.busy(10))
    }

    @Test
    fun anEndWithoutAStartIsIgnored() {
        val b = WhisperBoard()
        b.onMessage("asha", "Asha", "bilal", "Bilal", on = false, nowMs = 0)
        assertEquals(emptySet<String>(), b.hushed(me, 10))
    }
}
