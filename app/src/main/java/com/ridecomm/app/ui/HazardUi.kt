package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.hazard.HazardKind
import com.ridecomm.app.hazard.HazardView

val HazardKind.icon: Int
    get() = when (this) {
        HazardKind.POTHOLE -> R.drawable.ms_warning
        HazardKind.SPEED_BREAKER -> R.drawable.ms_speed
        HazardKind.SLIPPERY -> R.drawable.ms_water_drop
        HazardKind.POLICE -> R.drawable.ms_local_police
        HazardKind.ACCIDENT -> R.drawable.ms_car_crash
        HazardKind.ANIMAL -> R.drawable.ms_pets
    }

private val HazardGradient = Brush.linearGradient(listOf(Color(0xFFFF5E3A), Color(0xFFE5245E)))

/**
 * Road hazards on the ride screen: the ones marked so far (nearest first) and a button to mark
 * a new one. Just the button while there are none, to keep the screen calm.
 */
@Composable
fun HazardCard(hazards: List<HazardView>, nowMs: Long, onMark: () -> Unit, onRemove: (String) -> Unit) {
    if (hazards.isEmpty()) {
        if (LocalLook.current == UiLook.GLASS) {
            ActionRow(R.drawable.ms_warning, "Mark a road hazard", iconTint = Color.White, iconBackground = GlassTokens.TileOrange, onClick = onMark)
        } else {
            GlassButton("Mark a road hazard", R.drawable.ms_warning, Modifier.fillMaxWidth(), height = 52.dp, onClick = onMark)
        }
        return
    }
    GlassCard(spacing = 12.dp) {
        SectionLabel("Road hazards")
        hazards.take(MAX_SHOWN).forEach { HazardRow(it, nowMs, onRemove) }
        if (hazards.size > MAX_SHOWN) {
            Text("+${hazards.size - MAX_SHOWN} more on the map", style = MaterialTheme.typography.bodyMedium)
        }
        GlassButton("Mark a hazard", R.drawable.ms_add, Modifier.fillMaxWidth(), height = 48.dp, onClick = onMark)
    }
}

private const val MAX_SHOWN = 3

@Composable
private fun HazardRow(view: HazardView, nowMs: Long, onRemove: (String) -> Unit) {
    val h = view.hazard
    val where = view.distanceM?.let { d ->
        GroupMath.shortDistance(d) + when {
            d < GroupMath.TOGETHER_M -> " · here"
            view.ahead == true -> " ahead"
            view.ahead == false -> " behind"
            else -> " away"
        }
    }
    val minutes = ((nowMs - h.atMs) / 60_000).coerceAtLeast(0)
    val age = if (minutes < 1) "just now" else "$minutes min ago"
    Row(verticalAlignment = Alignment.CenterVertically) {
        HazardBadge(h.kind, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.kind.spoken, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(where, h.byName, age).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (view.ahead == true) Palette.Amber else Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlassIconButton(R.drawable.ms_close, "Remove", size = 40.dp, iconSize = 18.dp) { onRemove(h.id) }
    }
}

@Composable
fun HazardBadge(kind: HazardKind, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(HazardGradient),
        contentAlignment = Alignment.Center,
    ) { Ico(kind.icon, size * 0.55f, Color.White) }
}

/** Big tiles, one per kind, so it's quick to hit with gloves at a stop. */
@Composable
fun HazardPicker(onPick: (HazardKind) -> Unit, onCancel: () -> Unit) {
    GlassDialog(onDismiss = onCancel) {
        Text("Mark a hazard here", style = MaterialTheme.typography.titleLarge)
        Text(
            "Riders behind you hear it as they get close, like \"Pothole in 300 meters\". You can also say \"RideComm, pothole\".",
            style = MaterialTheme.typography.bodyMedium,
        )
        HazardKind.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { kind ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(112.dp)
                            .glass(RoundedCornerShape(22.dp), tint = Color(0xFFFF5E3A), fillAlpha = 0.14f)
                            .clickable { onPick(kind) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        HazardBadge(kind, 42.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            kind.label,
                            style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        GlassButton("Cancel", modifier = Modifier.fillMaxWidth(), onClick = onCancel)
    }
}
