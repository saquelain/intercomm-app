package com.ridecomm.app.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Turns other music apps (Spotify, YouTube Music, …) down while someone talks, using Android's
 * own audio-focus ducking: asking for short "may duck" focus makes the system lower whatever is
 * playing, and giving it back restores the volume. Works with any app, no integration needed.
 */
object OtherAppsDucker {
    private var request: AudioFocusRequest? = null

    fun setDucked(context: Context, ducked: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java)
        if (ducked && request == null) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
            if (audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request = r
        } else if (!ducked) {
            request?.let { audio.abandonAudioFocusRequest(it) }
            request = null
        }
    }
}
