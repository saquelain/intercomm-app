package com.ridecomm.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ridecomm.app.night.NightMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Speaks short confirmations ("Mic off") so riders know what happened without looking.
 * Uses the call audio path so it plays in the helmet headset.
 */
object Announcer {
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Lines asked for before the speech engine finished starting. */
    private val pending = mutableListOf<String>()

    private val _speaking = MutableStateFlow(false)
    /** True while an announcement is playing; shared music turns down meanwhile. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun speak(context: Context, text: String) {
        night = NightMode.refresh(context)
        val engine = tts
        if (engine == null) {
            pending += text
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { _speaking.value = true }
                        override fun onDone(utteranceId: String?) { _speaking.value = false }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { _speaking.value = false }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
                    })
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    pending.forEach { say(it) }
                }
                pending.clear()
            }
            return
        }
        if (ready) say(text) else pending += text
    }

    private fun say(text: String) {
        // Quieter at night (night mode), so alerts don't startle in a silent helmet.
        val params = Bundle()
        if (night) params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, NightMode.NIGHT_VOICE_VOLUME)
        // Queue rather than cut off: a confirmation can be followed by a vote result.
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "ridecomm")
    }

    private var night = false

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        pending.clear()
        _speaking.value = false
    }
}
