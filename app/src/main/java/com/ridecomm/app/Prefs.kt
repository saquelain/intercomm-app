package com.ridecomm.app

import android.content.Context
import com.ridecomm.app.audio.GateSettings
import com.ridecomm.app.ride.RecentRide
import com.ridecomm.app.ride.RecentRides
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
    private const val KEY_EMERGENCY_NUMBERS = "emergency_numbers"
    // New key so the feature starts switched off for everyone while it's still being built.
    private const val KEY_SHARE_LOCATION = "share_location_beta"
    private const val KEY_CRASH_DETECTION = "crash_detection"
    private const val KEY_RIDE_SERVER = "ride_server_url"
    private const val KEY_HEADSET_BUTTONS = "headset_buttons"
    private const val KEY_GROUP_KEY = "group_key"
    private const val KEY_SPEAKER_OVERLAY = "speaker_overlay"
    private const val KEY_KEEP_OTHER_MUSIC = "keep_other_music"
    private const val KEY_WIND_GATE = "wind_gate"
    private const val KEY_GATE_THRESHOLD = "gate_threshold_db"
    private const val KEY_GATE_RUMBLE = "gate_rumble_db"
    private const val KEY_GATE_HOLD = "gate_hold_ms"
    private const val KEY_GATE_REDUCTION = "gate_reduction_db"
    private const val KEY_RECENT_RIDES = "recent_rides"
    private const val KEY_UNFINISHED_RIDE = "unfinished_ride"
    private const val KEY_RIDER_ALERTS = "rider_alerts"
    private const val KEY_VOICE_COMMANDS = "voice_commands"

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

    /** Phone numbers that get an SOS by SMS when there's no internet, as typed ("98…, +91 99…"). */
    fun emergencyNumbers(context: Context): String = prefs(context).getString(KEY_EMERGENCY_NUMBERS, "") ?: ""

    fun setEmergencyNumbers(context: Context, numbers: String) =
        prefs(context).edit().putString(KEY_EMERGENCY_NUMBERS, numbers.trim()).apply()

    /** Share my GPS position with the group during rides (distance and separation alerts). */
    fun shareLocation(context: Context): Boolean = prefs(context).getBoolean(KEY_SHARE_LOCATION, false)

    fun setShareLocation(context: Context, share: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHARE_LOCATION, share).apply()

    /**
     * Let Spotify / YouTube Music keep playing during rides (turned down while someone talks)
     * instead of pausing them like a phone call does.
     */
    fun keepOtherMusic(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_OTHER_MUSIC, true)

    fun setKeepOtherMusic(context: Context, keep: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_OTHER_MUSIC, keep).apply()

    /** Start the SOS countdown automatically after a hard impact followed by no movement. */
    fun crashDetection(context: Context): Boolean = prefs(context).getBoolean(KEY_CRASH_DETECTION, true)

    fun setCrashDetection(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_CRASH_DETECTION, on).apply()

    /** Show who is talking at the top-left corner while another app (e.g. Maps) is open. */
    fun speakerOverlay(context: Context): Boolean = prefs(context).getBoolean(KEY_SPEAKER_OVERLAY, true)

    fun setSpeakerOverlay(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_SPEAKER_OVERLAY, on).apply()

    /** Private ride server address; when set it's used instead of the LiveKit development token server. */
    fun rideServerUrl(context: Context): String =
        prefs(context).getString(KEY_RIDE_SERVER, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_RIDE_SERVER_URL

    fun setRideServerUrl(context: Context, url: String) =
        prefs(context).edit().putString(KEY_RIDE_SERVER, url.trim()).apply()

    /** The group's shared secret; the private ride server only lets in riders who know it. */
    fun groupKey(context: Context): String = prefs(context).getString(KEY_GROUP_KEY, "") ?: ""

    fun setGroupKey(context: Context, key: String) =
        prefs(context).edit().putString(KEY_GROUP_KEY, key.trim()).apply()

    /** Ready to ride: either the private server with a group key, or the development token server. */
    fun serverConfigured(context: Context): Boolean =
        if (rideServerUrl(context).isNotBlank()) groupKey(context).isNotBlank() else tokenServerId(context).isNotBlank()

    /** The helmet headset's play/pause button controls the ride (mute, music, SOS). */
    fun headsetButtons(context: Context): Boolean = prefs(context).getBoolean(KEY_HEADSET_BUTTONS, true)

    fun setHeadsetButtons(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_HEADSET_BUTTONS, on).apply()

    fun setBubblePosition(context: Context, y: Int, onLeft: Boolean) =
        prefs(context).edit().putInt(KEY_BUBBLE_Y, y).putBoolean(KEY_BUBBLE_LEFT, onLeft).apply()

    /** The wind filter's settings; null means off. */
    fun windGate(context: Context): GateSettings? {
        val p = prefs(context)
        // Older versions stored just a preset name (or OFF).
        val mode = p.getString(KEY_WIND_GATE, GateSettings.Preset.MEDIUM.name)
        if (mode == OFF) return null
        val preset = GateSettings.Preset.entries.firstOrNull { it.name == mode }?.settings ?: GateSettings.MEDIUM
        return GateSettings(
            thresholdDb = p.getFloat(KEY_GATE_THRESHOLD, preset.thresholdDb),
            rumbleAllowanceDb = p.getFloat(KEY_GATE_RUMBLE, preset.rumbleAllowanceDb),
            holdMs = p.getInt(KEY_GATE_HOLD, preset.holdMs),
            reductionDb = p.getFloat(KEY_GATE_REDUCTION, preset.reductionDb),
        )
    }

    fun setWindGate(context: Context, value: GateSettings?) {
        val e = prefs(context).edit()
        if (value == null) {
            e.putString(KEY_WIND_GATE, OFF)
        } else {
            e.putString(KEY_WIND_GATE, CUSTOM)
                .putFloat(KEY_GATE_THRESHOLD, value.thresholdDb)
                .putFloat(KEY_GATE_RUMBLE, value.rumbleAllowanceDb)
                .putInt(KEY_GATE_HOLD, value.holdMs)
                .putFloat(KEY_GATE_REDUCTION, value.reductionDb)
        }
        e.apply()
    }

    private const val OFF = "OFF"
    private const val CUSTOM = "CUSTOM"

    fun recentRides(context: Context): List<RecentRide> =
        RecentRides.parse(prefs(context).getString(KEY_RECENT_RIDES, "") ?: "")

    /**
     * Marks [code] as the ride I'm in (until I leave) and adds it to the recent rides. If the app or
     * phone restarts mid-ride, the home screen offers to rejoin it.
     */
    fun rideStarted(context: Context, code: String, nowMs: Long = System.currentTimeMillis()) {
        val recent = RecentRides.add(recentRides(context), code, nowMs)
        prefs(context).edit()
            .putString(KEY_RECENT_RIDES, RecentRides.format(recent))
            .putString(KEY_UNFINISHED_RIDE, RecentRides.format(listOf(RecentRide(code, nowMs))))
            .apply()
    }

    /** The ride I didn't leave myself (app closed, phone restarted), if any. */
    fun unfinishedRide(context: Context): RecentRide? =
        RecentRides.parse(prefs(context).getString(KEY_UNFINISHED_RIDE, "") ?: "").firstOrNull()

    fun clearUnfinishedRide(context: Context) = prefs(context).edit().remove(KEY_UNFINISHED_RIDE).apply()

    /** Speak when riders join, drop out or come back, and when a phone's battery runs low. */
    fun riderAlerts(context: Context): Boolean = prefs(context).getBoolean(KEY_RIDER_ALERTS, true)

    fun setRiderAlerts(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_RIDER_ALERTS, on).apply()

    /** "RideComm, vote break": hands-free commands spoken into the mic. */
    fun voiceCommands(context: Context): Boolean = prefs(context).getBoolean(KEY_VOICE_COMMANDS, true)

    fun setVoiceCommands(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_VOICE_COMMANDS, on).apply()
}
