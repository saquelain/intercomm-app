package com.ridecomm.app.ride

enum class RideStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING }

enum class Signal { GOOD, WEAK, LOST, UNKNOWN }

data class Rider(
    val id: String,
    val name: String,
    val isMe: Boolean,
    val isSpeaking: Boolean,
    val isMuted: Boolean,
    val signal: Signal,
)

data class RideState(
    val status: RideStatus = RideStatus.IDLE,
    val code: String = "",
    val riders: List<Rider> = emptyList(),
    val micMuted: Boolean = false,
    /** Set when a ride fails to start or drops; shown once on the home screen. */
    val error: String? = null,
)
