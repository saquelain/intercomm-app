package com.ridecomm.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ridecomm.app.Prefs
import com.ridecomm.app.R

/** Asks for "Display over other apps" so the floating ride button can show over Maps. */
@Composable
fun OverlayPermissionCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = Settings.canDrawOverlays(context) }
    if (allowed || !Prefs.bubbleEnabled(context)) return

    GlassCard(tint = Palette.Cyan, fillAlpha = 0.10f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_picture_in_picture, 28.dp, Palette.Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Floating ride button", style = MaterialTheme.typography.titleMedium)
                Text("Mute, music, votes and SOS over Maps.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        GlassButton("Allow display over other apps", modifier = Modifier.fillMaxWidth()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        }
    }
}
