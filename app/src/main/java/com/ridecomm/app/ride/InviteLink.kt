package com.ridecomm.app.ride

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ridecomm.app.Prefs
import com.ridecomm.app.plan.RidePlans
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

    /**
     * With rides locked to the group, the link carries the group key after "#", so riders who tap it
     * get in without typing it (the part after "#" never reaches the web server).
     */
    fun url(code: String, groupKey: String = "", plan: String = ""): String {
        // A ride plan (see RidePlans) also travels after "#", next to the key.
        val hash = listOfNotNull(
            plan.takeIf { it.isNotBlank() }?.let { "p=$it" },
            groupKey.takeIf { it.isNotBlank() }?.let { "k=" + Uri.encode(it) },
        ).joinToString("&")
        return "$PAGE?code=$code" + if (hash.isNotEmpty()) "#$hash" else ""
    }

    /** The group key to put in links, when rides are locked to the group. */
    fun groupKeyToShare(context: Context): String =
        if (Prefs.activeRideServer(context).isNotBlank()) Prefs.groupKey(context) else ""

    private const val WATCH_PAGE = "https://saquelain.github.io/intercomm-app/watch/"

    /** The family's link to follow the ride on a map (watch page; the group key travels after "#"). */
    fun familyUrl(code: String, groupKey: String = "") =
        "$WATCH_PAGE?code=$code" + if (groupKey.isNotBlank()) "#k=" + Uri.encode(groupKey) else ""

    fun familyText(context: Context, code: String) =
        "Follow our ride live on a map: " +
            familyUrl(code, groupKeyToShare(context))

    fun shareText(code: String, groupKey: String = "") =
        "Join my RideComm ride: ${url(code, groupKey)}\nOr enter the code $code in the app. On iPhone the link opens it in the browser, no app needed."

    /** The invite link for my ride, with the group key when rides are locked to the group. */
    fun shareText(context: Context, code: String) = shareText(code, groupKeyToShare(context))

    /**
     * Picks up ridecomm://join/CODE (and ?k=GROUPKEY) from an incoming intent. With a [context] the
     * group key is saved, so the private ride server lets this rider in.
     */
    fun handle(intent: Intent?, context: Context? = null) {
        val data = intent?.data ?: return
        if (data.scheme != "ridecomm" || data.host != "join") return
        val code = RideCode.clean(data.lastPathSegment.orEmpty())
        if (code.length != RideCode.LENGTH) return
        val key = keyFrom(data)
        if (context != null) {
            runCatching { data.getQueryParameter("p") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { RidePlans.fromLink(context, code, it) }
        }
        if (context != null && key != null) {
            Prefs.setGroupKey(context, key)
            if (Prefs.rideServerUrl(context).isNotBlank()) Prefs.setPrivateServer(context, true)
        }
        _pending.value = code
    }

    /** The group key in an invite link, if it has one. */
    fun keyFrom(data: Uri): String? = runCatching { data.getQueryParameter("k") }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    fun consume(): String? = _pending.value.also { _pending.value = null }
}
