package com.ridecomm.app.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a RideComm screen is showing; the floating button hides while it is. */
object AppVisibility {
    private val _inForeground = MutableStateFlow(false)
    val inForeground: StateFlow<Boolean> = _inForeground.asStateFlow()

    fun set(visible: Boolean) {
        _inForeground.value = visible
    }
}
