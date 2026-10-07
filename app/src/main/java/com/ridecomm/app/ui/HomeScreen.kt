package com.ridecomm.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.ride.RideCode
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideState

@Composable
fun HomeScreen(state: RideState) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(Prefs.riderName(context)) }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var tokenServerId by remember { mutableStateOf(Prefs.tokenServerId(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingCode by remember { mutableStateOf<String?>(null) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            // Optional: lets an SOS include where you are.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val code = pendingCode
        pendingCode = null
        if (code != null && result[Manifest.permission.RECORD_AUDIO] == true) {
            RideManager.start(context, code)
        } else if (code != null) {
            Toast.makeText(context, "RideComm needs the microphone to talk to your group", Toast.LENGTH_LONG).show()
        }
    }

    fun startRide(code: String) {
        Prefs.setRiderName(context, name.trim())
        pendingCode = code
        permissionLauncher.launch(permissions)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("RideComm", style = MaterialTheme.typography.headlineMedium)
                Text("Group intercom for riders", style = MaterialTheme.typography.bodyMedium)
            }
            GlassIconButton(R.drawable.ms_settings, "Settings", size = 48.dp, iconSize = 22.dp) { showSettings = true }
        }

        state.error?.let { error ->
            GlassCard(tint = Palette.Stop, fillAlpha = 0.18f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ms_warning, 24.dp, Palette.Stop)
                    Spacer(Modifier.width(12.dp))
                    Text(error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    GlassIconButton(R.drawable.ms_close, "Dismiss", size = 40.dp, iconSize = 18.dp, onClick = RideManager::clearError)
                }
            }
        }

        if (tokenServerId.isBlank()) {
            GlassCard(tint = Palette.Amber, fillAlpha = 0.14f) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                Text("Add your LiveKit token server ID once to start riding.", style = MaterialTheme.typography.bodyMedium)
                GlassButton("Open settings", R.drawable.ms_settings) { showSettings = true }
            }
        }

        GlassCard(spacing = 18.dp) {
            GlassTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = "Your name",
                placeholder = "e.g. Rahul",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            PrimaryButton(
                "Start a new ride",
                R.drawable.ms_two_wheeler,
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { startRide(RideCode.generate()) }
        }

        GlassCard(spacing = 18.dp) {
            GlassTextField(
                value = joinCode,
                onValueChange = { joinCode = RideCode.clean(it) },
                label = "Join your group",
                placeholder = "RIDE CODE",
                textStyle = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                center = true,
            )
            GlassButton(
                "Join ride",
                R.drawable.ms_arrow_forward,
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && joinCode.length == RideCode.LENGTH,
                height = 60.dp,
            ) { startRide(joinCode) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(R.drawable.ms_headset_mic, "Talk", Modifier.weight(1f))
            Feature(R.drawable.ms_library_music, "Music", Modifier.weight(1f))
            Feature(R.drawable.ms_thumb_up, "Votes", Modifier.weight(1f))
            Feature(R.drawable.ms_sos, "SOS", Modifier.weight(1f))
        }
    }

    if (showSettings) {
        SettingsDialog(
            onClose = { showSettings = false },
            onSaved = { tokenServerId = Prefs.tokenServerId(context) },
        )
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Brand)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) { Ico(R.drawable.ms_two_wheeler, 32.dp, Color.White) }
}

@Composable
private fun Feature(icon: Int, label: String, modifier: Modifier) {
    Column(
        modifier
            .glass(RoundedCornerShape(20.dp), fillAlpha = 0.05f, rimAlpha = 0.18f)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ico(icon, 24.dp, Palette.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
    }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var tokenId by remember { mutableStateOf(Prefs.tokenServerId(context)) }
    var bubbleOn by remember { mutableStateOf(Prefs.bubbleEnabled(context)) }
    var numbers by remember { mutableStateOf(Prefs.emergencyNumbers(context)) }

    GlassDialog(onDismiss = onClose) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
        GlassTextField(
            value = tokenId,
            onValueChange = { tokenId = it },
            label = "LiveKit token server ID",
            placeholder = "ridecomm-xxxxxx",
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "LiveKit Cloud → Settings → Development token server. Everyone in the group uses the same one.",
            style = MaterialTheme.typography.bodyMedium,
        )
        GlassTextField(
            value = numbers,
            onValueChange = { numbers = it },
            label = "Emergency SMS numbers",
            placeholder = "+91 98xxxxxxxx, …",
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Text("Used for SOS when there's no internet.", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Floating ride button", style = MaterialTheme.typography.titleMedium)
                Text("Controls over Maps and other apps", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = bubbleOn,
                onCheckedChange = { bubbleOn = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.Orange,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton("Cancel", modifier = Modifier.weight(1f), onClick = onClose)
            PrimaryButton("Save", modifier = Modifier.weight(1f), height = 56.dp) {
                Prefs.setTokenServerId(context, tokenId)
                Prefs.setBubbleEnabled(context, bubbleOn)
                Prefs.setEmergencyNumbers(context, numbers)
                onSaved()
                onClose()
            }
        }
    }
}
