package com.ridecomm.app.headset

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.sos.SosManager

/**
 * The play/pause button on a Bluetooth helmet headset controls the ride, so riders never have to
 * touch the phone:
 *
 * - 1 press: mute / unmute
 * - 2 presses: next song (as DJ) or music off/on for me
 * - 3 presses: start the SOS countdown (cancel on screen, as always)
 *
 * Android sends headset buttons to the app that most recently played media, so while Spotify or
 * YouTube Music is playing, the button may control that app instead.
 */
object HeadsetButtons {
    private var session: MediaSession? = null
    private var context: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val counter = PressCounter()

    private val settle = Runnable {
        when (counter.settle(SystemClock.uptimeMillis())) {
            1 -> toggleMute()
            2 -> musicGesture()
            3 -> SosManager.startCountdown()
            null -> Unit
            else -> Unit // 4+ presses: ignore rather than guess
        }
    }

    fun start(context: Context) {
        if (session != null || !Prefs.headsetButtons(context)) return
        val app = context.applicationContext
        this.context = app
        session = MediaSession(app, "RideComm").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    return onKey(event)
                }
            })
            // Look like an active player so Android routes the headset button here.
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            isActive = true
        }
    }

    fun stop() {
        handler.removeCallbacks(settle)
        session?.release()
        session = null
    }

    /** The setting changed during a ride. */
    fun applySettings(context: Context) {
        if (Prefs.headsetButtons(context)) {
            if (RideManager.state.value.status != RideStatus.IDLE) start(context)
        } else {
            stop()
        }
    }

    private fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                counter.press(SystemClock.uptimeMillis())
                handler.removeCallbacks(settle)
                handler.postDelayed(settle, SETTLE_MS)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> musicGesture()
            else -> return false
        }
        return true
    }

    private fun toggleMute() {
        val muted = !RideManager.state.value.micMuted
        RideManager.toggleMute()
        say(if (muted) "Mic off" else "Mic on")
    }

    private fun musicGesture() {
        val music = MusicManager.state.value
        when {
            music.title == null -> say("No music playing")
            music.iAmDj -> {
                MusicManager.next()
                say("Next song")
            }
            else -> {
                MusicManager.toggleOffForMe()
                say(if (music.offForMe) "Music on" else "Music off")
            }
        }
    }

    private fun say(text: String) {
        context?.let { Announcer.speak(it, text) }
    }

    /** Slightly longer than the press gap, so the last press of a gesture is counted. */
    private const val SETTLE_MS = 600L
}
