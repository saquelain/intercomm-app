package com.ridecomm.app.ride

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Notices a regular phone call (answered or dialling) during a ride, from the phone's audio
 * mode, so no phone permission is needed. The ride itself stays connected throughout.
 */
object PhoneCalls {
    private const val POLL_MS = 2_000L

    private val scope = safeMainScope()
    private val _inCall = MutableStateFlow(false)
    val inCall: StateFlow<Boolean> = _inCall.asStateFlow()

    private var audio: AudioManager? = null
    private var poll: Job? = null
    private var listener: Any? = null

    fun start(context: Context) {
        stop()
        val am = context.applicationContext.getSystemService(AudioManager::class.java)
        audio = am
        check()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listen(context, am)
        } else {
            poll = scope.launch {
                while (isActive) {
                    delay(POLL_MS)
                    check()
                }
            }
        }
    }

    fun stop() {
        poll?.cancel()
        poll = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (listener as? AudioManager.OnModeChangedListener)?.let { audio?.removeOnModeChangedListener(it) }
        }
        listener = null
        audio = null
        _inCall.value = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listen(context: Context, am: AudioManager) {
        val l = AudioManager.OnModeChangedListener { check() }
        am.addOnModeChangedListener(context.mainExecutor, l)
        listener = l
    }

    private fun check() {
        // RideComm's own call uses MODE_IN_COMMUNICATION; a phone call is MODE_IN_CALL.
        _inCall.value = audio?.mode == AudioManager.MODE_IN_CALL
    }
}
