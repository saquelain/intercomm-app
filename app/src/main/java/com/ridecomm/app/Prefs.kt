package com.ridecomm.app

import android.content.Context
import java.util.UUID

/** Small on-device settings: rider name, a stable device id, and the LiveKit token server id. */
object Prefs {
    private const val FILE = "ridecomm"
    private const val KEY_NAME = "rider_name"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_TOKEN_SERVER_ID = "token_server_id"

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
}
