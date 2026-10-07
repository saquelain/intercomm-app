package com.ridecomm.app.voice

import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.VoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test
    fun needsTheWakeWordFirst() {
        assertNull(p("let's take a break"))
        assertNull(p("I think RideComm break")) // wake word not at the start
        assertNull(p(""))
    }

    @Test
    fun acceptsRecogniserSpellings() {
        val expected = VoiceCommand.StartVote(VoteKind.BREAK)
        listOf("RideComm break", "ride comm, break", "Ride com break", "ride calm break", "Hey RideComm, take a break", "right come break")
            .forEach { assertEquals(it, expected, p(it)) }
    }

    @Test
    fun votes() {
        assertEquals(VoiceCommand.StartVote(VoteKind.FUEL), p("RideComm vote fuel"))
        assertEquals(VoiceCommand.StartVote(VoteKind.FUEL), p("RideComm petrol stop"))
        assertEquals(VoiceCommand.StartVote(VoteKind.FOOD), p("RideComm I'm hungry"))
        assertEquals(VoiceCommand.StartVote(VoteKind.BREAK), p("RideComm chai"))
        assertEquals(VoiceCommand.Cast(true), p("RideComm yes"))
        assertEquals(VoiceCommand.Cast(false), p("RideComm no"))
    }

    @Test
    fun messagesAndControls() {
        assertEquals(VoiceCommand.Quick(QuickMessage.SLOW_DOWN), p("RideComm slow down"))
        assertEquals(VoiceCommand.Quick(QuickMessage.WAIT), p("RideComm wait for me"))
        assertEquals(VoiceCommand.Mute(true), p("RideComm mute"))
        assertEquals(VoiceCommand.Mute(false), p("RideComm unmute"))
        assertEquals(VoiceCommand.NextSong, p("RideComm next song"))
        assertEquals(VoiceCommand.Music(false), p("RideComm stop music"))
        assertEquals(VoiceCommand.Music(true), p("RideComm music on"))
        assertEquals(VoiceCommand.WhoIsHere, p("RideComm who's here"))
        assertEquals(VoiceCommand.Battery, p("RideComm battery"))
        assertEquals(VoiceCommand.Trip, p("RideComm speed"))
        assertEquals(VoiceCommand.Trip, p("RideComm how far have we gone"))
    }

    @Test
    fun safetyCommandsWin() {
        assertEquals(VoiceCommand.Sos, p("RideComm SOS"))
        assertEquals(VoiceCommand.Sos, p("RideComm help"))
        assertEquals(VoiceCommand.Cancel, p("RideComm cancel"))
        assertEquals(VoiceCommand.Cancel, p("RideComm I'm OK"))
        // "stop music" is about music, not a break stop.
        assertEquals(VoiceCommand.Music(false), p("RideComm stop the music"))
    }

    @Test
    fun unknownAfterWakeWord() {
        assertEquals(VoiceCommand.Unknown, p("RideComm"))
        assertEquals(VoiceCommand.Unknown, p("RideComm what's the weather"))
    }
}
