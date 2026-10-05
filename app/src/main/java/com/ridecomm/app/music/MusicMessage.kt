package com.ridecomm.app.music

import org.json.JSONObject

/**
 * Small JSON messages riders exchange about shared music (sent on [MusicManager.TEXT_TOPIC]).
 * Song files themselves travel separately as byte streams.
 */
sealed class MusicMessage {
    /** DJ → riders: what's playing. [positionMs] was the playback position at wall-clock time [atMs]. */
    data class Now(
        val songId: String,
        val title: String,
        val djName: String,
        val playing: Boolean,
        val positionMs: Long,
        val atMs: Long,
    ) : MusicMessage() {
        /** Where playback should be right now, given both phones' clocks are network-synced. */
        fun positionAt(nowMs: Long): Long =
            if (playing) positionMs + (nowMs - atMs).coerceAtLeast(0) else positionMs
    }

    /** DJ → riders: music turned off. */
    data object Stop : MusicMessage()

    /** New or reconnected rider → everyone: "tell me what's playing". */
    data object Sync : MusicMessage()

    /** Rider → DJ: "send me this song file, I don't have it". */
    data class Need(val songId: String) : MusicMessage()

    fun encode(): String = when (this) {
        is Now -> JSONObject()
            .put("t", "now")
            .put("id", songId)
            .put("title", title)
            .put("dj", djName)
            .put("playing", playing)
            .put("pos", positionMs)
            .put("at", atMs)
        Stop -> JSONObject().put("t", "stop")
        Sync -> JSONObject().put("t", "sync")
        is Need -> JSONObject().put("t", "need").put("id", songId)
    }.toString()

    companion object {
        fun decode(text: String): MusicMessage? = runCatching {
            val o = JSONObject(text)
            when (o.getString("t")) {
                "now" -> Now(
                    songId = o.getString("id"),
                    title = o.getString("title"),
                    djName = o.getString("dj"),
                    playing = o.getBoolean("playing"),
                    positionMs = o.getLong("pos"),
                    atMs = o.getLong("at"),
                )
                "stop" -> Stop
                "sync" -> Sync
                "need" -> Need(o.getString("id"))
                else -> null
            }
        }.getOrNull()
    }
}
