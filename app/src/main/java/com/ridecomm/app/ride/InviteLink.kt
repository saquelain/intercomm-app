package com.ridecomm.app.ride

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Invite links. Sharing a ride gives an https link to a small page (GitHub Pages, docs/join/)
 * that opens the app with ridecomm://join/CODE, or offers the APK if it isn't installed.
 */
object InviteLink {
    private const val PAGE = "https://saquelain.github.io/intercomm-app/join/"

    private val _pending = MutableStateFlow<String?>(null)
    /** A ride code from a tapped invite, waiting for the home screen to join it. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun url(code: String) = "$PAGE?code=$code"

    fun shareText(code: String) = "Join my RideComm ride: ${url(code)}\nOr enter the code $code in the app."

    /** Picks up ridecomm://join/CODE from an incoming intent. */
    fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "ridecomm" || data.host != "join") return
        val code = RideCode.clean(data.lastPathSegment.orEmpty())
        if (code.length == RideCode.LENGTH) _pending.value = code
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }
}
