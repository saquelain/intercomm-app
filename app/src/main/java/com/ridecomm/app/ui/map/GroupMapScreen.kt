package com.ridecomm.app.ui.map

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import com.ridecomm.app.R
import com.ridecomm.app.group.RideRolesState
import com.ridecomm.app.hazard.HazardView
import com.ridecomm.app.ui.icon
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.group.GroupState
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.group.RegroupLogic
import com.ridecomm.app.group.Destination
import com.ridecomm.app.group.RegroupPoint
import com.ridecomm.app.group.Relation
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ui.GlassButton
import com.ridecomm.app.ui.GlassCard
import com.ridecomm.app.ui.GlassDialog
import com.ridecomm.app.ui.GlassIconButton
import com.ridecomm.app.ui.GlassTextField
import com.ridecomm.app.ui.Ico
import com.ridecomm.app.ui.Palette
import com.ridecomm.app.ui.PrimaryButton
import com.ridecomm.app.ui.glass
import com.ridecomm.app.ui.ActionRow
import com.ridecomm.app.ui.GlassMapPanel
import com.ridecomm.app.ui.LocalLook
import com.ridecomm.app.ui.UiLook
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** A position shown on the map, for fitting and centring. */
internal data class Spot(val lat: Double, val lon: Double)

/**
 * Full-screen live map of the group: every rider sharing their location (photo, name, direction),
 * me, and the regroup point. Long-press to set a regroup point for everyone.
 *
 * [mapContent] is the real map by default; screenshot tests pass a stand-in.
 */
/** "Lead" / "Sweep" / null for a rider. */
internal fun roleLabel(roles: RideRolesState, id: String): String? = when (id) {
    roles.leadId -> "Lead"
    roles.sweepId -> "Sweep"
    else -> null
}

@Composable
internal fun GroupMapScreen(
    group: GroupState,
    riders: List<Rider>,
    photos: Map<String, Bitmap>,
    onClose: () -> Unit,
    hazards: List<HazardView> = emptyList(),
    roles: RideRolesState = RideRolesState(),
    destination: Destination? = null,
    /** Set when the Shared destination setting is on: long-press can set it too. */
    onSetDestination: ((lat: Double, lon: Double, label: String) -> Unit)? = null,
    mapContent: (@Composable (places: List<GroupOverlay.Place>) -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var follow by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf<Spot?>(null) }
    var fitKey by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(Prefs.mapDark(context)) }
    var pinAt by remember { mutableStateOf<GeoPoint?>(null) }
    val names = riders.associate { it.id to it.name }
    val talking = riders.filter { it.isSpeaking }.map { it.id }.toSet()
    val now = System.currentTimeMillis()

    val places = buildList {
        hazards.forEach { v ->
            val h = v.hazard
            add(GroupOverlay.Place(h.lat, h.lon) { x, y, _ -> MapMarker.Hazard(x, y, h.kind.icon, h.kind.label) })
        }
        destination?.let { d ->
            add(GroupOverlay.Place(d.lat, d.lon) { x, y, _ -> MapMarker.Regroup(x, y, d.label, "Destination", destination = true) })
        }
        group.regroup?.let { p ->
            val detail = "${p.arrived.size} of ${riders.size.coerceAtLeast(1)} here"
            add(GroupOverlay.Place(p.lat, p.lon) { x, y, _ -> MapMarker.Regroup(x, y, p.label, detail) })
        }
        group.positions.forEach { (id, pos) ->
            val moving = (pos.speedKmh ?: 0f) >= 8f
            add(
                GroupOverlay.Place(pos.lat, pos.lon) { x, y, _ ->
                    MapMarker.Rider(
                        x, y,
                        name = names[id] ?: pos.name.ifBlank { "Rider" },
                        photo = photos[id],
                        talking = id in talking,
                        headingDeg = pos.headingDeg?.takeIf { moving },
                        stale = now - pos.atMs > STALE_MS,
                        role = roleLabel(roles, id),
                    )
                },
            )
        }
        group.me?.let { me ->
            val self = riders.firstOrNull { it.isMe }
            add(
                GroupOverlay.Place(me.lat, me.lon) { x, y, mpp ->
                    MapMarker.Me(x, y, me.headingDeg?.toFloat(), me.accuracyM / mpp, self?.name.orEmpty(), self?.let { photos[it.id] })
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        val onLongPress: (GeoPoint) -> Unit = { pinAt = it }
        if (mapContent != null) mapContent(places) else LiveMap(group, places, focus, follow, fitKey, dark, onLongPress)

        // Top bar
        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .background(mapPanel(), RoundedCornerShape(24.dp))
                .glass(RoundedCornerShape(24.dp), fillAlpha = 0.10f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(R.drawable.ms_arrow_back, "Back", size = 46.dp, iconSize = 22.dp, onClick = onClose)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Group map", style = MaterialTheme.typography.titleMedium)
                val onMap = group.positions.size + if (group.me != null) 1 else 0
                Text("$onMap of ${riders.size.coerceAtLeast(1)} riders on the map", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
            }
            GlassIconButton(if (dark) R.drawable.ms_light_mode else R.drawable.ms_dark_mode, if (dark) "Light map" else "Dark map", size = 46.dp, iconSize = 22.dp) {
                dark = !dark
                Prefs.setMapDark(context, dark)
            }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(R.drawable.ms_zoom_out_map, "Show everyone", size = 46.dp, iconSize = 22.dp) {
                follow = false
                focus = null
                fitKey++
            }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(
                R.drawable.ms_my_location,
                if (follow) "Stop following me" else "Follow me",
                size = 46.dp,
                iconSize = 22.dp,
                tint = if (follow) Palette.Orange else Color.White,
            ) { follow = !follow }
        }

        // Bottom panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                MapSetup.ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .background(mapPanel(), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            RiderChips(group, names) { lat, lon ->
                follow = false
                focus = Spot(lat, lon)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(mapPanel(), RoundedCornerShape(26.dp))
                    .glass(RoundedCornerShape(26.dp), fillAlpha = 0.10f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!group.sharing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(R.drawable.ms_location_on, 18.dp, Palette.Amber)
                        Spacer(Modifier.width(8.dp))
                        Text("Turn on location (GPS) so the group can see you.", style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
                    }
                }
                val point = group.regroup
                if (point != null) {
                    RegroupInfo(point, group.regroupDistanceM, group.regroupRelation, riders.size)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Navigate", R.drawable.ms_navigation, Modifier.weight(1f), height = 52.dp) {
                            openDirections(context, point.lat, point.lon)
                        }
                        GlassButton("Clear", R.drawable.ms_close, Modifier.weight(0.7f), height = 52.dp) { GroupTracker.clearRegroup() }
                    }
                } else {
                    Text(
                        "Long-press anywhere on the map to set a regroup point for everyone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    GlassButton("Regroup at my location", R.drawable.ms_flag, Modifier.fillMaxWidth(), height = 52.dp) {
                        val me = group.me
                        if (me == null) {
                            Toast.makeText(context, "Waiting for your location…", Toast.LENGTH_SHORT).show()
                        } else {
                            pinAt = GeoPoint(me.lat, me.lon)
                        }
                    }
                }
            }
        }
    }

    pinAt?.let { at ->
        RegroupDialog(
            onCancel = { pinAt = null },
            onSet = { label ->
                GroupTracker.setRegroup(at.latitude, at.longitude, label)
                pinAt = null
            },
            onDestination = onSetDestination?.let { set ->
                { label: String ->
                    set(at.latitude, at.longitude, label)
                    pinAt = null
                }
            },
        )
    }
}

private const val STALE_MS = 45_000L

/** Panels over the map are nearly opaque so street names underneath don't show through the text. */
private val MapPanel = Color(0xE0101222)

@Composable
private fun mapPanel() = if (LocalLook.current == UiLook.GLASS) GlassMapPanel else MapPanel

/** The osmdroid map with the dark tiles and the group overlay. */
@Composable
private fun LiveMap(
    group: GroupState,
    places: List<GroupOverlay.Place>,
    focus: Spot?,
    follow: Boolean,
    fitKey: Int,
    dark: Boolean,
    onLongPress: (GeoPoint) -> Unit,
) {
    val context = LocalContext.current
    val overlay = remember { GroupOverlay(context) { onLongPress(it) } }
    // Fit everyone on first open (once there's something to show) and on each "Show everyone".
    var fittedKey by remember { mutableIntStateOf(-1) }
    var lastFocus by remember { mutableStateOf<Spot?>(null) }
    val map = remember {
        MapSetup.configure(context)
        MapView(context).apply {
            setTileSource(MapSetup.tiles)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            controller.setZoom(14.0)
            overlays.add(overlay)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxSize(),
        update = { m ->
            m.overlayManager.tilesOverlay.setColorFilter(if (dark) MapSetup.darkFilter else null)
            m.overlayManager.tilesOverlay.loadingBackgroundColor = if (dark) android.graphics.Color.rgb(0x10, 0x12, 0x1C) else android.graphics.Color.rgb(0xF2, 0xEF, 0xE9)
            overlay.places = places
            val spots = spotsOf(group)
            when {
                focus != null && focus != lastFocus -> {
                    lastFocus = focus
                    m.controller.animateTo(GeoPoint(focus.lat, focus.lon), 16.0, 600L)
                }
                follow && group.me != null -> m.controller.animateTo(GeoPoint(group.me.lat, group.me.lon))
                fitKey != fittedKey && spots.isNotEmpty() -> {
                    fittedKey = fitKey
                    lastFocus = null
                    m.post { fit(m, spots) }
                }
            }
            m.invalidate()
        },
    )
}

private fun spotsOf(group: GroupState): List<Spot> = buildList {
    group.me?.let { add(Spot(it.lat, it.lon)) }
    group.positions.values.forEach { add(Spot(it.lat, it.lon)) }
    group.regroup?.let { add(Spot(it.lat, it.lon)) }
}

private fun fit(map: MapView, spots: List<Spot>) {
    if (spots.size == 1) {
        map.controller.animateTo(GeoPoint(spots[0].lat, spots[0].lon), 15.5, 500L)
        return
    }
    val box = BoundingBox(
        spots.maxOf { it.lat }, spots.maxOf { it.lon }, spots.minOf { it.lat }, spots.minOf { it.lon },
    )
    // Room for the top bar and bottom panel.
    map.zoomToBoundingBox(box.increaseByScale(1.6f), true, (90 * map.resources.displayMetrics.density).toInt())
    if (map.zoomLevelDouble > 17.0) map.controller.setZoom(17.0)
}

/** "Amit · 1.2 km ahead" chips; tap one to centre the map on that rider. */
@Composable
private fun RiderChips(group: GroupState, names: Map<String, String>, onPick: (Double, Double) -> Unit) {
    if (group.positions.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        group.positions.entries.sortedBy { it.value.distanceM ?: Double.MAX_VALUE }.forEach { (id, pos) ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MapPanel)
                    .glass(RoundedCornerShape(18.dp), fillAlpha = 0.10f)
                    .clickable { onPick(pos.lat, pos.lon) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Cyan))
                Spacer(Modifier.width(8.dp))
                Text(names[id] ?: pos.name, style = MaterialTheme.typography.labelLarge)
                pos.distanceM?.let { d ->
                    Text(" · " + distanceText(d, pos.relation), style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
                }
            }
        }
    }
}

private fun distanceText(d: Double, relation: Relation?): String =
    if (d < GroupMath.TOGETHER_M) {
        "with you"
    } else {
        GroupMath.shortDistance(d) + when (relation) {
            Relation.AHEAD -> " ahead"
            Relation.BEHIND -> " behind"
            else -> ""
        }
    }

/** Flag, label and "4.2 km ahead · 2 of 4 here · set by Rahul". */
@Composable
internal fun RegroupInfo(point: RegroupPoint, distanceM: Double?, relation: Relation?, riderCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.Amber), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ms_flag, 22.dp, Color(0xFF2A1E05))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Regroup: ${point.label}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                distanceM?.let { add(distanceText(it, relation)) }
                add("${point.arrived.size} of ${riderCount.coerceAtLeast(1)} here")
                add("set by ${point.byName}")
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pick a name for the regroup point, then everyone gets it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegroupDialog(onCancel: () -> Unit, onSet: (String) -> Unit, onDestination: ((String) -> Unit)? = null) {
    var label by remember { mutableStateOf(RegroupLogic.LABELS.first()) }
    GlassDialog(onDismiss = onCancel) {
        Text("Regroup point", style = MaterialTheme.typography.headlineMedium)
        Text("Everyone hears it, sees it on the map and can navigate to it.", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RegroupLogic.LABELS.forEach { option ->
                val selected = option == label
                Box(
                    Modifier
                        .height(44.dp)
                        .glass(
                            RoundedCornerShape(14.dp),
                            tint = if (selected) Palette.Orange else Color.White,
                            fillAlpha = if (selected) 0.32f else 0.06f,
                        )
                        .clickable { label = option }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Palette.TextSecondary)
                }
            }
        }
        GlassTextField(
            value = label,
            onValueChange = { label = it.take(24) },
            label = "Name",
            placeholder = "e.g. Shell pump",
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            PrimaryButton("Set for everyone", R.drawable.ms_flag, Modifier.weight(1.4f), height = 56.dp) { onSet(label) }
        }
        if (onDestination != null) {
            GlassButton("Make it the destination instead", R.drawable.ms_sports_score, Modifier.fillMaxWidth(), height = 52.dp) {
                onDestination(if (label in RegroupLogic.LABELS) "Destination" else label)
            }
        }
    }
}

/** On the ride screen: the regroup point (with Navigate) or a way into the map. */
@Composable
internal fun GroupMapCard(group: GroupState, riderCount: Int, onOpen: () -> Unit) {
    val context = LocalContext.current
    val point = group.regroup
    if (point == null && LocalLook.current == UiLook.GLASS) {
        val onMap = group.positions.size + if (group.me != null) 1 else 0
        ActionRow(
            R.drawable.ms_map,
            "Group map",
            if (group.sharing) "$onMap of ${riderCount.coerceAtLeast(1)} riders on the map · set a regroup point" else "Turn on location to share yours",
            onClick = onOpen,
        )
        return
    }
    GlassCard(tint = if (point != null) Palette.Amber else Color.White, fillAlpha = if (point != null) 0.14f else 0.09f) {
        if (point != null) {
            RegroupInfo(point, group.regroupDistanceM, group.regroupRelation, riderCount)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Navigate", R.drawable.ms_navigation, Modifier.weight(1f), height = 52.dp) {
                    openDirections(context, point.lat, point.lon)
                }
                GlassButton("Map", R.drawable.ms_map, Modifier.weight(0.8f), height = 52.dp, onClick = onOpen)
            }
        } else {
            Row(Modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.Cyan.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Ico(R.drawable.ms_map, 22.dp, Palette.Cyan)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Group map", style = MaterialTheme.typography.titleMedium)
                    val onMap = group.positions.size + if (group.me != null) 1 else 0
                    Text(
                        if (group.sharing) "$onMap of ${riderCount.coerceAtLeast(1)} riders on the map · set a regroup point" else "Turn on location to share yours",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Ico(R.drawable.ms_arrow_forward, 22.dp, Palette.TextSecondary)
            }
        }
    }
}

/** Opens turn-by-turn directions in Google Maps (or any maps app). */
fun openDirections(context: android.content.Context, lat: Double, lon: Double) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GroupTracker.directionsLink(lat, lon)))
    runCatching { context.startActivity(intent) }
}
