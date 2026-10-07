package com.ridecomm.app.ride

import android.content.Context
import com.ridecomm.app.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings choice for saving mobile data. */
enum class DataSaverMode(val label: String) {
    OFF("Off"),
    /** Only while my network is weak; back to normal once it's good again. */
    AUTO("Auto"),
    ALWAYS("Always"),
}

/**
 * Decides when to save data in Auto mode: after [WEAK_AFTER_MS] of a poor or lost connection,
 * and back to normal only after [GOOD_AFTER_MS] of a good one, so a flaky signal doesn't flip
 * the voice quality back and forth. Pure logic; times are elapsed milliseconds.
 */
class WeakNetworkWatch {
    var saving = false
        private set
    private var weakSince: Long? = null
    private var goodSince: Long? = null

    /** [weak] is the connection right now. Returns true when [saving] changed. */
    fun update(nowMs: Long, weak: Boolean): Boolean {
        if (weak) {
            goodSince = null
            val since = weakSince ?: nowMs.also { weakSince = it }
            if (!saving && nowMs - since >= WEAK_AFTER_MS) {
                saving = true
                return true
            }
        } else {
            weakSince = null
            val since = goodSince ?: nowMs.also { goodSince = it }
            if (saving && nowMs - since >= GOOD_AFTER_MS) {
                saving = false
                return true
            }
        }
        return false
    }

    fun reset() {
        saving = false
        weakSince = null
        goodSince = null
    }

    companion object {
        const val WEAK_AFTER_MS = 5_000L
        const val GOOD_AFTER_MS = 30_000L
    }
}

/**
 * Whether the ride is saving data right now: lower voice quality and no shared-song downloads
 * (songs catch up by themselves once data saving ends).
 */
object DataSaver {
    private val watch = WeakNetworkWatch()
    private var mode = DataSaverMode.AUTO

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun applySettings(context: Context) {
        mode = Prefs.dataSaver(context)
        publish()
    }

    /** Called whenever my connection quality changes, and every few seconds. */
    fun onConnection(nowMs: Long, weak: Boolean) {
        watch.update(nowMs, weak)
        publish()
    }

    /** New ride: start from normal quality. */
    fun reset(context: Context) {
        watch.reset()
        applySettings(context)
    }

    private fun publish() {
        _active.value = when (mode) {
            DataSaverMode.OFF -> false
            DataSaverMode.AUTO -> watch.saving
            DataSaverMode.ALWAYS -> true
        }
    }

    /** Normal voice bitrate and the one used while saving data (Opus stays clear for speech at 12 kbps). */
    const val NORMAL_VOICE_BPS = 24_000
    const val SAVING_VOICE_BPS = 12_000
}
