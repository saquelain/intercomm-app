package com.ridecomm.app.night

import android.content.Context
import com.ridecomm.app.Prefs
import com.ridecomm.app.sos.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Night mode for night rides: the screen goes dim red (easy on eyes used to the dark road) and
 * spoken alerts get quieter. "Auto" follows sunset and sunrise where the phone is.
 */
object NightMode {
    /** Spoken alerts at this share of the normal volume at night. */
    const val NIGHT_VOICE_VOLUME = 0.55f

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Works out whether it's on now (cheap; call as often as needed). */
    fun refresh(context: Context): Boolean {
        val setting = Prefs.nightMode(context)
        val location = if (setting == NightModeSetting.AUTO) LocationHelper.lastKnown(context) else null
        val on = NightLogic.isNight(setting, System.currentTimeMillis(), location?.latitude, location?.longitude)
        _active.value = on
        return on
    }
}
