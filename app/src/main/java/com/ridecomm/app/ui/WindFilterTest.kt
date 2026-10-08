package com.ridecomm.app.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.ridecomm.app.audio.GateSettings
import androidx.compose.runtime.produceState
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomm.app.R
import com.ridecomm.app.audio.FilterTester
import com.ridecomm.app.audio.GateAnalysis
import com.ridecomm.app.audio.GateFrame
import com.ridecomm.app.audio.GateStatus
import com.ridecomm.app.audio.GateTotals
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.ride.RideManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Meters show -70..0 dBFS. */
private const val METER_FLOOR_DB = -70f

/**
 * Shows the wind filter at work, and lets every part of it be tuned. On a ride: live meters on the
 * call's own mic. Otherwise: record a few seconds, see what was sent and what was blocked, and
 * play both back. Any change re-runs the recording through the filter straight away.
 */
@Composable
fun WindFilterTestDialog(
    settings: GateSettings?,
    onSettings: (GateSettings?) -> Unit,
    inRide: Boolean,
    onClose: () -> Unit,
) {
    GlassDialog(onDismiss = onClose) {
        Text("Wind filter test", style = MaterialTheme.typography.headlineMedium)
        GateChips(settings, onSettings)
        Text(settingsHint(settings), style = MaterialTheme.typography.bodyMedium)
        if (inRide) LiveRideMeter() else RecordedTest(settings)
        if (settings != null) FineTune(settings, onSettings)
        GlassButton("Done", modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

private fun settingsHint(settings: GateSettings?) = when (settings?.preset) {
    null -> if (settings == null) {
        "Off: everything your mic hears goes to the group, wind included."
    } else {
        "Custom: your own fine-tuned settings (below)."
    }
    GateSettings.Preset.LOW -> "Low: blocks the most noise. You need to speak up clearly to get through."
    GateSettings.Preset.MEDIUM -> "Medium: blocks wind and engine, lets normal speech through."
    GateSettings.Preset.HIGH -> "High: even quiet speech gets through, but so does more noise."
}

/** Sliders for each part of the filter, each explained in plain words. */
@Composable
internal fun FineTune(settings: GateSettings, onSettings: (GateSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Fine-tune", Modifier.weight(1f))
            if (settings != GateSettings.MEDIUM) {
                Text(
                    "Reset",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Cyan,
                    modifier = Modifier.clickable { onSettings(GateSettings.MEDIUM) }.padding(4.dp),
                )
            }
        }
        TuneSlider(
            title = "Voice sensitivity",
            help = "How quiet your voice can be and still get through.",
            value = settings.thresholdDb,
            // Left = only loud speech (high threshold), right = quiet speech too (low threshold).
            range = GateSettings.THRESHOLD_MIN..GateSettings.THRESHOLD_MAX,
            reversed = true,
            left = "Loud voice only",
            right = "Quiet voice too",
            valueText = "${settings.thresholdDb.roundToInt()} dB",
        ) { onSettings(settings.copy(thresholdDb = it.roundToInt().toFloat())) }
        TuneSlider(
            title = "Wind rejection",
            help = "How strictly deep rumble counts as wind. Strong blocks more wind, but can cut a deep or muffled voice.",
            value = settings.rumbleAllowanceDb,
            range = GateSettings.RUMBLE_MIN..GateSettings.RUMBLE_MAX,
            reversed = true,
            left = "Gentle",
            right = "Strong",
            valueText = windLabel(settings.rumbleAllowanceDb),
        ) { onSettings(settings.copy(rumbleAllowanceDb = it.roundToInt().toFloat())) }
        TuneSlider(
            title = "Keep sending after you stop",
            help = "So the ends of words aren't cut. Longer lets a little more wind through after you speak.",
            value = settings.holdMs.toFloat(),
            range = GateSettings.HOLD_MIN.toFloat()..GateSettings.HOLD_MAX.toFloat(),
            left = "Short",
            right = "Long",
            valueText = String.format(Locale.US, "%.1f s", settings.holdMs / 1000f),
        ) { onSettings(settings.copy(holdMs = ((it / 50).roundToInt() * 50))) }
        TuneSlider(
            title = "Noise reduction",
            help = "How much quieter blocked noise gets. Less than silence keeps a bit of natural background.",
            value = settings.reductionDb,
            range = GateSettings.REDUCTION_MIN..GateSettings.SILENCE_DB,
            left = "A little",
            right = "Silence",
            valueText = if (settings.reductionDb >= GateSettings.SILENCE_DB) "Silence" else "−${settings.reductionDb.roundToInt()} dB",
        ) { onSettings(settings.copy(reductionDb = it.roundToInt().toFloat())) }
    }
}

private fun windLabel(allowanceDb: Float) = when {
    allowanceDb <= -3f -> "Very strong"
    allowanceDb <= 3f -> "Strong"
    allowanceDb <= 9f -> "Normal"
    else -> "Gentle"
}

@Composable
private fun TuneSlider(
    title: String,
    help: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    left: String,
    right: String,
    valueText: String,
    reversed: Boolean = false,
    onChange: (Float) -> Unit,
) {
    // A reversed slider runs from the top of the range on the left to the bottom on the right.
    val shown = if (reversed) range.endInclusive + range.start - value else value
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = Palette.Orange)
        }
        Text(help, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = shown.coerceIn(range),
            onValueChange = { onChange(if (reversed) range.endInclusive + range.start - it else it) },
            valueRange = range,
            colors = lookSliderColors(),
        )
        Row {
            Text(left, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
            Text(right, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
    }
}

/** On a ride: the call's mic, live, plus totals since the ride started. */
@Composable
private fun LiveRideMeter() {
    val frame by MicGate.live.collectAsStateWithLifecycle()
    val totals by MicGate.totals.collectAsStateWithLifecycle()
    val ride by RideManager.state.collectAsStateWithLifecycle()
    if (ride.micMuted) {
        Text("Your mic is off. Unmute to see the filter at work.", style = MaterialTheme.typography.bodyLarge, color = Palette.Amber)
    } else {
        LiveMeter(frame)
    }
    SectionLabel("This ride so far")
    TotalsRow(totals)
    Text(
        "To record yourself and hear before and after, open this test when you're not on a ride.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextTertiary,
    )
}

/** Off a ride: record, look, listen. */
@Composable
private fun RecordedTest(settings: GateSettings?) {
    val context = LocalContext.current
    val tester = remember { FilterTester() }
    DisposableEffect(tester) { onDispose { tester.release() } }
    val state by tester.state.collectAsStateWithLifecycle()
    val live by tester.live.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(settings)
    val record = { tester.record(context) { current } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) record() else Toast.makeText(context, "The test needs the microphone", Toast.LENGTH_LONG).show()
    }
    val start = { if (tester.hasPermission(context)) record() else permission.launch(Manifest.permission.RECORD_AUDIO) }

    when (val s = state) {
        FilterTester.State.Idle, is FilterTester.State.Failed -> {
            if (s is FilterTester.State.Failed) Text(s.message, style = MaterialTheme.typography.bodyLarge, color = Palette.Stop)
            Steps()
            PrimaryButton("Record 8 seconds", R.drawable.ms_mic, Modifier.fillMaxWidth(), onClick = start)
        }
        is FilterTester.State.Recording -> {
            LiveMeter(live)
            ProgressLine(s.progress)
            Text("Talk, then blow on the mic…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
        }
        is FilterTester.State.Recorded -> {
            // Re-run off the main thread, a moment after a slider stops moving; keep showing the last result meanwhile.
            val analysis by produceState(remember(s) { GateAnalysis.run(s.clip, FilterTester.RATE, settings) }, s, settings) {
                delay(120)
                value = withContext(Dispatchers.Default) { GateAnalysis.run(s.clip, FilterTester.RATE, settings) }
            }
            var playhead by remember { mutableStateOf<Float?>(null) }
            var playing by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(playing) {
                while (playing != null) {
                    delay(30)
                    playhead = tester.playhead()
                    if (playhead == null) playing = null
                }
                playhead = null
            }
            ClipView(s.clip, analysis, playhead)
            TotalsRow(analysis.totals)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Your mic", R.drawable.ms_play_arrow, Modifier.weight(1f), height = 52.dp) {
                    tester.play(s.clip)
                    playing = "mic"
                }
                PrimaryButton(
                    "Group hears",
                    R.drawable.ms_play_arrow,
                    Modifier.weight(1f),
                    brush = Palette.GoGradient,
                    contentColor = Palette.Night,
                    height = 52.dp,
                ) {
                    tester.play(analysis.filtered)
                    playing = "group"
                }
            }
            GlassButton("Record again", R.drawable.ms_replay, Modifier.fillMaxWidth(), height = 52.dp, onClick = start)
        }
    }
}

@Composable
private fun Steps() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "Say a few words, like you would to your group.",
            "Then blow across the mic, rub it, or hold the phone out of a window: that's wind.",
            "See what got through, and play it back before and after.",
        ).forEachIndexed { i, step ->
            Row {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Palette.Orange, modifier = Modifier.width(22.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(Palette.Brand),
        )
    }
}

/** What the filter is doing now, as a status line and four level bars. */
@Composable
internal fun LiveMeter(frame: GateFrame?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GateStatusLine(frame)
        LevelBar("Your mic", frame?.micDb, Color.White.copy(alpha = 0.85f))
        LevelBar("Sent to the group", frame?.sentDb, Palette.Go)
        SectionLabel("What the filter listens to")
        LevelBar("Voice", frame?.voiceDb, Palette.Cyan, marker = frame?.thresholdDb, markerLabel = "voice must pass the line")
        LevelBar("Wind and engine rumble", frame?.lowDb, Palette.Amber)
    }
}

@Composable
private fun GateStatusLine(frame: GateFrame?) {
    val off = frame != null && frame.thresholdDb == null
    val (icon, color, title, detail) = when {
        frame == null -> Quad(R.drawable.ms_mic, Palette.TextTertiary, "Waiting for the mic…", "")
        off -> Quad(R.drawable.ms_mic, Palette.TextSecondary, "Filter off", "Everything is sent")
        frame.status == GateStatus.VOICE -> Quad(R.drawable.ms_mic, Palette.Go, "Sending your voice", "The group hears you")
        frame.status == GateStatus.NOISE -> Quad(R.drawable.ms_air, Palette.Amber, "Blocking noise", "The group hears silence")
        else -> Quad(R.drawable.ms_mic, Palette.TextTertiary, "Quiet", "Nothing to block")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), tint = color, fillAlpha = 0.14f, rimAlpha = 0.25f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Ico(icon, 22.dp, color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Quad(val icon: Int, val color: Color, val title: String, val detail: String)

/** A level from [METER_FLOOR_DB] (empty) to 0 dBFS (full), with an optional threshold line. */
@Composable
private fun LevelBar(label: String, db: Float?, color: Color, marker: Float? = null, markerLabel: String? = null) {
    val target = db?.let { ((it - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f) } ?: 0f
    val fill by animateFloatAsState(target, tween(80), label = label)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            if (marker != null && markerLabel != null) {
                Text(markerLabel, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r)
            if (fill > 0.005f) drawRoundRect(color, size = Size(size.width * fill, size.height), cornerRadius = r)
            if (marker != null) {
                val x = size.width * ((marker - METER_FLOOR_DB) / -METER_FLOOR_DB).coerceIn(0f, 1f)
                drawRect(Color(0xFF0E0B22), Offset(x - 3.dp.toPx(), -2.dp.toPx()), Size(6.dp.toPx(), size.height + 4.dp.toPx()))
                drawRect(Color.White, Offset(x - 1.dp.toPx(), -2.dp.toPx()), Size(2.dp.toPx(), size.height + 4.dp.toPx()))
            }
        }
    }
}

/**
 * The recording as two bar waveforms, one bar per 50 ms: what the mic heard (coloured by what
 * the filter decided) and what the group would hear.
 */
@Composable
internal fun ClipView(clip: FloatArray, analysis: GateAnalysis, playhead: Float?) {
    val micPeaks = remember(clip) { peaks(clip, analysis.frames.size) }
    val sentPeaks = remember(analysis) { peaks(analysis.filtered, analysis.frames.size) }
    val statuses = analysis.frames.map { it.status }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val go = Palette.Go
        val amber = Palette.Amber
        val idle = Palette.TextTertiary
        Text("Your mic heard", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        Waveform(micPeaks, 64.dp, playhead) { i ->
            when (statuses.getOrNull(i)) {
                GateStatus.VOICE -> go
                GateStatus.NOISE -> amber
                else -> idle
            }
        }
        Text("The group hears", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        Waveform(sentPeaks, 64.dp, playhead) { go }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Palette.Go, "Voice: sent")
            Legend(Palette.Amber, "Noise: blocked")
            Legend(Color.White.copy(alpha = 0.3f), "Quiet")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun Waveform(peaks: FloatArray, height: Dp, playhead: Float?, colorOf: (Int) -> Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f)),
    ) {
        val n = peaks.size.coerceAtLeast(1)
        val step = size.width / n
        val bar = (step * 0.6f).coerceAtLeast(1f)
        val mid = size.height / 2
        val minHalf = 1.dp.toPx()
        peaks.forEachIndexed { i, p ->
            // Square-root scale so quiet sounds are still visible next to loud ones.
            val half = (kotlin.math.sqrt(p) * (mid - 3.dp.toPx())).coerceAtLeast(minHalf)
            val x = i * step + (step - bar) / 2
            drawRoundRect(
                if (p < 0.002f) Color.White.copy(alpha = 0.15f) else colorOf(i),
                Offset(x, mid - half),
                Size(bar, half * 2),
                CornerRadius(bar / 2),
            )
        }
        if (playhead != null) {
            val x = size.width * playhead
            drawRect(Color.White, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
    }
}

/** Peak level of each of [buckets] equal slices of [samples]. */
private fun peaks(samples: FloatArray, buckets: Int): FloatArray {
    if (buckets <= 0 || samples.isEmpty()) return FloatArray(0)
    val per = samples.size.toFloat() / buckets
    return FloatArray(buckets) { b ->
        val from = (b * per).toInt()
        val to = ((b + 1) * per).toInt().coerceAtMost(samples.size)
        var peak = 0f
        for (i in from until to) peak = maxOf(peak, abs(samples[i]))
        peak.coerceAtMost(1f)
    }
}

/** Voice sent, noise blocked, and how much quieter the noise got. */
@Composable
internal fun TotalsRow(totals: GateTotals) {
    val cut = totals.noiseCutDb
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Voice sent", duration(totals.voiceMs), Palette.Go, R.drawable.ms_mic, Modifier.weight(1f))
        StatTile("Noise blocked", duration(totals.blockedMs), Palette.Amber, R.drawable.ms_air, Modifier.weight(1f))
        StatTile(
            "Noise cut",
            when {
                cut == null -> "–"
                cut >= 40f -> "Silenced"
                else -> "−${cut.roundToInt()} dB"
            },
            Palette.Cyan,
            R.drawable.ms_volume_off,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, icon: Int, modifier: Modifier) {
    Column(
        modifier
            .glass(RoundedCornerShape(16.dp), fillAlpha = 0.06f, rimAlpha = 0.18f)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Ico(icon, 18.dp, color)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary, maxLines = 1)
    }
}

private fun duration(ms: Long): String {
    val s = ms / 1000.0
    return if (s < 60) String.format(Locale.US, "%.1f s", s) else String.format(Locale.US, "%d:%02d", (s / 60).toInt(), (s % 60).toInt())
}

/** Off / Low / Medium / High chips for the wind filter, plus Custom once it's been fine-tuned. */
@Composable
internal fun GateChips(value: GateSettings?, onChange: (GateSettings?) -> Unit) {
    val custom = value != null && value.preset == null
    val options = buildList {
        add(Triple("Off", value == null) { onChange(null) })
        GateSettings.Preset.entries.forEach { p -> add(Triple(p.label, value == p.settings) { onChange(p.settings) }) }
        if (custom) add(Triple("Custom", true) {})
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, selected, onClick) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .glass(
                        RoundedCornerShape(14.dp),
                        tint = if (selected) Palette.Accent else Color.White,
                        fillAlpha = if (selected) 0.32f else 0.06f,
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else Palette.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
