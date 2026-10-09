package com.ridecomm.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.nearby.AheadLogic
import com.ridecomm.app.nearby.AheadPoi
import com.ridecomm.app.nearby.LowFuelState
import com.ridecomm.app.nearby.PoiKind
import com.ridecomm.app.nearby.PoiSearch
import com.ridecomm.app.nearby.TravelDirection
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.trip.TripTracker

private val PoiKind.icon get() = when (this) {
    PoiKind.FUEL -> R.drawable.ms_local_gas_station
    PoiKind.FOOD -> R.drawable.ms_restaurant
    PoiKind.MECHANIC -> R.drawable.ms_build
}

private fun directions(context: android.content.Context, lat: Double, lon: Double) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GroupTracker.directionsLink(lat, lon)))) }
}

/**
 * "On the road ahead": find petrol, food or a mechanic in the direction I'm riding, and "Low fuel"
 * to be guided to the next pump. While low fuel is on, the card shows the pump I'm heading for.
 */
@Composable
fun RoadAheadCard(lowFuel: LowFuelState, onFind: (PoiKind) -> Unit, onLowFuel: () -> Unit, onGotFuel: () -> Unit) {
    val context = LocalContext.current
    if (lowFuel.active) {
        GlassCard(tint = Palette.Amber, fillAlpha = 0.14f, spacing = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.ms_local_gas_station, 24.dp, Palette.Amber)
                Spacer(Modifier.width(10.dp))
                Text("Low fuel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (lowFuel.searching) Text("Searching…", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
            }
            val t = lowFuel.target
            if (t != null) {
                Text(t.poi.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(AheadLogic.describe(t, lowFuel.haveDirection), style = MaterialTheme.typography.bodyLarge, color = Palette.Amber)
            } else {
                Text(lowFuel.note ?: "Looking for the next petrol pump ahead…", style = MaterialTheme.typography.bodyMedium)
            }
            ButtonRow(if (t != null) 2 else 1) {
                if (t != null) {
                    PrimaryButton("Navigate", R.drawable.ms_navigation, Modifier.share(1.2f), height = 52.dp) { directions(context, t.poi.lat, t.poi.lon) }
                }
                GlassButton("Got fuel", R.drawable.ms_check_circle, Modifier.share(0.8f), height = 52.dp, onClick = onGotFuel)
            }
        }
        return
    }
    GlassCard(spacing = 12.dp) {
        SectionLabel("On the road ahead")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PoiKind.entries.forEach { kind ->
                Column(
                    Modifier
                        .weight(1f)
                        .glass(RoundedCornerShape(18.dp), fillAlpha = 0.07f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onFind(kind) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Ico(kind.icon, 24.dp, Palette.Accent)
                    Spacer(Modifier.height(4.dp))
                    FitText(kind.label, MaterialTheme.typography.labelLarge)
                }
            }
        }
        GlassButton("Low fuel: guide me to petrol", R.drawable.ms_local_gas_station, Modifier.fillMaxWidth(), height = 50.dp, tint = Palette.Amber, onClick = onLowFuel)
    }
}

/** Places of one kind on the road ahead, nearest along the road first. */
@Composable
fun FinderDialog(kind: PoiKind, groupMapOn: Boolean, onClose: () -> Unit, preset: List<AheadPoi>? = null) {
    val context = LocalContext.current
    var results by remember { mutableStateOf(preset) }
    var error by remember { mutableStateOf<String?>(null) }
    var haveDirection by remember { mutableStateOf(true) }
    LaunchedEffect(kind) {
        if (preset != null) return@LaunchedEffect
        val me = TripTracker.location.value ?: LocationHelper.lastKnown(context)
        if (me == null) {
            error = "Your location isn't known yet. Allow location and try again."
            return@LaunchedEffect
        }
        val bearing = TravelDirection.current(me.latitude, me.longitude)
        haveDirection = bearing != null
        runCatching { PoiSearch.search(context, kind, me.latitude, me.longitude, bearing) }
            .onSuccess { found -> results = AheadLogic.ahead(me.latitude, me.longitude, bearing, found, kind.radiusM.toDouble()).take(6) }
            .onFailure { error = "Couldn't search. Check your internet." }
    }
    GlassDialog(onDismiss = onClose) {
        Text("${kind.label} ahead", style = MaterialTheme.typography.headlineMedium)
        if (!haveDirection) Text("Start riding to see what's ahead; for now, the nearest.", style = MaterialTheme.typography.bodyMedium)
        val list = results
        when {
            error != null -> Text(error!!, style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
            list == null -> Text("Searching…", style = MaterialTheme.typography.bodyMedium)
            list.isEmpty() -> Text("Nothing found within ${kind.radiusM / 1000} km ahead.", style = MaterialTheme.typography.bodyMedium)
        }
        list?.forEach { a ->
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), fillAlpha = 0.06f).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(a.poi.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(AheadLogic.describe(a, haveDirection), style = MaterialTheme.typography.bodyMedium)
                ButtonRow(if (groupMapOn) 2 else 1) {
                    PrimaryButton("Navigate", R.drawable.ms_navigation, Modifier.share(), height = 46.dp) { directions(context, a.poi.lat, a.poi.lon) }
                    if (groupMapOn) {
                        GlassButton("Stop here", R.drawable.ms_flag, Modifier.share(), height = 46.dp, tint = Color.White) {
                            GroupTracker.setRegroup(a.poi.lat, a.poi.lon, when (kind) {
                                PoiKind.FUEL -> "Fuel stop"
                                PoiKind.FOOD -> "Food stop"
                                PoiKind.MECHANIC -> "Mechanic"
                            })
                            onClose()
                        }
                    }
                }
            }
        }
        Text("Places from Google Maps or OpenStreetMap. Distances are straight-line.", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        GlassButton("Close", modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}
