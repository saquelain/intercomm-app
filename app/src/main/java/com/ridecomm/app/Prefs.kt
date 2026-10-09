package com.ridecomm.app

import android.content.Context
import com.ridecomm.app.audio.GateSettings
import com.ridecomm.app.ride.RecentRide
import com.ridecomm.app.ride.DataSaverMode
import com.ridecomm.app.ride.RecentRides
import com.ridecomm.app.night.NightModeSetting
import com.ridecomm.app.ride.RiderVolume
import com.ridecomm.app.ride.TalkMode
import com.ridecomm.app.sos.EmergencyInfo
import com.ridecomm.app.trip.BreakEvery
import com.ridecomm.app.trip.UpdateEvery
import com.ridecomm.app.ui.UiLook
import com.ridecomm.app.ui.map.MapProvider
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
    private const val KEY_SPEED_LIMIT = "speed_limit_kmh"
    private const val KEY_RIDE_UPDATES = "ride_updates"
    private const val KEY_DATA_SAVER = "data_saver"
    private const val KEY_MAP_DARK = "map_dark"
    private const val KEY_TALK_MODE = "talk_mode"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_HAZARD_ALERTS = "hazard_alerts"
    private const val KEY_ROLE_ALERTS = "lead_sweep_alerts"
    private const val KEY_RIDER_VOLUMES = "rider_volumes"
    private const val KEY_EMERGENCY_INFO = "emergency_info"
    private const val KEY_SHARE_EMERGENCY_INFO = "share_emergency_info"
    private const val KEY_PRIVATE_SERVER = "private_server"
    private const val KEY_TALK_TO_ONE = "talk_to_one"
    private const val KEY_DESTINATION = "shared_destination"
    private const val KEY_HOME_SAFE = "home_safe"
    private const val KEY_HOME_SPOT = "home_spot"
    private const val KEY_HOME_CHECK_IN = "home_check_in"
    private const val KEY_BREAK_EVERY = "break_every"
    private const val KEY_LOCK_SCREEN_INFO = "lock_screen_info"
    private const val KEY_LOOK = "ui_look"
    private const val KEY_MAP_PROVIDER = "map_provider"
    private const val KEY_FAMILY_WATCH = "family_watch"
    private const val KEY_RIDE_HISTORY = "ride_history"
    private const val KEY_PLANNER = "ride_planner"
    private const val KEY_PLANS = "ride_plans"
    private const val KEY_FINDER = "finder"
    private const val KEY_RAIN = "rain_alerts"
    private const val KEY_POINTS = "points"

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

    /**
     * Lock rides to my group: passes come from the private ride server (which checks the group key)
     * instead of the open development token server. On by default when the APK has a server built in.
     */
    fun privateServer(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVATE_SERVER, BuildConfig.DEFAULT_RIDE_SERVER_URL.isNotBlank())

    fun setPrivateServer(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_PRIVATE_SERVER, on).apply()

    /** The private ride server in use, or blank when rides use the development token server. */
    fun activeRideServer(context: Context): String = if (privateServer(context)) rideServerUrl(context) else ""

    /** Ready to ride: either the private server with a group key, or the development token server. */
    fun serverConfigured(context: Context): Boolean =
        if (activeRideServer(context).isNotBlank()) groupKey(context).isNotBlank() else tokenServerId(context).isNotBlank()

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

    /** Warn by voice above this speed (km/h); 0 = off. */
    fun speedLimit(context: Context): Int = prefs(context).getInt(KEY_SPEED_LIMIT, 0)

    fun setSpeedLimit(context: Context, kmh: Int) = prefs(context).edit().putInt(KEY_SPEED_LIMIT, kmh).apply()

    /** How often to speak "42 kilometres, 1 hour riding…". */
    fun rideUpdates(context: Context): UpdateEvery =
        UpdateEvery.entries.firstOrNull { it.name == prefs(context).getString(KEY_RIDE_UPDATES, null) } ?: UpdateEvery.OFF

    fun setRideUpdates(context: Context, every: UpdateEvery) =
        prefs(context).edit().putString(KEY_RIDE_UPDATES, every.name).apply()

    /** When to save mobile data (lower voice quality, no shared-song downloads). */
    fun dataSaver(context: Context): DataSaverMode =
        DataSaverMode.entries.firstOrNull { it.name == prefs(context).getString(KEY_DATA_SAVER, null) } ?: DataSaverMode.AUTO

    fun setDataSaver(context: Context, mode: DataSaverMode) =
        prefs(context).edit().putString(KEY_DATA_SAVER, mode.name).apply()

    /** Group map in dark colours or the normal light map (default, like Google Maps; reads better in bright sun). */
    fun mapDark(context: Context): Boolean = prefs(context).getBoolean(KEY_MAP_DARK, false)

    fun setMapDark(context: Context, dark: Boolean) = prefs(context).edit().putBoolean(KEY_MAP_DARK, dark).apply()

    /** Open mic (the wind filter decides when I'm talking) or push to talk. */
    fun talkMode(context: Context): TalkMode =
        TalkMode.entries.firstOrNull { it.name == prefs(context).getString(KEY_TALK_MODE, null) } ?: TalkMode.OPEN_MIC

    fun setTalkMode(context: Context, mode: TalkMode) = prefs(context).edit().putString(KEY_TALK_MODE, mode.name).apply()

    /** Dim red screen and a quieter voice for night rides. */
    fun nightMode(context: Context): NightModeSetting =
        NightModeSetting.entries.firstOrNull { it.name == prefs(context).getString(KEY_NIGHT_MODE, null) } ?: NightModeSetting.OFF

    fun setNightMode(context: Context, mode: NightModeSetting) = prefs(context).edit().putString(KEY_NIGHT_MODE, mode.name).apply()

    /** Mark potholes, police and so on for the riders behind, and hear the ones ahead. */
    fun hazardAlerts(context: Context): Boolean = prefs(context).getBoolean(KEY_HAZARD_ALERTS, true)

    fun setHazardAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HAZARD_ALERTS, on).apply()

    /** Speak when a rider gets ahead of the lead or drops behind the sweep (needs Group map). */
    fun roleAlerts(context: Context): Boolean = prefs(context).getBoolean(KEY_ROLE_ALERTS, true)

    fun setRoleAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ROLE_ALERTS, on).apply()

    /** How loud I hear each rider (by their device id), kept across rides. */
    fun riderVolumes(context: Context): Map<String, RiderVolume> =
        RiderVolume.parse(prefs(context).getString(KEY_RIDER_VOLUMES, "") ?: "")

    fun setRiderVolumes(context: Context, volumes: Map<String, RiderVolume>) =
        prefs(context).edit().putString(KEY_RIDER_VOLUMES, RiderVolume.format(volumes)).apply()

    /** Blood group, allergies and who to call, for whoever helps me after an SOS. */
    fun emergencyInfo(context: Context): EmergencyInfo =
        EmergencyInfo.parse(prefs(context).getString(KEY_EMERGENCY_INFO, "") ?: "")

    fun setEmergencyInfo(context: Context, info: EmergencyInfo) =
        prefs(context).edit().putString(KEY_EMERGENCY_INFO, info.toJson().toString()).apply()

    /** Send my emergency info with my SOS (it's never sent otherwise). */
    fun shareEmergencyInfo(context: Context): Boolean = prefs(context).getBoolean(KEY_SHARE_EMERGENCY_INFO, true)

    fun setShareEmergencyInfo(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHARE_EMERGENCY_INFO, on).apply()

    /** Hold a rider to talk only to them. (Others' private talk is always respected.) */
    fun talkToOne(context: Context): Boolean = prefs(context).getBoolean(KEY_TALK_TO_ONE, true)

    fun setTalkToOne(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_TALK_TO_ONE, on).apply()

    /** Where the group is heading: set it for everyone, see it, navigate to it. */
    fun sharedDestination(context: Context): Boolean = prefs(context).getBoolean(KEY_DESTINATION, true)

    fun setSharedDestination(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_DESTINATION, on).apply()

    /** "Home safe" check-in after the ride, and hearing who else got home. */
    fun homeSafe(context: Context): Boolean = prefs(context).getBoolean(KEY_HOME_SAFE, true)

    fun setHomeSafe(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HOME_SAFE, on).apply()

    /** Where home is (stays on this phone; only "home safe" is ever sent). Null until set. */
    fun homeSpot(context: Context): Pair<Double, Double>? {
        val parts = (prefs(context).getString(KEY_HOME_SPOT, "") ?: "").split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return lat to lon
    }

    fun setHomeSpot(context: Context, spot: Pair<Double, Double>?) =
        prefs(context).edit().putString(KEY_HOME_SPOT, spot?.let { "${it.first},${it.second}" } ?: "").apply()

    /** The ride I left without saying I got home, so the home screen can offer it ("CODE,leftAtMs"). */
    fun pendingHomeCheckIn(context: Context): RecentRide? =
        RecentRides.parse(prefs(context).getString(KEY_HOME_CHECK_IN, "") ?: "").firstOrNull()

    fun setPendingHomeCheckIn(context: Context, ride: RecentRide?) =
        prefs(context).edit().putString(KEY_HOME_CHECK_IN, ride?.let { RecentRides.format(listOf(it)) } ?: "").apply()

    /** Remind me to take a break after this much riding. */
    fun breakEvery(context: Context): BreakEvery =
        BreakEvery.entries.firstOrNull { it.name == prefs(context).getString(KEY_BREAK_EVERY, null) } ?: BreakEvery.H2

    fun setBreakEvery(context: Context, every: BreakEvery) = prefs(context).edit().putString(KEY_BREAK_EVERY, every.name).apply()

    /** Emergency info as a notification on the lock screen, for whoever picks up my phone. Off by default. */
    fun lockScreenInfo(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK_SCREEN_INFO, false)

    fun setLockScreenInfo(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_INFO, on).apply()

    /** Classic or Glass look (screens switch over as their Glass design is done). */
    fun look(context: Context): UiLook =
        UiLook.entries.firstOrNull { it.name == prefs(context).getString(KEY_LOOK, null) } ?: UiLook.CLASSIC

    fun setLook(context: Context, look: UiLook) = prefs(context).edit().putString(KEY_LOOK, look.name).apply()

    /** Which map the Group map shows: Google Maps (when this build has a key) or OpenStreetMap. */
    fun mapProvider(context: Context): MapProvider =
        MapProvider.entries.firstOrNull { it.name == prefs(context).getString(KEY_MAP_PROVIDER, null) } ?: MapProvider.GOOGLE

    fun setMapProvider(context: Context, provider: MapProvider) =
        prefs(context).edit().putString(KEY_MAP_PROVIDER, provider.name).apply()

    /** Family watching from home (the watch page) may see my position and SOS. Off by default. */
    fun familyWatch(context: Context): Boolean = prefs(context).getBoolean(KEY_FAMILY_WATCH, false)

    fun setFamilyWatch(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_FAMILY_WATCH, on).apply()

    /** Keep a summary of each ride on this phone (route, distance, speeds, stops). On by default; never shared. */
    fun rideHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_RIDE_HISTORY, true)

    fun setRideHistory(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_RIDE_HISTORY, on).apply()

    /** Ride planner: plan rides ahead and get reminders. On by default. */
    fun planner(context: Context): Boolean = prefs(context).getBoolean(KEY_PLANNER, true)

    fun setPlanner(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_PLANNER, on).apply()

    /** Upcoming ride plans (JSON array, see RidePlans). */
    fun plansJson(context: Context): String = prefs(context).getString(KEY_PLANS, "") ?: ""

    fun setPlansJson(context: Context, json: String) = prefs(context).edit().putString(KEY_PLANS, json).apply()

    /** Fuel & food finder and low fuel alerts. On by default (searches only when asked). */
    fun finder(context: Context): Boolean = prefs(context).getBoolean(KEY_FINDER, true)

    fun setFinder(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_FINDER, on).apply()

    /** Rain alerts from the forecast here and ahead. Off by default: my rough location goes to a weather service. */
    fun rainAlerts(context: Context): Boolean = prefs(context).getBoolean(KEY_RAIN, false)

    fun setRainAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_RAIN, on).apply()

    /** Points & badges: earn points for each ride; riders in the ride see each other's. On by default. */
    fun points(context: Context): Boolean = prefs(context).getBoolean(KEY_POINTS, true)

    fun setPoints(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_POINTS, on).apply()
}
