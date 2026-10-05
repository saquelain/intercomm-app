package com.ridecomm.app

import android.content.Context
import java.util.UUID

/** Small on-device settings: rider name, a stable device id, and the LiveKit token server id. */
object Prefs {
    private const val FILE = "ridecomm"
    private const val KEY_NAME = "rider_name"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_TOKEN_SERVER_ID = "token_server_id"
    private const val KEY_BUBBLE_ENABLED = "bubble_enabled"
    private const val KEY_BUBBLE_Y = "bubble_y"
    private const val KEY_BUBBLE_LEFT = "bubble_left"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun riderName(context: Context): String = prefs(context).getString(KEY_NAME, "") ?: ""

    fun setRiderName(context: Context, name: String) =
        prefs(context).edit().putString(KEY_NAME, name).apply()

    /** Stable per-install id, so a rider who rejoins replaces their old connection instead of showing twice. */
    fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /** The id typed in settings wins; otherwise the one baked into the APK at build time. */
    fun tokenServerId(context: Context): String =
        prefs(context).getString(KEY_TOKEN_SERVER_ID, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_TOKEN_SERVER_ID

    fun setTokenServerId(context: Context, id: String) =
        prefs(context).edit().putString(KEY_TOKEN_SERVER_ID, id.trim()).apply()

    /** Whether the floating ride button shows over other apps during a ride. */
    fun bubbleEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BUBBLE_ENABLED, true)

    fun setBubbleEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply()

    fun bubbleY(context: Context): Int? =
        prefs(context).getInt(KEY_BUBBLE_Y, -1).takeIf { it >= 0 }

    fun bubbleOnLeft(context: Context): Boolean = prefs(context).getBoolean(KEY_BUBBLE_LEFT, false)

    fun setBubblePosition(context: Context, y: Int, onLeft: Boolean) =
        prefs(context).edit().putInt(KEY_BUBBLE_Y, y).putBoolean(KEY_BUBBLE_LEFT, onLeft).apply()
}
