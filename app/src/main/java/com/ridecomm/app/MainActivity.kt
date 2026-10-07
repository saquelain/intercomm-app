package com.ridecomm.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomm.app.overlay.AppVisibility
import com.ridecomm.app.ride.InviteLink
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ui.GlassBackground
import com.ridecomm.app.ui.HomeScreen
import com.ridecomm.app.ui.RideCommTheme
import com.ridecomm.app.ui.RideScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InviteLink.handle(intent)
        enableEdgeToEdge()
        setContent {
            RideCommTheme {
                val state by RideManager.state.collectAsStateWithLifecycle()
                GlassBackground {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (state.status == RideStatus.IDLE) HomeScreen(state) else RideScreen(state)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        InviteLink.handle(intent)
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
