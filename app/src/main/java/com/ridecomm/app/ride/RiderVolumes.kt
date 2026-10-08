package com.ridecomm.app.ride

import android.content.Context
import com.ridecomm.app.Prefs
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** How loud I hear one rider: 0–2× and whether I've muted them for myself. */
data class RiderVolume(val volume: Float = 1f, val mutedForMe: Boolean = false) {
    val isDefault: Boolean get() = !mutedForMe && volume == 1f

    companion object {
        const val MIN = 0.2f
        const val MAX = 2f

        fun parse(text: String): Map<String, RiderVolume> {
            if (text.isBlank()) return emptyMap()
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
            return o.keys().asSequence().mapNotNull { id ->
                val v = o.optJSONObject(id) ?: return@mapNotNull null
                id to RiderVolume(v.optDouble("v", 1.0).toFloat().coerceIn(MIN, MAX), v.optBoolean("m", false))
            }.toMap()
        }

        fun format(volumes: Map<String, RiderVolume>): String = JSONObject().apply {
            volumes.filterValues { !it.isDefault }.forEach { (id, v) -> put(id, JSONObject().put("v", v.volume.toDouble()).put("m", v.mutedForMe)) }
        }.toString()
    }
}

/**
 * Per-rider volume, only on my phone: turn a loud rider down or a quiet one up, or mute someone
 * for myself. Muting stops their voice being downloaded at all, which also saves data.
 * Remembered for the next ride (riders keep their id across rides).
 */
object RiderVolumes {
    private val _volumes = MutableStateFlow<Map<String, RiderVolume>>(emptyMap())
    val volumes: StateFlow<Map<String, RiderVolume>> = _volumes.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        _volumes.value = Prefs.riderVolumes(context)
        loaded = true
    }

    fun of(id: String): RiderVolume = _volumes.value[id] ?: RiderVolume()

    /** Riders talking only to someone else right now: silent on my phone until they're done. */
    private var hushed: Set<String> = emptySet()

    fun setHushed(ids: Set<String>, room: Room?) {
        if (ids == hushed) return
        hushed = ids
        room?.let { apply(it) }
    }

    fun set(context: Context, id: String, value: RiderVolume, room: Room?) {
        _volumes.value = (_volumes.value + (id to value)).filterValues { !it.isDefault }
        Prefs.setRiderVolumes(context, _volumes.value)
        room?.let { apply(it) }
    }

    /** Applies the volumes to everyone's voice; call whenever riders or their tracks change. */
    fun apply(room: Room) {
        room.remoteParticipants.values.forEach { p ->
            val id = p.identity?.value ?: return@forEach
            val v = of(id)
            val pub = p.getTrackPublication(Track.Source.MICROPHONE) as? RemoteTrackPublication ?: return@forEach
            if (pub.subscribed == v.mutedForMe) runCatching { pub.setSubscribed(!v.mutedForMe) }
            val volume = if (id in hushed) 0.0 else v.volume.toDouble()
            (pub.track as? RemoteAudioTrack)?.let { runCatching { it.setVolume(volume) } }
        }
    }
}
