package com.ridecomm.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.score.WrappedLogic
import com.ridecomm.app.score.Badge
import com.ridecomm.app.score.BadgeProgress
import com.ridecomm.app.score.Levels
import com.ridecomm.app.score.RideScoreState
import com.ridecomm.app.score.Score
import com.ridecomm.app.score.ScoreBook
import com.ridecomm.app.score.ScoreEntry
import com.ridecomm.app.score.ScoreRules
import java.text.NumberFormat
import java.util.Locale

private fun num(n: Int) = NumberFormat.getIntegerInstance(Locale.US).format(n)
private fun kmWhole(km: Double) = "${num(km.toInt())} km"

private val GoldGradient = Brush.linearGradient(listOf(Color(0xFFFFC93C), Color(0xFFFF8A1F)))

@DrawableRes
fun Badge.icon(): Int = when (this) {
    Badge.FIRST -> R.drawable.ms_flag
    Badge.RIDES_10, Badge.RIDES_50 -> R.drawable.ms_two_wheeler
    Badge.CENTURY -> R.drawable.ms_sports_score
    Badge.LONG_HAUL -> R.drawable.ms_route
    Badge.KM_1000, Badge.KM_5000, Badge.KM_10000 -> R.drawable.ms_emoji_events
    Badge.PACK -> R.drawable.ms_groups
    Badge.SPOTTER -> R.drawable.ms_warning
    Badge.LEADER -> R.drawable.ms_navigation
    Badge.SWEEP -> R.drawable.ms_shield
    Badge.HOME -> R.drawable.ms_home
    Badge.SMART -> R.drawable.ms_coffee
    Badge.EARLY -> R.drawable.ms_wb_twilight
    Badge.WEEKLY -> R.drawable.ms_local_fire_department
}

/** A round medal with the level's number, gold from Road Captain up. */
@Composable
private fun LevelMedal(index: Int, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (index >= 4) GoldGradient else Palette.Brand),
        contentAlignment = Alignment.Center,
    ) { Ico(R.drawable.ms_military_tech, size * 0.55f, Color.White) }
}

@Composable
internal fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, brush: Brush = Palette.Brand) {
    val track = Palette.TextTertiary.copy(alpha = 0.25f)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (fraction > 0f) drawRoundRect(brush, size = Size(size.width * fraction.coerceIn(0.04f, 1f), size.height), cornerRadius = r)
    }
}

/** "120 points to Explorer", or "Top level" for Legends. */
private fun toNext(total: Int): String {
    val level = Levels.of(total)
    return level.next?.let { "${num(it.minPoints - total)} points to ${it.name}" } ?: "Top level reached"
}

/** On the home screen: my level, points, this year in one line, and the latest badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PointsCard(entries: List<ScoreEntry>, nowMs: Long, onOpen: () -> Unit) {
    val total = ScoreBook.total(entries)
    val level = Levels.of(total)
    val year = ScoreBook.year(entries, ScoreBook.yearOf(nowMs))
    val streak = ScoreBook.weekStreak(entries, nowMs)
    val latest = ScoreBook.badges(entries).filter { it.earned }.sortedByDescending { it.earnedAtMs }.take(3)
    GlassCard(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpen), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Points & badges", Modifier.weight(1f))
            Text("See all", style = MaterialTheme.typography.labelLarge, color = Palette.Accent)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelMedal(level.index, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FitText(level.name, MaterialTheme.typography.titleLarge)
                FitText("${num(total)} points", MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            }
            if (streak >= 2) {
                Ico(R.drawable.ms_local_fire_department, 22.dp, Palette.Orange)
                Text("$streak wk", style = MaterialTheme.typography.titleMedium, color = Palette.Orange)
            }
        }
        ProgressBar(level.progress(total))
        Text(toNext(total), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        if (year.rides > 0) {
            Text(
                "This year: ${kmWhole(year.km)} · ${year.rides} ${if (year.rides == 1) "ride" else "rides"} · ${num(year.points)} points",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("Ride with RideComm to earn points. Never for speed: for distance, riding together and looking after the group.", style = MaterialTheme.typography.bodyMedium)
        }
        if (latest.isNotEmpty()) {
            // Chips keep their natural width and wrap to a second line on narrow phones.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                latest.forEach { BadgeChip(it.badge) }
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge, modifier: Modifier = Modifier) {
    Row(
        modifier.glass(RoundedCornerShape(14.dp), fillAlpha = 0.07f).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(badge.icon(), 18.dp, Palette.Amber)
        Spacer(Modifier.width(6.dp))
        Text(badge.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The full page: level, this year, badges, how points are earned. */
@Composable
fun PointsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Score.load(context) }
    val entries by Score.entries.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val year = WrappedLogic.yearFor(now)
    var wrapped by remember { mutableStateOf(false) }
    FullScreen(onClose) {
        PointsContent(
            entries, now, onClose, onReset = { Score.reset(context) },
            onWrapped = if (Prefs.wrapped(context) && entries.any { ScoreBook.yearOf(it.atMs) == year }) { { wrapped = true } } else null,
        )
    }
    if (wrapped) WrappedScreen(year) { wrapped = false }
}

/** The page's content for given entries (screenshots render it straight). */
@Composable
fun PointsContent(entries: List<ScoreEntry>, nowMs: Long, onClose: () -> Unit, onReset: () -> Unit, onWrapped: (() -> Unit)? = null) {
    val total = ScoreBook.total(entries)
    val level = Levels.of(total)
    val year = ScoreBook.year(entries, ScoreBook.yearOf(nowMs))
    val streak = ScoreBook.weekStreak(entries, nowMs)
    val badges = ScoreBook.badges(entries)
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTopBar("Points & badges", "Kept on this phone · never for speed", onClose)
        GlassCard(spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelMedal(level.index, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel("Level ${level.index + 1} of ${Levels.all.size}")
                    FitText(level.name, MaterialTheme.typography.headlineMedium)
                    FitText("${num(total)} points", MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                }
            }
            ProgressBar(level.progress(total))
            Text(toNext(total), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        }

        if (onWrapped != null) WrappedCard(WrappedLogic.yearFor(nowMs), WrappedLogic.season(nowMs), onWrapped)
        GlassCard(spacing = 12.dp) {
            SectionLabel("This year · ${year.year}")
            val hours = year.movingMin / 60
            val tiles = listOf(
                kmWhole(year.km) to "Distance",
                "${year.rides}" to if (year.rides == 1) "Ride" else "Rides",
                (if (hours > 0) "$hours h" else "${year.movingMin} min") to "Riding time",
                num(year.points) to "Points",
                kmWhole(year.longestKm) to "Longest ride",
                "$streak ${if (streak == 1) "week" else "weeks"}" to "Week streak",
            )
            // Three tiles a row, two on a small phone with large text.
            BoxWithConstraints {
                val perRow = if (maxWidth < 300.dp * LocalDensity.current.fontScale) 2 else 3
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.chunked(perRow).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (value, label) ->
                                Column(Modifier.weight(1f).glass(RoundedCornerShape(18.dp), fillAlpha = 0.07f).padding(12.dp)) {
                                    FitText(value, MaterialTheme.typography.titleLarge)
                                    FitText(label, MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
            MonthBars(year.monthKm, ScoreBook.yearOf(nowMs) == year.year)
            if (year.buddies.isNotEmpty()) {
                Text(
                    "Rode most with " + year.buddies.joinToString(", ") { (name, n) -> "$name ($n)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        GlassCard(spacing = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Badges", Modifier.weight(1f))
                Text("${badges.count { it.earned }} of ${badges.size}", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
            }
            badges.sortedByDescending { it.earned }.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { BadgeTile(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        GlassCard(spacing = 8.dp) {
            SectionLabel("How to earn points")
            ScoreRules.howTo.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "Levels: " + Levels.all.joinToString(" · ") { "${it.name} ${num(it.minPoints)}" },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
            )
        }

        if (entries.isNotEmpty()) {
            GlassButton(if (confirmReset) "Tap again to reset all points" else "Reset points", R.drawable.ms_close, Modifier.fillMaxWidth(), height = 48.dp) {
                if (confirmReset) {
                    onReset()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(p: BadgeProgress, modifier: Modifier = Modifier) {
    Column(
        modifier
            .glass(RoundedCornerShape(18.dp), tint = if (p.earned) Palette.AmberBright else Color.White, fillAlpha = if (p.earned) 0.14f else 0.05f)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (p.earned) GoldGradient else Brush.linearGradient(listOf(Palette.TextTertiary.copy(alpha = 0.3f), Palette.TextTertiary.copy(alpha = 0.3f)))),
                contentAlignment = Alignment.Center,
            ) { Ico(if (p.earned) p.badge.icon() else R.drawable.ms_lock, 18.dp, if (p.earned) Color.White else Palette.TextSecondary) }
            Spacer(Modifier.width(8.dp))
            FitText(p.badge.title, MaterialTheme.typography.titleMedium, min = 9.sp)
        }
        Text(p.badge.how, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!p.earned && p.badge.goal > 1) {
            ProgressBar(p.fraction)
            Text("${num(p.value.coerceAtMost(p.badge.goal))} / ${num(p.badge.goal)} ${p.badge.unit}".trim(), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
    }
}

/** km per month as small bars, J F M … underneath. */
@Composable
private fun MonthBars(monthKm: List<Double>, thisYear: Boolean) {
    val max = monthKm.maxOrNull()?.takeIf { it > 0 } ?: return
    val current = if (thisYear) java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) else -1
    val bar = Palette.Accent
    val dim = Palette.TextTertiary.copy(alpha = 0.3f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val slot = size.width / 12
            val w = slot * 0.6f
            monthKm.forEachIndexed { i, km ->
                val h = (km / max).toFloat() * size.height
                val left = slot * i + (slot - w) / 2
                if (km > 0) {
                    drawRoundRect(if (i == current) bar else bar.copy(alpha = 0.55f), Offset(left, size.height - h.coerceAtLeast(4f)), Size(w, h.coerceAtLeast(4f)), CornerRadius(w / 3))
                } else {
                    drawRoundRect(dim, Offset(left, size.height - 3f), Size(w, 3f), CornerRadius(1.5f))
                }
            }
        }
        Row {
            "JFMAMJJASOND".forEach { c ->
                Text(c.toString(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

/** In the ride: this ride's points for me and every rider who shares theirs. */
@Composable
fun RidePointsCard(state: RideScoreState) {
    if (!state.active) return
    GlassCard(spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_emoji_events, 22.dp, Palette.Amber)
            Spacer(Modifier.width(8.dp))
            SectionLabel("Ride points", Modifier.weight(1f))
        }
        state.board.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = if (i == 0 && r.ride > 0) Palette.Amber else Palette.TextTertiary, modifier = Modifier.width(24.dp))
                LevelMedal(r.level, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (r.isMe) "You" else r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Levels.name(r.level), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
                }
                Text("+${num(r.ride)}", style = MaterialTheme.typography.titleMedium, color = if (r.isMe) Palette.Accent else Palette.TextPrimary)
            }
        }
        if (state.board.size == 1) {
            Text("Riders with Points & badges on show up here.", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
        }
    }
}

/** On a ride's summary: how it scored, and the badges it earned. */
@Composable
fun RideScoreCard(entry: ScoreEntry, earned: List<Badge>) {
    val lines = ScoreRules.breakdown(entry)
    GlassCard(spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_emoji_events, 22.dp, Palette.Amber)
            Spacer(Modifier.width(8.dp))
            SectionLabel("Points", Modifier.weight(1f))
            Text("+${num(lines.sumOf { it.points })}", style = MaterialTheme.typography.titleLarge, color = Palette.Accent)
        }
        if (lines.isEmpty()) Text("A short ride: no points this time.", style = MaterialTheme.typography.bodyMedium)
        lines.forEach { l ->
            Row {
                Text(l.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("+${num(l.points)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            }
        }
        earned.forEach { b ->
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), tint = Palette.AmberBright, fillAlpha = 0.14f).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(GoldGradient), contentAlignment = Alignment.Center) { Ico(b.icon(), 18.dp, Color.White) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("New badge: ${b.title}", style = MaterialTheme.typography.titleMedium)
                    Text(b.how, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                }
            }
        }
    }
}
