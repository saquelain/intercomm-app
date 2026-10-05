package com.ridecomm.app

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech

/**
 * Speaks short confirmations ("Mic off") so riders know what happened without looking.
 * Uses the call audio path so it plays in the helmet headset.
 */
object Announcer {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(context: Context, text: String) {
        val engine = tts
        if (engine == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    pending?.let { say(it) }
                }
                pending = null
            }
            return
        }
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ridecomm")
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
