package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.ridecomm.app.group.Place
import com.ridecomm.app.group.PlaceSearch
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.ui.map.PickerControl
import com.ridecomm.app.ui.map.PickerMap
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Pick a place on the map: move the map under the pin, see the spot's name, then "Use this spot".
 * For places search doesn't know (a friend's gate, a turn off the highway).
 */
@Composable
fun MapPicker(start: Pair<Double, Double>?, title: String, onCancel: () -> Unit, onPick: (Place) -> Unit) {
    val context = LocalContext.current
    var center by remember { mutableStateOf(start) }
    var name by remember { mutableStateOf<String?>(null) }
    val control = remember { PickerControl() }
    // The spot's name once the map has rested a moment.
    LaunchedEffect(center) {
        name = null
        val (lat, lon) = center ?: return@LaunchedEffect
        delay(500)
        name = PlaceSearch.nameOf(lat, lon)
    }
    FullScreen(onClose = onCancel) {
        Box(Modifier.fillMaxSize()) {
            PickerMap(start, control, onCenter = { lat, lon -> center = lat to lon }, modifier = Modifier.fillMaxSize())
            // The pin's point is the middle of the map.
            Box(Modifier.align(Alignment.Center).offset(y = (-22).dp)) {
                Ico(R.drawable.ms_location_on, 48.dp, Color(0xFFFF3D81))
            }
            Box(Modifier.align(Alignment.Center).size(6.dp).clip(CircleShape).background(Color(0xFF140F2E)))
            Row(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(R.drawable.ms_arrow_back, "Back", size = 46.dp, iconSize = 22.dp, onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Move the map to put the pin on the place",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xB3140F2E)).padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassIconButton(R.drawable.ms_my_location, "My location", size = 52.dp, iconSize = 24.dp) {
                    LocationHelper.lastKnown(context)?.let { control.moveTo(it.latitude, it.longitude) }
                }
                GlassCard(spacing = 10.dp) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
                    val c = center
                    Text(name ?: if (c == null) "Move the map" else "Finding the name…", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c != null) Text(String.format(Locale.US, "%.5f, %.5f", c.first, c.second), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    PrimaryButton("Use this spot", R.drawable.ms_check_circle, Modifier.fillMaxWidth(), height = 56.dp, enabled = c != null) {
                        val (lat, lon) = c ?: return@PrimaryButton
                        onPick(Place((name ?: "Pinned spot").take(40), String.format(Locale.US, "%.5f, %.5f", lat, lon), lat, lon))
                    }
                }
            }
        }
    }
}
