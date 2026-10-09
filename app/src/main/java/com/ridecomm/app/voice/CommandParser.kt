package com.ridecomm.app.voice

import com.ridecomm.app.hazard.HazardKind
import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.VoteKind

/** Something a rider can ask for by voice. */
sealed interface VoiceCommand {
    data class StartVote(val kind: VoteKind) : VoiceCommand
    data class Cast(val yes: Boolean) : VoiceCommand
    data class Quick(val message: QuickMessage) : VoiceCommand
    data class Mute(val muted: Boolean) : VoiceCommand
    data object NextSong : VoiceCommand
    data class Music(val on: Boolean) : VoiceCommand
    data object Sos : VoiceCommand
    data object Cancel : VoiceCommand
    data object WhoIsHere : VoiceCommand
    data object Battery : VoiceCommand
    data object Trip : VoiceCommand
    data object WhereIsEveryone : VoiceCommand
    data object RegroupHere : VoiceCommand
    data class Hazard(val kind: HazardKind) : VoiceCommand
    /** "Low fuel": guide me to the next petrol pump ahead. */
    data object LowFuel : VoiceCommand
    /** Said "RideComm" but nothing we know after it. */
    data object Unknown : VoiceCommand
}

/**
 * Turns recognised speech into a command. Only speech that starts with the wake word counts
 * ("RideComm, vote break"), so normal chat never triggers anything. Speech recognisers spell
 * "RideComm" many ways, so several spellings are accepted.
 */
object CommandParser {
    /** Ways recognisers write "RideComm", as normalised word lists. */
    private val WAKE_WORDS = listOf(
        "ridecomm", "ridecom", "ride comm", "ride com", "ride come", "ride calm", "ride kom", "ride comb",
        "right comm", "right com", "right come", "ride common", "ride command", "write comm", "ride call",
    ).map { it.split(' ') }

    /** Words the recogniser is nudged towards. */
    val BIASING = listOf(
        "RideComm", "break", "fuel", "low fuel", "food", "yes", "no", "slow down", "wait for me", "mute", "unmute",
        "next song", "music off", "music on", "SOS", "cancel", "who's here", "battery", "speed", "how far", "where is everyone", "regroup here",
        "pothole", "speed breaker", "slippery", "police", "accident", "animal",
    )

    /** Rules in priority order: the first whose phrase appears in the command wins. */
    private val RULES: List<Pair<List<String>, VoiceCommand>> = listOf(
        listOf("cancel", "stop sos", "i'm ok", "i am ok", "im ok", "false alarm") to VoiceCommand.Cancel,
        listOf("sos", "s o s", "emergency", "help") to VoiceCommand.Sos,
        // Hazards before votes: "police stop ahead" is a hazard, not a break vote.
        listOf("pothole", "pot hole", "potholes", "crater") to VoiceCommand.Hazard(HazardKind.POTHOLE),
        listOf("speed breaker", "speed breakers", "speed bump", "bump", "breaker") to VoiceCommand.Hazard(HazardKind.SPEED_BREAKER),
        listOf("slippery", "sand", "gravel", "wet road", "slippery road") to VoiceCommand.Hazard(HazardKind.SLIPPERY),
        listOf("police", "cops", "cop", "police check", "checkpoint") to VoiceCommand.Hazard(HazardKind.POLICE),
        listOf("accident", "crash ahead") to VoiceCommand.Hazard(HazardKind.ACCIDENT),
        listOf("animal", "animals", "cow", "cows", "dog", "dogs", "cattle", "buffalo", "goat", "goats") to VoiceCommand.Hazard(HazardKind.ANIMAL),
        listOf("regroup", "re group", "meet here", "meeting point") to VoiceCommand.RegroupHere,
        listOf("where is everyone", "where's everyone", "wheres everyone", "where is everybody", "where's everybody", "where is the group", "where are", "where is", "where's", "wheres") to VoiceCommand.WhereIsEveryone,
        listOf("next song", "next", "skip") to VoiceCommand.NextSong,
        listOf("music off", "stop music", "stop the music", "no music", "pause music") to VoiceCommand.Music(on = false),
        listOf("music on", "play music", "start music", "resume music") to VoiceCommand.Music(on = true),
        listOf("unmute", "un mute", "mic on") to VoiceCommand.Mute(muted = false),
        listOf("mute", "mic off") to VoiceCommand.Mute(muted = true),
        // Before the Fuel vote: "low fuel" is about my tank, not a stop for everyone.
        listOf("low fuel", "low petrol", "fuel low", "petrol low", "reserve", "need petrol", "need fuel", "fuel is low", "petrol is low") to VoiceCommand.LowFuel,
        listOf("fuel", "petrol", "gas", "diesel") to VoiceCommand.StartVote(VoteKind.FUEL),
        listOf("food", "lunch", "dinner", "breakfast", "eat", "hungry") to VoiceCommand.StartVote(VoteKind.FOOD),
        listOf("break", "tea", "chai", "coffee", "rest", "stop") to VoiceCommand.StartVote(VoteKind.BREAK),
        listOf("slow down", "slow") to VoiceCommand.Quick(QuickMessage.SLOW_DOWN),
        listOf("wait for me", "wait") to VoiceCommand.Quick(QuickMessage.WAIT),
        listOf("who's here", "who is here", "whos here", "who's on", "who is on", "riders", "who") to VoiceCommand.WhoIsHere,
        listOf("battery", "batteries") to VoiceCommand.Battery,
        listOf("speed", "how fast", "distance", "how far", "stats", "status", "update") to VoiceCommand.Trip,
        listOf("yes", "yeah", "yep", "agree", "okay", "ok") to VoiceCommand.Cast(yes = true),
        listOf("no", "nope", "nah", "disagree") to VoiceCommand.Cast(yes = false),
    )

    /** Null when the speech didn't start with the wake word. */
    fun parse(speech: String): VoiceCommand? {
        val words = normalise(speech)
        val start = words.indexOfFirstWake() ?: return null
        val rest = words.drop(start).joinToString(" ")
        if (rest.isBlank()) return VoiceCommand.Unknown
        val padded = " $rest "
        return RULES.firstOrNull { (phrases, _) -> phrases.any { padded.contains(" $it ") } }?.second
            ?: VoiceCommand.Unknown
    }

    private fun normalise(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").split(' ').filter { it.isNotBlank() }

    /**
     * Index of the first word after the wake word, if the wake word comes first (optionally after
     * "hey"/"ok"/"okay").
     */
    private fun List<String>.indexOfFirstWake(): Int? {
        val skip = if (firstOrNull() in setOf("hey", "hi", "ok", "okay")) 1 else 0
        for (wake in WAKE_WORDS) {
            if (size >= skip + wake.size && subList(skip, skip + wake.size) == wake) return skip + wake.size
        }
        return null
    }
}
