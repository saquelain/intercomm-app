package com.ridecomm.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.sos.SosAlert
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.sos.SosState

private val SosRed = Color(0xFFC62828)

/** Red header button that starts the cancellable SOS countdown. */
@Composable
fun SosButton(sos: SosState) {
    Button(
        onClick = SosManager::startCountdown,
        enabled = !sos.mySosActive && sos.countdown == null,
        modifier = Modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
    ) { Text("🚨 SOS", fontSize = 18.sp, fontWeight = FontWeight.Black) }
}

/** My active SOS ("I'm OK") and SOS alerts from other riders. */
@Composable
fun SosCards(sos: SosState) {
    if (sos.mySosActive) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(SosRed, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🚨 Your SOS is active", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            sos.mySosStatus?.let { Text(it, fontSize = 15.sp, color = Color.White) }
            Button(
                onClick = SosManager::imOk,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SosRed),
            ) { Text("✅ I'M OK", fontSize = 22.sp, fontWeight = FontWeight.Black) }
        }
    }
    sos.alerts.forEach { AlertCard(it) }
}

@Composable
private fun AlertCard(alert: SosAlert) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(SosRed, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🚨 ${alert.name} needs help", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(
            alert.distanceM?.let { "${SosManager.formatDistance(it)} away" } ?: "Location not available yet",
            fontSize = 16.sp,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SosManager.mapsUri(alert)?.let { uri ->
                Button(
                    onClick = {
                        SosManager.acknowledge()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SosRed),
                ) { Text("🗺 Open map", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            Button(
                onClick = { SosManager.dismiss(alert.identity) },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF401010), contentColor = Color.White),
            ) { Text("Dismiss", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Full-screen countdown inside the app; tapping anywhere cancels. */
@Composable
fun SosCountdown(seconds: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(SosRed)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = SosManager::cancelCountdown,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🚨 SOS", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("$seconds", fontSize = 120.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("Sending to your group…", fontSize = 22.sp, color = Color.White)
            Text(
                "TAP ANYWHERE TO CANCEL",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}
