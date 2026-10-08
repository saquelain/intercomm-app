package com.ridecomm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import android.view.WindowManager
import com.ridecomm.app.night.NightMode
import com.ridecomm.app.ui.nightFilter
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomm.app.overlay.AppVisibility
import com.ridecomm.app.ride.InviteLink
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ui.LookBackground
import com.ridecomm.app.ui.LookSetting
import com.ridecomm.app.ui.LookScope
import com.ridecomm.app.ui.GlassSceneStyle
import com.ridecomm.app.ui.HomeScreen
import com.ridecomm.app.ui.RideCommTheme
import com.ridecomm.app.ui.RideScreen

private const val NIGHT_CHECK_MS = 60_000L
private const val NIGHT_BRIGHTNESS = 0.18f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InviteLink.handle(intent, this)
        enableEdgeToEdge()
        setContent {
            RideCommTheme {
                val state by RideManager.state.collectAsStateWithLifecycle()
                val night by NightMode.active.collectAsStateWithLifecycle()
                // "Auto" follows sunset, so check again every minute.
                LaunchedEffect(Unit) {
                    while (true) {
                        NightMode.refresh(this@MainActivity)
                        delay(NIGHT_CHECK_MS)
                    }
                }
                LaunchedEffect(night) {
                    window.attributes = window.attributes.apply {
                        screenBrightness = if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
                val look by LookSetting.current.collectAsStateWithLifecycle()
                val home = state.status == RideStatus.IDLE
                LookBackground(look, Modifier.nightFilter(night), if (home) GlassSceneStyle.HOME else GlassSceneStyle.RIDE) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        LookScope(look) { if (home) HomeScreen(state) else RideScreen(state) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A setting changed elsewhere, or time passed while away.
        NightMode.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        InviteLink.handle(intent, this)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.set(true)
    }

    override fun onStop() {
        AppVisibility.set(false)
        super.onStop()
    }
}
