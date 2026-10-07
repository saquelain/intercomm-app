package com.ridecomm.app.ui

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.audio.GateStatus
import com.ridecomm.app.alerts.BatteryInfo
import com.ridecomm.app.alerts.BatteryWatch
import com.ridecomm.app.alerts.RiderAlerts
import com.ridecomm.app.ride.DataUsage
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupMath
import com.ridecomm.app.group.GroupState
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.group.Relation
import com.ridecomm.app.group.RiderPosition
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.music.MusicState
import com.ridecomm.app.profile.Profile
import com.ridecomm.app.profile.ProfileSync
import com.ridecomm.app.ride.InviteLink
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideState
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ride.Signal
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.sos.SosState
import com.ridecomm.app.vote.VoteManager
import com.ridecomm.app.vote.VoteState

private val OthersGradient = Brush.linearGradient(listOf(Palette.Violet, Palette.Cyan))

@Composable
fun RideScreen(state: RideState) {
    val music by MusicManager.state.collectAsStateWithLifecycle()
    val vote by VoteManager.state.collectAsStateWithLifecycle()
    val sos by SosManager.state.collectAsStateWithLifecycle()
    val group by GroupTracker.state.collectAsStateWithLifecycle()
    val sent by VoteManager.sent.collectAsStateWithLifecycle()
    val photos by ProfileSync.photos.collectAsStateWithLifecycle()
    val myPhoto by Profile.photo.collectAsStateWithLifecycle()
    val batteries by RiderAlerts.batteries.collectAsStateWithLifecycle()
    val filterStatus by remember { MicGate.live.map { it?.status }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val dataUsed by produceState(DataUsage.usedBytes()) {
        while (true) {
            delay(DATA_REFRESH_MS)
            value = DataUsage.usedBytes()
        }
    }
    RideContent(state, music, vote, sos, group, sent, photos, myPhoto, dataUsed, batteries, filterStatus)
}

/** The ride screen for given states (split out so screenshots can render any situation). */
@Composable
fun RideContent(
    state: RideState,
    music: MusicState,
    vote: VoteState,
    sos: SosState,
    group: GroupState = GroupState(),
    sent: VoteManager.Sent? = null,
    photos: Map<String, Bitmap> = emptyMap(),
    myPhoto: Bitmap? = null,
    dataUsed: Long? = null,
    batteries: Map<String, BatteryInfo> = emptyMap(),
    filterStatus: GateStatus? = null,
) {
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // An invite tapped while already riding: same ride → nothing to do; another ride → say how.
    val invite by InviteLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(invite) {
        val code = invite ?: return@LaunchedEffect
        InviteLink.consume()
        if (code != state.code) {
            Toast.makeText(context, "You're already in ride ${state.code}. Leave it to join $code.", Toast.LENGTH_LONG).show()
        }
    }

    val shareCode = {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, InviteLink.shareText(state.code))
        context.startActivity(Intent.createChooser(share, "Share ride code"))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Ride code")
                        // One line even on narrow phones: shrink rather than wrap the code.
                        Text(
                            state.code,
                            style = MaterialTheme.typography.displayMedium,
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 40.sp),
                        )
                    }
                    GlassIconButton(R.drawable.ms_settings, "Settings", size = 52.dp, iconSize = 24.dp) { showSettings = true }
                    Spacer(Modifier.width(10.dp))
                    SosButton(sos)
                }

                StatusLine(state, dataUsed)
                SosCards(sos)
                RidersCard(state.riders, group, batteries) { rider -> if (rider.isMe) myPhoto else photos[rider.id] }
                VoteCard(vote)
                MusicCard(music)
                OverlayPermissionCard()
            }

            Dock(
                muted = state.micMuted,
                speaking = state.riders.firstOrNull { it.isMe }?.isSpeaking == true,
                filterStatus = filterStatus,
                onLeave = { confirmLeave = true },
                onShare = shareCode,
            )
        }

        AnimatedVisibility(
            visible = sent != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Keep showing the last message while the banner slides out.
            var shown by remember { mutableStateOf(sent) }
            if (sent != null) shown = sent
            shown?.let { SentBanner(it) }
        }

        // Drawn last so it covers the whole ride screen.
        sos.countdown?.let { SosCountdown(it, sos.countdownFromCrash) }
    }

    if (showSettings) SettingsDialog(onClose = { showSettings = false }, inRide = true)

    if (confirmLeave) {
        GlassDialog(onDismiss = { confirmLeave = false }) {
            Text("Leave the ride?", style = MaterialTheme.typography.titleLarge)
            Text("You'll stop hearing the group. You can rejoin with the same code.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton("Stay", modifier = Modifier.weight(1f)) { confirmLeave = false }
                PrimaryButton("Leave", R.drawable.ms_logout, Modifier.weight(1f), brush = Palette.StopGradient, height = 56.dp) {
                    confirmLeave = false
                    RideManager.leave()
                }
            }
        }
    }
}

@Composable
private fun StatusLine(state: RideState, dataUsed: Long?) {
    when (state.status) {
        RideStatus.CONNECTING -> StatusPill("Joining…", Palette.Amber)
        RideStatus.RECONNECTING -> StatusPill("Weak network, reconnecting…", Palette.Amber)
        else -> {
            val count = state.riders.size
            val data = dataUsed?.let { " · ${DataUsage.format(it)}" }.orEmpty()
            StatusPill("Connected · $count ${if (count == 1) "rider" else "riders"}$data", Palette.Go)
        }
    }
}

private const val DATA_REFRESH_MS = 5_000L

@Composable
private fun RidersCard(riders: List<Rider>, group: GroupState, batteries: Map<String, BatteryInfo>, photoOf: (Rider) -> Bitmap?) {
    GlassCard(spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Riders", Modifier.weight(1f))
            if (group.sharing) StatusPill("Location (beta)", Palette.Cyan)
        }
        riders.forEach { RiderRow(it, if (it.isMe) null else group.positions[it.id], photoOf(it), batteries[it.id]) }
    }
}

@Composable
private fun RiderRow(rider: Rider, position: RiderPosition?, photo: Bitmap?, battery: BatteryInfo?) {
    val context = LocalContext.current
    val ring by animateColorAsState(if (rider.isSpeaking) Palette.Go else Color.Transparent, label = "ring")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(rider.name, 54.dp, if (rider.isMe) Palette.Brand else OthersGradient, ring, photo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (rider.isMe) "${rider.name} (you)" else rider.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    rider.isMuted -> "Mic off"
                    rider.isSpeaking -> "Talking"
                    else -> "Listening"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (rider.isSpeaking) Palette.Go else Palette.TextSecondary,
            )
        }
        if (rider.isMuted) {
            Ico(R.drawable.ms_mic_off, 20.dp, Palette.Stop, "Mic off")
            Spacer(Modifier.width(10.dp))
        }
        if (battery != null && !battery.charging && battery.level <= BatteryWatch.SHOW_AT_OR_BELOW) {
            LowBattery(battery.level)
            Spacer(Modifier.width(10.dp))
        }
        SignalIcon(rider.signal)
        if (position != null) {
            Spacer(Modifier.width(10.dp))
            DistanceChip(position) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GroupTracker.mapsLink(position))))
            }
        }
    }
}

/** "1.2 km · behind" with a map pin; tap to open the rider's position in Maps. */
@Composable
private fun DistanceChip(position: RiderPosition, onClick: () -> Unit) {
    val distance = position.distanceM
    val far = distance != null && distance >= 1_000
    Column(
        Modifier
            .glass(RoundedCornerShape(16.dp), tint = if (far) Palette.Amber else Color.White, fillAlpha = if (far) 0.18f else 0.08f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_location_on, 14.dp, if (far) Palette.Amber else Palette.Cyan)
            Spacer(Modifier.width(3.dp))
            Text(
                distance?.let { GroupMath.shortDistance(it) } ?: "Map",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextPrimary,
            )
        }
        val label = when (position.relation) {
            Relation.AHEAD -> "ahead"
            Relation.BEHIND -> "behind"
            Relation.NEARBY -> if (distance != null && distance < GroupMath.TOGETHER_M) "with you" else null
        }
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

/** Battery icon with "18%", amber when low and red when nearly empty. */
@Composable
private fun LowBattery(level: Int) {
    val color = if (level <= 10) Palette.Stop else Palette.Amber
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ico(R.drawable.ms_battery_alert, 20.dp, color, "Battery low")
        Text("$level%", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun SignalIcon(signal: Signal) {
    val (icon, color) = when (signal) {
        Signal.GOOD -> R.drawable.ms_signal_cellular_alt to Palette.Go
        Signal.WEAK -> R.drawable.ms_signal_cellular_alt_1_bar to Palette.Amber
        Signal.LOST -> R.drawable.ms_signal_cellular_off to Palette.Stop
        Signal.UNKNOWN -> return
    }
    Ico(icon, 22.dp, color, "Signal")
}

/** Bottom bar: Leave, the big mic button, Share. */
@Composable
private fun Dock(muted: Boolean, speaking: Boolean, filterStatus: GateStatus?, onLeave: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .glass(RoundedCornerShape(36.dp), fillAlpha = 0.10f)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DockSide(R.drawable.ms_logout, "Leave", Palette.Stop, onLeave)
        MicButton(muted, speaking, filterStatus)
        DockSide(R.drawable.ms_share, "Share", Color.White, onShare)
    }
}

@Composable
private fun DockSide(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(icon, label, size = 56.dp, tint = tint, onClick = onClick)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun MicButton(muted: Boolean, speaking: Boolean, filterStatus: GateStatus?) {
    val pulse by animateFloatAsState(if (speaking && !muted) 1.06f else 1f, label = "pulse")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(pulse)
                .size(96.dp)
                .border(3.dp, Color.White.copy(alpha = if (speaking && !muted) 0.7f else 0.2f), CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(if (muted) Palette.StopGradient else Palette.GoGradient)
                .clickable(onClick = RideManager::toggleMute),
            contentAlignment = Alignment.Center,
        ) {
            Ico(if (muted) R.drawable.ms_mic_off else R.drawable.ms_mic, 40.dp, Color.White, if (muted) "Unmute" else "Mute")
        }
        Spacer(Modifier.height(6.dp))
        // The wind filter at work: shows when it's holding back wind instead of sending it.
        val (label, color) = when {
            muted -> "Mic off · tap to talk" to Palette.Stop
            filterStatus == GateStatus.NOISE -> "Mic on · wind blocked" to Palette.Amber
            filterStatus == GateStatus.VOICE -> "Mic on · sending" to Palette.Go
            else -> "Mic on" to Palette.Go
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
