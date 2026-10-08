package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.group.DestinationState
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.group.Place
import com.ridecomm.app.group.PlaceSearch
import com.ridecomm.app.home.HomeSafeState
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.trip.BreakDue
import com.ridecomm.app.trip.TripSpeech
import com.ridecomm.app.ui.map.openDirections
import kotlinx.coroutines.launch

private val DestinationColor: Color @Composable get() = Palette.Go

/** Round coloured icon at the start of a card. */
@Composable
private fun CardIcon(icon: Int, color: Color, dark: Color) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Ico(icon, 22.dp, dark)
    }
}

/**
 * Where the group is heading, with one Navigate button for everyone; or a small button to set it.
 */
@Composable
fun DestinationCard(state: DestinationState, onPick: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    val d = state.destination
    if (d == null) {
        if (LocalLook.current != UiLook.CLASSIC) {
            if (LocalLook.current == UiLook.NEU) {
                ActionRow(R.drawable.ms_sports_score, "Where are we heading?", iconTint = NeuTokens.IconViolet, onClick = onPick)
            } else {
                ActionRow(R.drawable.ms_sports_score, "Where are we heading?", onClick = onPick)
            }
        } else {
            GlassButton("Where are we heading?", R.drawable.ms_sports_score, Modifier.fillMaxWidth(), height = 52.dp, onClick = onPick)
        }
        return
    }
    GlassCard(tint = DestinationColor, fillAlpha = 0.12f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(R.drawable.ms_sports_score, DestinationColor, Color(0xFF042A1B))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Heading to ${d.label}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = buildList {
                    val m = state.distanceM
                    when {
                        state.arrived -> add("You're here")
                        m != null -> add("${GroupMath.shortDistance(m)} away")
                    }
                    add("set by ${d.byName}")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Navigate", R.drawable.ms_navigation, Modifier.weight(1f), height = 52.dp) { openDirections(context, d.lat, d.lon) }
            GlassIconButton(R.drawable.ms_edit, "Change destination", size = 52.dp, iconSize = 20.dp, onClick = onPick)
            GlassIconButton(R.drawable.ms_close, "Clear destination", size = 52.dp, iconSize = 20.dp, onClick = onClear)
        }
    }
}

/** Search a place by name (or paste coordinates / a maps link) and set it for everyone. */
@Composable
fun DestinationDialog(onCancel: () -> Unit, onPick: (Place) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val search = {
        if (query.isNotBlank() && !busy) {
            busy = true
            error = null
            scope.launch {
                try {
                    results = PlaceSearch.search(query)
                    if (results.isNullOrEmpty()) error = "Nothing found. Try a town or landmark name."
                } catch (e: Exception) {
                    error = "Couldn't search. Check your internet."
                } finally {
                    busy = false
                }
            }
        }
    }
    GlassDialog(onDismiss = onCancel) {
        Text("Where are we heading?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Everyone hears it, sees how far it is and gets a Navigate button. Type a place, or paste coordinates or a Google Maps link.",
            style = MaterialTheme.typography.bodyMedium,
        )
        GlassTextField(
            value = query,
            onValueChange = { query = it.take(200) },
            label = "Place",
            placeholder = "e.g. Lonavala",
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        PrimaryButton(if (busy) "Searching…" else "Search", R.drawable.ms_search, Modifier.fillMaxWidth(), height = 52.dp, enabled = !busy && query.isNotBlank()) { search() }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Amber) }
        results?.forEach { place ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp), fillAlpha = 0.06f)
                    .clickable { onPick(place) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(R.drawable.ms_location_on, 22.dp, DestinationColor)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (place.detail.isNotBlank()) {
                        Text(place.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text("Search by OpenStreetMap.", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        GlassButton("Cancel", modifier = Modifier.fillMaxWidth(), onClick = onCancel)
    }
}

/** "2 hours riding · Time for a break?" with one tap to ask the group. */
@Composable
fun BreakCard(due: BreakDue, onAsk: () -> Unit, onNotNow: () -> Unit) {
    GlassCard(tint = Palette.Amber, fillAlpha = 0.14f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(R.drawable.ms_coffee, Palette.Amber, Color(0xFF2A1E05))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Time for a break?", style = MaterialTheme.typography.titleMedium)
                Text("${TripSpeech.duration(due.ridingMs).replaceFirstChar { it.uppercase() }} of riding since the last stop", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Ask for a break", R.drawable.ms_coffee, Modifier.weight(1.3f), height = 52.dp, onClick = onAsk)
            GlassButton("Not now", modifier = Modifier.weight(0.8f), height = 52.dp, onClick = onNotNow)
        }
    }
}

/**
 * End of the ride: who's home safe and who's still on the road, and my own "I'm home safe".
 * Only a small button until someone checks in.
 */
@Composable
fun HomeSafeCard(state: HomeSafeState, riders: List<Rider>, onImHome: () -> Unit, onLeave: () -> Unit) {
    val anyone = state.meHome || state.home.isNotEmpty()
    if (!anyone && state.leftNotHome.isEmpty()) {
        if (LocalLook.current != UiLook.CLASSIC) {
            ActionRow(R.drawable.ms_home, "I'm home safe", iconTint = Palette.Go, iconBackground = Palette.Go.copy(alpha = 0.22f), onClick = onImHome)
        } else {
            GlassButton("I'm home safe", R.drawable.ms_home, Modifier.fillMaxWidth(), height = 52.dp, onClick = onImHome)
        }
        return
    }
    val homeNames = buildList {
        if (state.meHome) add("You")
        addAll(state.home.values)
    }
    // Still in the ride and not home yet, plus riders who left without saying they got home.
    val onRoad = riders.filter { !it.isMe && it.id !in state.home }.map { it.name } +
        state.leftNotHome.filterKeys { it !in state.home }.values.map { "$it (left the ride)" }
    GlassCard(tint = Palette.Go, fillAlpha = 0.10f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(R.drawable.ms_home, Palette.Go, Color(0xFF042A1B))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Getting home", style = MaterialTheme.typography.titleMedium)
                if (homeNames.isNotEmpty()) Text("Home safe: ${homeNames.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = Palette.Go)
                if (onRoad.isNotEmpty()) Text("On the road: ${onRoad.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
                if (onRoad.isEmpty() && state.meHome) Text("Everyone is home", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.meHome) {
            GlassButton("Leave the ride", R.drawable.ms_logout, Modifier.fillMaxWidth(), height = 52.dp, onClick = onLeave)
        } else {
            PrimaryButton("I'm home safe", R.drawable.ms_home, Modifier.fillMaxWidth(), height = 52.dp, brush = Palette.GoGradient, contentColor = Color(0xFF052E1F), onClick = onImHome)
        }
    }
}
