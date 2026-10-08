package com.ridecomm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ridecomm.app.Prefs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.R
import com.ridecomm.app.sos.SosAlert
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.sos.SosState

/** Red round button that starts the cancellable SOS countdown. */
@Composable
fun SosButton(sos: SosState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val look = LocalLook.current
        GlassIconButton(
            R.drawable.ms_sos,
            "SOS",
            modifier = when (look) {
                UiLook.GLASS -> Modifier.glow(GlassTokens.SosGlow, 25.dp, CircleShape)
                UiLook.NEU -> Modifier.glow(NeuTokens.SosGlow, 24.dp, CircleShape, offsetY = 6.dp)
                UiLook.CLASSIC -> Modifier
            },
            size = if (look == UiLook.CLASSIC) 64.dp else 62.dp,
            iconSize = 34.dp,
            brush = when (look) {
                UiLook.GLASS -> GlassTokens.Sos
                UiLook.NEU -> NeuTokens.Sos
                UiLook.CLASSIC -> Palette.StopGradient
            },
            enabled = !sos.mySosActive && sos.countdown == null,
            onClick = SosManager::startCountdown,
        )
    }
}

/** My active SOS ("I'm OK") and SOS alerts from other riders. */
@Composable
fun SosCards(sos: SosState) {
    if (sos.mySosActive) {
        GlassCard(tint = Palette.Stop, fillAlpha = 0.26f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.ms_emergency, 30.dp, LocalCardInk.current)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your SOS is active", style = MaterialTheme.typography.titleLarge)
                    sos.mySosStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalCardInk.current) }
                }
            }
            PrimaryButton("I'm OK", R.drawable.ms_check_circle, Modifier.fillMaxWidth(), brush = Palette.GoGradient, height = 64.dp, onClick = SosManager::imOk)
        }
        HelperCard()
    }
    sos.alerts.forEach { AlertCard(it) }
}

/**
 * While my SOS is on: my details in big text for a passer-by or medic holding my phone, with
 * buttons to call my emergency contact and the emergency number.
 */
@Composable
private fun HelperCard() {
    val context = LocalContext.current
    val info = remember { Prefs.emergencyInfo(context) }
    val name = remember { Prefs.riderName(context) }
    GlassCard(tint = Color.White, fillAlpha = 0.10f, spacing = 10.dp) {
        SectionLabel("For anyone helping")
        Text(name.ifBlank { "Rider" }, style = MaterialTheme.typography.headlineMedium)
        if (info.bloodGroup.isNotBlank()) InfoLine("Blood group", info.bloodGroup, big = true)
        if (info.medical.isNotBlank()) InfoLine("Medical", info.medical)
        if (info.isEmpty) {
            Text("Add blood group, allergies and an emergency contact in Settings.", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (info.contactPhone.isNotBlank()) {
                PrimaryButton(
                    "Call ${info.contactName.ifBlank { "contact" }}",
                    R.drawable.ms_call,
                    Modifier.weight(1f),
                    brush = Palette.GoGradient,
                    height = 58.dp,
                ) { dial(context, info.contactPhone) }
            }
            GlassButton("Call $EMERGENCY_NUMBER", R.drawable.ms_call, Modifier.weight(1f), height = 58.dp) {
                dial(context, EMERGENCY_NUMBER)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, big: Boolean = false) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalCardInk.current.copy(alpha = 0.7f))
        Text(value, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium, color = LocalCardInk.current)
    }
}

/** Opens the dialer with the number filled in (no permission needed; the rider taps Call). */
private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))) }
}

/** India's (and Europe's) single emergency number. */
private const val EMERGENCY_NUMBER = "112"

@Composable
private fun AlertCard(alert: SosAlert) {
    val context = LocalContext.current
    GlassCard(tint = Palette.Stop, fillAlpha = 0.28f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_sos, 34.dp, LocalCardInk.current)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (alert.crash) "${alert.name} may have crashed" else "${alert.name} needs help", style = MaterialTheme.typography.titleLarge)
                Text(
                    alert.distanceM?.let { "${SosManager.formatDistance(it)} away" } ?: "Location not available yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
        alert.info?.let { info ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (info.bloodGroup.isNotBlank()) InfoLine("Blood", info.bloodGroup)
                if (info.medical.isNotBlank()) Box(Modifier.weight(1f)) { InfoLine("Medical", info.medical) }
            }
            if (info.contactPhone.isNotBlank()) {
                GlassButton(
                    "Call ${info.contactName.ifBlank { "their contact" }} · ${info.contactPhone}",
                    R.drawable.ms_call,
                    Modifier.fillMaxWidth(),
                    height = 52.dp,
                ) { dial(context, info.contactPhone) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SosManager.mapsUri(alert)?.let { uri ->
                PrimaryButton(
                    "Open map",
                    R.drawable.ms_map,
                    Modifier.weight(1f),
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFE3E8))),
                    contentColor = Color(0xFFC2185B),
                    height = 58.dp,
                ) {
                    SosManager.acknowledge()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
            GlassButton("Dismiss", R.drawable.ms_close, Modifier.weight(1f), height = 58.dp) {
                SosManager.dismiss(alert.identity)
            }
        }
    }
}

/** Full-screen countdown inside the app; tapping anywhere cancels. */
@Composable
fun SosCountdown(seconds: Int, crash: Boolean = false) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE5245E), Color(0xFF7A0F2E))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = SosManager::cancelCountdown,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Ico(if (crash) R.drawable.ms_car_crash else R.drawable.ms_sos, 72.dp, Color.White)
            if (crash) Text("Crash detected", style = MaterialTheme.typography.headlineMedium)
            Box(
                Modifier
                    .size(200.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$seconds", style = MaterialTheme.typography.displayMedium.copy(fontSize = 110.sp, letterSpacing = 0.sp))
            }
            Text("Sending SOS to your group", style = MaterialTheme.typography.titleLarge)
            Row(
                Modifier
                    .padding(top = 24.dp)
                    .glass(PillShape, fillAlpha = 0.16f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(R.drawable.ms_close, 26.dp, Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Tap anywhere to cancel", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
