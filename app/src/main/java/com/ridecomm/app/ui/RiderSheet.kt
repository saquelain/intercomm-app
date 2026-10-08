package com.ridecomm.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.group.RideRolesState
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ride.RiderVolume
import kotlin.math.roundToInt

private val LeadColor: Color @Composable get() = Palette.Orange
private val SweepColor: Color @Composable get() = Palette.Cyan

/** "LEAD" / "SWEEP" pill next to a rider's name. */
@Composable
fun RoleTag(role: String) {
    val color = if (role == "Lead") LeadColor else SweepColor
    Box(
        Modifier
            .glass(PillShape, tint = color, fillAlpha = 0.30f, rimAlpha = 0.5f)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(role.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White) }
}

/**
 * Tap a rider: how loud I hear them (only on my phone), mute them for me, and make them the
 * group's lead or sweep (for everyone).
 */
@Composable
fun RiderSheet(
    rider: Rider,
    photo: Bitmap?,
    volume: RiderVolume,
    roles: RideRolesState,
    onVolume: (RiderVolume) -> Unit,
    onLead: (Boolean) -> Unit,
    onSweep: (Boolean) -> Unit,
    onClose: () -> Unit,
    whispering: Boolean = false,
    onWhisperStart: (() -> Unit)? = null,
    onWhisperStop: () -> Unit = {},
) {
    val isLead = roles.leadId == rider.id
    val isSweep = roles.sweepId == rider.id
    GlassDialog(onDismiss = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(rider.name, 64.dp, if (rider.isMe) Palette.Brand else Brush.linearGradient(listOf(Palette.Violet, Palette.Cyan)), null, photo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (rider.isMe) "${rider.name} (you)" else rider.name, style = MaterialTheme.typography.titleLarge)
                if (isLead) RoleTag("Lead")
                if (isSweep) RoleTag("Sweep")
            }
        }

        if (onWhisperStart != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HoldToTalkButton(
                    if (whispering) "Only ${rider.name} hears you" else "Hold to talk only to ${rider.name}",
                    active = whispering,
                    modifier = Modifier.fillMaxWidth(),
                    onStart = onWhisperStart,
                    onStop = onWhisperStop,
                )
                Text("The others don't hear you while you hold.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!rider.isMe) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume for me", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (volume.mutedForMe) "Muted" else "${(volume.volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (volume.mutedForMe) Palette.Stop else Palette.Orange,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ms_volume_off, 20.dp, Palette.TextTertiary)
                    Slider(
                        value = volume.volume,
                        onValueChange = { onVolume(volume.copy(volume = (it * 10).roundToInt() / 10f, mutedForMe = false)) },
                        valueRange = RiderVolume.MIN..RiderVolume.MAX,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = lookSliderColors(),
                    )
                    Ico(R.drawable.ms_volume_up, 20.dp, Palette.TextTertiary)
                }
                Text("Only changes what you hear. 100% is normal.", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mute for me", style = MaterialTheme.typography.titleMedium)
                    Text("You stop hearing them; they still hear you. Also saves data.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = volume.mutedForMe,
                    onCheckedChange = { onVolume(volume.copy(mutedForMe = it)) },
                    colors = lookSwitchColors(Palette.Stop),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Group role", style = MaterialTheme.typography.titleMedium)
            Text(
                "The lead rides first, the sweep rides last. With the Group map on, they hear when someone gets ahead of the lead or drops behind the sweep.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleChip("Lead", R.drawable.ms_military_tech, LeadColor, isLead, Modifier.weight(1f)) { onLead(!isLead) }
                RoleChip("Sweep", R.drawable.ms_shield, SweepColor, isSweep, Modifier.weight(1f)) { onSweep(!isSweep) }
            }
        }
        PrimaryButton("Done", modifier = Modifier.fillMaxWidth(), height = 56.dp, onClick = onClose)
    }
}

@Composable
private fun RoleChip(label: String, icon: Int, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .glass(RoundedCornerShape(16.dp), tint = if (selected) color else Color.White, fillAlpha = if (selected) 0.34f else 0.06f)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ico(icon, 22.dp, if (selected) Color.White else color)
        Spacer(Modifier.width(8.dp))
        Text(if (selected) "$label ✓" else label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Palette.TextSecondary)
    }
}
