package com.ridecomm.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.Prefs
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("RideComm", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text("Group intercom for riders", fontSize = 18.sp, color = Muted)

        state.error?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = Danger.copy(alpha = 0.2f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(error, fontSize = 16.sp)
                    TextButton(onClick = RideManager::clearError) { Text("OK") }
                }
            }
        }

        if (tokenServerId.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Setup needed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Add your LiveKit token server ID once to start riding.", fontSize = 15.sp)
                    TextButton(onClick = { showSettings = true }) { Text("Open settings") }
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Your name") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        BigButton(
            text = "Start a new ride",
            enabled = name.isNotBlank(),
            onClick = { startRide(RideCode.generate()) },
        )

        Text("or join your group", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))

        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = RideCode.clean(it) },
            label = { Text("Ride code") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )

        BigButton(
            text = "Join ride",
            enabled = name.isNotBlank() && joinCode.length == RideCode.LENGTH,
            onClick = { startRide(joinCode) },
        )

        TextButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Settings")
        }
    }

    if (showSettings) {
        var draft by remember { mutableStateOf(tokenServerId) }
        var bubbleOn by remember { mutableStateOf(Prefs.bubbleEnabled(context)) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LiveKit token server ID (LiveKit Cloud → Settings → Development token server). Everyone in the group must use the same one.")
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Floating ride button over other apps", modifier = Modifier.weight(1f))
                        Switch(checked = bubbleOn, onCheckedChange = { bubbleOn = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setTokenServerId(context, draft)
                    Prefs.setBubbleEnabled(context, bubbleOn)
                    tokenServerId = Prefs.tokenServerId(context)
                    showSettings = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } },
        )
    }
}

/** Tall, full-width button that's easy to hit with gloves on. */
@Composable
fun BigButton(text: String, enabled: Boolean = true, outlined: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
