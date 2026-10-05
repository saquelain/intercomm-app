package com.ridecomm.app.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideState
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.Signal

@Composable
fun RideScreen(state: RideState) {
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RIDE CODE", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
                Text(state.code, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            }
            OutlinedButton(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Join my RideComm ride! Code: ${state.code}")
                    context.startActivity(Intent.createChooser(share, "Share ride code"))
                },
                modifier = Modifier.height(56.dp),
            ) { Text("Share", fontSize = 18.sp) }
        }

        StatusLine(state)

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.riders, key = { it.id }) { RiderCard(it) }
        }

        MuteButton(muted = state.micMuted, onClick = RideManager::toggleMute)

        OutlinedButton(
            onClick = { confirmLeave = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
        ) { Text("Leave ride", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the ride?") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; RideManager.leave() }) {
                    Text("Leave", color = Danger, fontSize = 18.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay", fontSize = 18.sp) }
            },
        )
    }
}

@Composable
private fun StatusLine(state: RideState) {
    val (text, color) = when (state.status) {
        RideStatus.CONNECTING -> "Joining…" to Orange
        RideStatus.RECONNECTING -> "Weak network, reconnecting…" to Orange
        else -> {
            val count = state.riders.size
            "Connected · $count ${if (count == 1) "rider" else "riders"}" to Talking
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 18.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RiderCard(rider: Rider) {
    val ring by animateColorAsState(if (rider.isSpeaking) Talking else Color.Transparent, label = "speaking")
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(3.dp, ring, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).background(if (rider.isMe) Orange else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rider.name.first().uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (rider.isMe) Color.Black else Color.White,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (rider.isMe) "${rider.name} (you)" else rider.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    rider.isMuted -> "Mic off"
                    rider.isSpeaking -> "Talking…"
                    else -> "Listening"
                },
                fontSize = 15.sp,
                color = if (rider.isSpeaking) Talking else Muted,
            )
        }
        SignalBadge(rider.signal)
    }
}

@Composable
private fun SignalBadge(signal: Signal) {
    val (label, color) = when (signal) {
        Signal.GOOD -> "Good" to Talking
        Signal.WEAK -> "Weak" to Orange
        Signal.LOST -> "Lost" to Danger
        Signal.UNKNOWN -> return
    }
    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MuteButton(muted: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(96.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (muted) Danger else Talking,
            contentColor = Color.Black,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (muted) "MIC OFF" else "MIC ON", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(if (muted) "Tap to talk" else "Tap to mute", fontSize = 15.sp)
        }
    }
}
