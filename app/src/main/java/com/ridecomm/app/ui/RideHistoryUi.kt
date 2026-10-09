package com.ridecomm.app.ui

import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.score.Score
import com.ridecomm.app.score.ScoreBook
import com.ridecomm.app.score.ScoreEntry
import com.ridecomm.app.trip.RideHistory
import com.ridecomm.app.trip.RideLog
import com.ridecomm.app.trip.RideSummary
import com.ridecomm.app.trip.RoutePoint
import com.ridecomm.app.ui.map.RouteMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun km(m: Double) = String.format(Locale.US, "%.1f km", m / 1000)
private fun day(ms: Long) = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))
private fun clock(ms: Long) = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

/** The route's shape as a small line drawing (history rows; also stands in for the map in screenshots). */
@Composable
fun RouteShape(route: List<RoutePoint>, modifier: Modifier = Modifier, width: Float = 5f) {
    Canvas(modifier) {
        if (route.size < 2) return@Canvas
        val pad = width * 2
        val path = RidePicture.routePath(route, RectF(pad, pad, size.width - pad, size.height - pad)).asComposePath()
        drawPath(
            path,
            Brush.linearGradient(listOf(Color(0xFFFF8A1F), Color(0xFFFF3D81))),
            style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** On the home screen: the last few rides, tap one for its summary. */
@Composable
fun HistoryCard(rides: List<RideSummary>, onOpen: (Long) -> Unit, onAll: () -> Unit) {
    if (rides.isEmpty()) return
    GlassCard(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Your rides", Modifier.weight(1f))
            Text("See all", style = MaterialTheme.typography.labelLarge, color = Palette.Accent, modifier = Modifier.clickable(onClick = onAll).padding(6.dp))
        }
        rides.take(3).forEach { RideRow(it) { onOpen(it.id) } }
    }
}

@Composable
private fun RideRow(ride: RideSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33140F2E)),
            contentAlignment = Alignment.Center,
        ) { RouteShape(ride.route, Modifier.fillMaxSize(), width = 3.5f) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${day(ride.startedAtMs)} · ${clock(ride.startedAtMs)}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${km(ride.distanceM)} · ${RideLog.duration(ride.movingMs)} riding", style = MaterialTheme.typography.bodyMedium)
        }
        Ico(R.drawable.ms_chevron_right, 22.dp, Palette.TextSecondary)
    }
}

/** A full-screen page over whatever is showing, in the current look. */
@Composable
internal fun FullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    val look = LocalLook.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LookScope(look) { LookBackground(look, Modifier.fillMaxSize(), GlassSceneStyle.RIDE) { content() } }
    }
}

@Composable
internal fun PageTopBar(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(R.drawable.ms_arrow_back, "Back", size = 46.dp, iconSize = 22.dp, onClick = onClose)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FitText(title, MaterialTheme.typography.titleLarge, min = 14.sp)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** All my rides, newest first. */
@Composable
fun RideListScreen(onClose: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val changes by RideHistory.changes.collectAsState()
    val rides by produceState(emptyList<RideSummary>(), changes) { value = RideHistory.listAsync(context) }
    FullScreen(onClose) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTopBar("Your rides", "${rides.size} kept on this phone · ${km(rides.sumOf { it.distanceM })} in all", onClose)
            GlassCard(spacing = 10.dp) {
                if (rides.isEmpty()) Text("No rides yet. They appear here after you leave a ride.", style = MaterialTheme.typography.bodyMedium)
                rides.forEach { RideRow(it) { onOpen(it.id) } }
            }
        }
    }
}

/** One ride's summary as a full page. */
@Composable
fun RideSummaryScreen(id: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val ride by produceState<RideSummary?>(null, id) { value = RideHistory.getAsync(context, id) }
    val scores by Score.entries.collectAsState()
    LaunchedEffect(Unit) { Score.load(context) }
    FullScreen(onClose) {
        ride?.let { r ->
            val entry = if (Prefs.points(context)) scores.firstOrNull { kotlin.math.abs(it.atMs - r.startedAtMs) < 60_000 } else null
            RideSummaryContent(
                r,
                score = entry,
                earned = entry?.let { e -> ScoreBook.badges(scores).filter { it.earnedAtMs == e.atMs }.map { it.badge } }.orEmpty(),
                onClose = onClose,
                onShare = { RidePicture.share(context, r) },
                onDelete = {
                    RideHistory.delete(context, r.id)
                    onClose()
                },
            )
        }
    }
}

/**
 * The summary: map of the route, the numbers, the stops and who rode. [map] is the real map by
 * default; screenshot tests pass a stand-in.
 */
@Composable
fun RideSummaryContent(
    ride: RideSummary,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    map: @Composable (Modifier) -> Unit = { RouteMap(ride.route, ride.stops, it) },
    score: ScoreEntry? = null,
    earned: List<com.ridecomm.app.score.Badge> = emptyList(),
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTopBar(
            "${day(ride.startedAtMs)} · ${clock(ride.startedAtMs)}",
            "Ride ${ride.code}" + if (ride.riders.isNotEmpty()) " · with ${ride.riders.joinToString(", ")}" else "",
            onClose,
        )
        if (ride.route.size >= 2) {
            map(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)))
        } else {
            GlassCard { Text("No route for this ride: location was off.", style = MaterialTheme.typography.bodyMedium) }
        }
        val stats = listOf(
            km(ride.distanceM) to "Distance",
            RideLog.duration(ride.movingMs) to "Riding time",
            RideLog.duration(ride.totalMs) to "Total time",
            "${ride.averageKmh.toInt()} km/h" to "Average",
            "${ride.topKmh.toInt()} km/h" to "Top speed",
            "${ride.stops.size}" to if (ride.stops.size == 1) "Stop" else "Stops",
        )
        stats.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) ->
                    Column(Modifier.weight(1f).glass(RoundedCornerShape(18.dp), fillAlpha = 0.07f).padding(12.dp)) {
                        FitText(value, MaterialTheme.typography.titleLarge)
                        FitText(label, MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                    }
                }
            }
        }
        score?.let { RideScoreCard(it, earned) }
        if (ride.stops.isNotEmpty()) {
            GlassCard(spacing = 10.dp) {
                SectionLabel("Stops")
                ride.stops.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocationHelper.mapsLink(s.lat, s.lon)))) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(R.drawable.ms_coffee, 22.dp, Palette.Amber)
                        Spacer(Modifier.width(10.dp))
                        Text("${clock(s.atMs)} · ${RideLog.duration(s.durationMs)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Ico(R.drawable.ms_location_on, 20.dp, Palette.TextSecondary, "Open in Maps")
                    }
                }
            }
        }
        ButtonRow(2) {
            PrimaryButton("Share picture", R.drawable.ms_share, Modifier.share(1.2f), height = 56.dp, onClick = onShare)
            GlassButton(if (confirmDelete) "Tap to delete" else "Delete", R.drawable.ms_close, Modifier.share(0.8f), height = 56.dp) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        }
        Text("Kept only on this phone.", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
    }
}
