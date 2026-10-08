package com.ridecomm.app.ui

import com.ridecomm.app.ui.map.GroupMapCard
import com.ridecomm.app.ui.map.GroupMapScreen
import com.ridecomm.app.ui.map.roleLabel
import com.ridecomm.app.group.RideRoles
import com.ridecomm.app.group.RideRolesState
import com.ridecomm.app.hazard.Hazards
import com.ridecomm.app.hazard.HazardsState
import com.ridecomm.app.ride.RiderVolume
import com.ridecomm.app.ride.RiderVolumes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt
import java.util.Locale
import com.ridecomm.app.trip.TripTracker
import com.ridecomm.app.trip.TripState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.audio.GateStatus
import com.ridecomm.app.alerts.BatteryInfo
import com.ridecomm.app.alerts.BatteryWatch
import com.ridecomm.app.alerts.RiderAlerts
import com.ridecomm.app.ride.DataSaver
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.ridecomm.app.Prefs
import com.ridecomm.app.whisper.Whisper
import com.ridecomm.app.whisper.WhisperState
import com.ridecomm.app.group.DestinationState
import com.ridecomm.app.group.RideDestination
import com.ridecomm.app.home.HomeSafe
import com.ridecomm.app.home.HomeSafeState
import com.ridecomm.app.trip.BreakDue
import com.ridecomm.app.trip.BreakReminder

private val OthersGradient = Brush.linearGradient(listOf(Palette.VioletBright, Palette.CyanBright))

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
    val trip by TripTracker.state.collectAsStateWithLifecycle()
    val onCall by RiderAlerts.onCall.collectAsStateWithLifecycle()
    val roles by RideRoles.state.collectAsStateWithLifecycle()
    val hazards by Hazards.state.collectAsStateWithLifecycle()
    val volumes by RiderVolumes.volumes.collectAsStateWithLifecycle()
    val whisper by Whisper.state.collectAsStateWithLifecycle()
    val destination by RideDestination.state.collectAsStateWithLifecycle()
    val homeSafe by HomeSafe.state.collectAsStateWithLifecycle()
    val breakDue by BreakReminder.due.collectAsStateWithLifecycle()
    val filterStatus by remember { MicGate.live.map { it?.status }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val dataUsed by produceState(DataUsage.usedBytes()) {
        while (true) {
            delay(DATA_REFRESH_MS)
            value = DataUsage.usedBytes()
        }
    }
    RideContent(state, music, vote, sos, group, sent, photos, myPhoto, dataUsed, batteries, filterStatus, trip, onCall, roles, hazards, volumes, whisper, destination, homeSafe, breakDue)
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
    trip: TripState? = null,
    onCall: Set<String> = emptySet(),
    roles: RideRolesState = RideRolesState(),
    hazards: HazardsState = HazardsState(),
    volumes: Map<String, RiderVolume> = emptyMap(),
    whisper: WhisperState = WhisperState(),
    destination: DestinationState = DestinationState(),
    homeSafe: HomeSafeState = HomeSafeState(),
    breakDue: BreakDue? = null,
) {
    val context = LocalContext.current
    // Read on every recomposition, so switching it in Settings mid-ride applies at once.
    val talkToOne = Prefs.talkToOne(context)
    var pickHazard by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    val photoOf = { rider: Rider -> if (rider.isMe) myPhoto else photos[rider.id] }
    var confirmLeave by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var pickDestination by remember { mutableStateOf(false) }

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
            .putExtra(Intent.EXTRA_TEXT, InviteLink.shareText(context, state.code))
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
                trip?.let { TripRow(it) }
                SosCards(sos)
                breakDue?.let { BreakCard(it, onAsk = BreakReminder::askGroup, onNotNow = BreakReminder::notNow) }
                if (destination.enabled) DestinationCard(destination, onPick = { pickDestination = true }, onClear = RideDestination::clear)
                if (group.enabled) GroupMapCard(group, state.riders.size) { showMap = true }
                if (hazards.enabled) {
                    HazardCard(hazards.hazards, System.currentTimeMillis(), onMark = { pickHazard = true }, onRemove = Hazards::remove)
                }
                RidersCard(
                    state.riders, group, batteries, onCall, roles, volumes, photoOf,
                    whisper = whisper,
                    onHold = if (talkToOne) { rider -> Whisper.start(rider.id, rider.name) } else null,
                ) { sheetFor = it.id }
                VoteCard(vote)
                MusicCard(music)
                if (homeSafe.enabled) HomeSafeCard(homeSafe, state.riders, onImHome = HomeSafe::imHome, onLeave = { confirmLeave = true })
                OverlayPermissionCard()
            }

            // Private talk sits just above the dock: never over the SOS button, and "Hold to reply" is near the thumb.
            AnimatedVisibility(
                visible = whisper.talkingTo != null || whisper.fromMe != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                var shown by remember { mutableStateOf(whisper) }
                if (whisper.talkingTo != null || whisper.fromMe != null) shown = whisper
                WhisperBanner(
                    shown,
                    canReply = talkToOne,
                    onReplyStart = { shown.fromMe?.let { Whisper.start(it.fromId, it.fromName) } },
                    onReplyStop = Whisper::stop,
                )
            }

            Dock(
                pushToTalk = state.pushToTalk,
                talking = state.talking,
                latched = state.talkLatched,
                muted = state.micMuted,
                speaking = state.riders.firstOrNull { it.isMe }?.isSpeaking == true,
                filterStatus = filterStatus,
                onLeave = { confirmLeave = true },
                onShare = shareCode,
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = sent != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                // Keep showing the last message while the banner slides out.
                var shown by remember { mutableStateOf(sent) }
                if (sent != null) shown = sent
                shown?.let { SentBanner(it) }
            }
        }

        // Drawn last so it covers the whole ride screen.
        if (showMap && group.enabled) {
            val myId = state.riders.firstOrNull { it.isMe }?.id
            val allPhotos = if (myId != null && myPhoto != null) photos + (myId to myPhoto) else photos
            GroupMapScreen(
                group, state.riders, allPhotos, onClose = { showMap = false }, hazards = hazards.hazards, roles = roles,
                destination = destination.destination,
                onSetDestination = if (destination.enabled) RideDestination::set else null,
            )
        }

        sos.countdown?.let { SosCountdown(it, sos.countdownFromCrash) }
    }

    if (showSettings) SettingsDialog(onClose = { showSettings = false }, inRide = true)

    if (pickDestination) {
        DestinationDialog(
            onCancel = { pickDestination = false },
            onPick = {
                RideDestination.set(it.lat, it.lon, it.name)
                pickDestination = false
            },
        )
    }

    if (pickHazard) {
        HazardPicker(onPick = { Hazards.mark(it); pickHazard = false }, onCancel = { pickHazard = false })
    }

    sheetFor?.let { id ->
        val rider = state.riders.firstOrNull { it.id == id }
        if (rider == null) {
            sheetFor = null
        } else {
            RiderSheet(
                rider = rider,
                photo = photoOf(rider),
                volume = volumes[id] ?: RiderVolume(),
                roles = roles,
                onVolume = { RiderVolumes.set(context, id, it, RideManager.currentRoom()) },
                onLead = { RideRoles.setLead(if (it) id else null, rider.name) },
                onSweep = { RideRoles.setSweep(if (it) id else null, rider.name) },
                onClose = { sheetFor = null },
                whispering = whisper.talkingTo == id,
                onWhisperStart = if (talkToOne && !rider.isMe) { { Whisper.start(id, rider.name) } } else null,
                onWhisperStop = Whisper::stop,
            )
        }
    }

    if (confirmLeave) {
        GlassDialog(onDismiss = { confirmLeave = false }) {
            Text("Leave the ride?", style = MaterialTheme.typography.titleLarge)
            Text("You'll stop hearing the group. You can rejoin with the same code.", style = MaterialTheme.typography.bodyMedium)
            if (homeSafe.enabled && !homeSafe.meHome) {
                PrimaryButton("I'm home safe · leave", R.drawable.ms_home, Modifier.fillMaxWidth(), brush = Palette.GoGradient, contentColor = Color(0xFF052E1F), height = 56.dp) {
                    confirmLeave = false
                    HomeSafe.imHome()
                    RideManager.leave()
                }
            }
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
            val saver by DataSaver.active.collectAsStateWithLifecycle()
            StatusPill(
                "Connected · $count ${if (count == 1) "rider" else "riders"}$data" + if (saver) " · Data saver" else "",
                if (saver) Palette.Amber else Palette.Go,
            )
        }
    }
}

private const val DATA_REFRESH_MS = 5_000L

/** Soft look: the mic button while silent. */
private val SoftMicOff = Brush.linearGradient(listOf(Color(0xFFA7ABC4), Color(0xFF7C819E)))

@Composable
private fun RidersCard(
    riders: List<Rider>,
    group: GroupState,
    batteries: Map<String, BatteryInfo>,
    onCall: Set<String>,
    roles: RideRolesState,
    volumes: Map<String, RiderVolume>,
    photoOf: (Rider) -> Bitmap?,
    whisper: WhisperState = WhisperState(),
    onHold: ((Rider) -> Unit)? = null,
    onOpen: (Rider) -> Unit,
) {
    GlassCard(spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Riders", Modifier.weight(1f))
            if (group.enabled && group.sharing) StatusPill("On the map", Palette.Cyan)
        }
        riders.forEach {
            RiderRow(
                it,
                if (it.isMe || !group.enabled) null else group.positions[it.id],
                photoOf(it),
                batteries[it.id],
                it.id in onCall,
                roleLabel(roles, it.id),
                volumes[it.id],
                privateTo = if (it.isMe) whisper.talkingTo?.let { _ -> whisper.talkingToName } else whisper.others[it.id],
                onHold = onHold?.takeIf { _ -> !it.isMe }?.let { hold -> { hold(it) } },
            ) { onOpen(it) }
        }
        Text(
            if (onHold != null) "Tap a rider for volume and lead / sweep · hold to talk only to them" else "Tap a rider for volume and lead / sweep",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextTertiary,
        )
    }
}

@Composable
private fun RiderRow(
    rider: Rider,
    position: RiderPosition?,
    photo: Bitmap?,
    battery: BatteryInfo?,
    onPhoneCall: Boolean,
    role: String?,
    volume: RiderVolume?,
    privateTo: String? = null,
    onHold: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val ring by animateColorAsState(
        when {
            privateTo != null -> WhisperColor
            rider.isSpeaking -> Palette.Go
            else -> Color.Transparent
        },
        label = "ring",
    )
    // Tap: the rider's sheet. Hold: talk only to them until you let go. The gesture must survive
    // recompositions (riders' talking state changes all the time), so it reads the latest callbacks.
    val tap by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    val gestures = if (onHold == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { tap() },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold?.invoke()
                },
                onPress = {
                    tryAwaitRelease()
                    Whisper.stop()
                },
            )
        }
    }
    Row(Modifier.clip(RoundedCornerShape(18.dp)).then(gestures), verticalAlignment = Alignment.CenterVertically) {
        Avatar(rider.name, 54.dp, if (rider.isMe) Palette.Brand else OthersGradient, ring, photo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rider.isMe) "${rider.name} (you)" else rider.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (role != null) {
                    Spacer(Modifier.width(8.dp))
                    RoleTag(role)
                }
            }
            val heard = when {
                volume == null -> ""
                volume.mutedForMe -> " · muted for you"
                volume.volume != 1f -> " · ${(volume.volume * 100).roundToInt()}%"
                else -> ""
            }
            Text(
                when {
                    privateTo != null -> "Talking only to $privateTo"
                    onPhoneCall -> "On a phone call"
                    rider.isMuted -> "Mic off"
                    rider.isSpeaking -> "Talking"
                    else -> "Listening"
                } + heard,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    privateTo != null -> WhisperColor
                    onPhoneCall -> Palette.Amber
                    rider.isSpeaking -> Palette.Go
                    else -> Palette.TextSecondary
                },
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

/** Speed now, distance and riding time from my own GPS. */
@Composable
private fun TripRow(trip: TripState) {
    // Riding time ticks every second, even when GPS is quiet.
    val now by produceState(System.currentTimeMillis(), trip.startedAtMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedMin = ((now - trip.startedAtMs).coerceAtLeast(0) / 60_000)
    val time = String.format(Locale.US, "%d:%02d", elapsedMin / 60, elapsedMin % 60)
    if (!trip.gps) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_location_on, 16.dp, Palette.TextTertiary)
            Spacer(Modifier.width(6.dp))
            Text(
                "Riding $time h · allow location to see speed and distance",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextTertiary,
            )
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TripTile(trip.speedKmh?.let { "${it.roundToInt()}" } ?: "–", "km/h", "Speed", Modifier.weight(1f), R.drawable.ms_speed, NeuTokens.TileBlue, NeuTokens.IconBlue)
        TripTile(String.format(Locale.US, "%.1f", trip.distanceM / 1000), "km", "Distance", Modifier.weight(1f), R.drawable.ms_route, NeuTokens.TileViolet, NeuTokens.IconViolet)
        TripTile(time, "h", "Riding", Modifier.weight(1f), R.drawable.ms_schedule, NeuTokens.TilePeach, NeuTokens.IconOrange)
    }
}

@Composable
private fun TripTile(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier,
    icon: Int = R.drawable.ms_speed,
    softTile: Color = NeuTokens.TileBlue,
    softIcon: Color = NeuTokens.IconBlue,
) {
    if (LocalLook.current == UiLook.NEU) {
        // Soft: a raised tile with a hint of colour, the icon in a small raised circle.
        Column(
            modifier
                .neuRaised(RoundedCornerShape(24.dp), 6.dp, Brush.linearGradient(listOf(Color.White, softTile)))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Box(Modifier.size(38.dp).neuRaised(CircleShape, 3.dp, Brush.linearGradient(listOf(Color.White, softTile))), contentAlignment = Alignment.Center) {
                Ico(icon, 20.dp, softIcon)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, letterSpacing = 0.sp), maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (LocalLook.current == UiLook.GLASS) {
        // Glass: icon on top, a big number, the label underneath.
        Column(
            modifier
                .frost(RoundedCornerShape(23.dp), glow = false)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Ico(icon, 20.dp, GlassTokens.StatIcon)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, letterSpacing = 0.sp), maxLines = 1)
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = GlassTokens.Muted))
        }
        return
    }
    Column(
        modifier
            .glass(RoundedCornerShape(18.dp), fillAlpha = 0.06f, rimAlpha = 0.18f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
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
private fun Dock(
    pushToTalk: Boolean,
    talking: Boolean,
    latched: Boolean,
    muted: Boolean,
    speaking: Boolean,
    filterStatus: GateStatus?,
    onLeave: () -> Unit,
    onShare: () -> Unit,
) {
    val look = LocalLook.current
    val glassLook = look == UiLook.GLASS
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                when (look) {
                    UiLook.GLASS -> Modifier.frost(RoundedCornerShape(30.dp))
                    UiLook.NEU -> Modifier.neuRaised(RoundedCornerShape(30.dp), 8.dp)
                    UiLook.CLASSIC -> Modifier.glass(RoundedCornerShape(36.dp), fillAlpha = 0.10f)
                },
            )
            .padding(horizontal = 18.dp, vertical = if (look != UiLook.CLASSIC) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (look == UiLook.NEU) {
            SoftDockSide(R.drawable.ms_logout, "Leave", NeuTokens.Stop, NeuTokens.LeaveTint, onLeave)
        } else {
            DockSide(R.drawable.ms_logout, "Leave", Palette.Stop, onLeave, if (glassLook) GlassTokens.Leave else null)
        }
        if (pushToTalk) TalkButton(talking, latched, muted) else MicButton(muted, speaking, filterStatus)
        if (look == UiLook.NEU) {
            SoftDockSide(R.drawable.ms_share, "Share", NeuTokens.InkMuted, NeuTokens.ShareTint, onShare)
        } else {
            DockSide(R.drawable.ms_share, "Share", Color.White, onShare, if (glassLook) GlassTokens.Share else null)
        }
    }
}

/** Soft look: a raised round button with a hint of colour and a coloured icon (pink Leave, lilac Share). */
@Composable
private fun SoftDockSide(icon: Int, label: String, iconColor: Color, fill: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .neuRaised(CircleShape, 6.dp, Brush.linearGradient(listOf(Color.White, fill)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Ico(icon, 28.dp, iconColor, label) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = Palette.TextSecondary)
    }
}

@Composable
private fun DockSide(icon: Int, label: String, tint: Color, onClick: () -> Unit, glassBrush: Brush? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (glassBrush != null) {
            GlassIconButton(icon, label, size = 64.dp, iconSize = 28.dp, brush = glassBrush, onClick = onClick)
        } else {
            GlassIconButton(icon, label, size = 56.dp, tint = tint, onClick = onClick)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun MicButton(muted: Boolean, speaking: Boolean, filterStatus: GateStatus?) {
    val pulse by animateFloatAsState(if (speaking && !muted) 1.06f else 1f, label = "pulse")
    val glassLook = LocalLook.current == UiLook.GLASS
    val soft = LocalLook.current == UiLook.NEU
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(pulse)
                .size(if (glassLook || soft) 100.dp else 96.dp)
                .then(
                    if (soft) {
                        // Soft: a raised ring around a blue button (grey when the mic is off).
                        Modifier
                            .neuRaised(CircleShape, 7.dp)
                            .padding(8.dp)
                            .then(if (muted) Modifier else Modifier.glow(NeuTokens.BlueGlow, 22.dp, CircleShape, offsetY = 4.dp))
                    } else if (glassLook) {
                        // Glass: teal with a soft glowing ring; grey when the mic is off.
                        Modifier
                            .then(if (muted) Modifier else Modifier.glow(GlassTokens.MicGlow, 30.dp, CircleShape))
                            .border(7.dp, if (muted) Color.White.copy(alpha = 0.13f) else Color(0x444DFDE9), CircleShape)
                            .padding(7.dp)
                            .border(5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    } else {
                        Modifier
                            .border(3.dp, Color.White.copy(alpha = if (speaking && !muted) 0.7f else 0.2f), CircleShape)
                            .padding(6.dp)
                    },
                )
                .clip(CircleShape)
                .background(
                    when {
                        soft && muted -> SoftMicOff
                        soft -> NeuTokens.BlueAction
                        glassLook && muted -> GlassTokens.MicOff
                        glassLook -> GlassTokens.MicOn
                        muted -> Palette.StopGradient
                        else -> Palette.GoGradient
                    },
                )
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
            filterStatus == GateStatus.VOICE -> "Mic on · sending" to if (soft) NeuTokens.Blue else if (glassLook) GlassTokens.MicLabel else Palette.Go
            else -> "Mic on" to if (soft) NeuTokens.Blue else if (glassLook) GlassTokens.MicLabel else Palette.Go
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Push to talk: hold to talk, or tap to talk hands-free and tap again to stop. Grey while
 * silent, green while my voice goes out.
 */
@Composable
private fun TalkButton(talking: Boolean, latched: Boolean, muted: Boolean) {
    val haptics = LocalHapticFeedback.current
    val grow by animateFloatAsState(if (talking) 1.08f else 1f, label = "grow")
    val glassLook = LocalLook.current == UiLook.GLASS
    val soft = LocalLook.current == UiLook.NEU
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .scale(grow)
                .size(if (glassLook || soft) 100.dp else 96.dp)
                .then(
                    if (soft) {
                        Modifier
                            .neuRaised(CircleShape, 7.dp)
                            .padding(8.dp)
                            .then(if (talking) Modifier.glow(NeuTokens.BlueGlow, 22.dp, CircleShape, offsetY = 4.dp) else Modifier)
                    } else if (glassLook) {
                        Modifier
                            .then(if (talking) Modifier.glow(GlassTokens.MicGlow, 30.dp, CircleShape) else Modifier)
                            .border(7.dp, if (talking) Color(0x444DFDE9) else Color.White.copy(alpha = 0.13f), CircleShape)
                            .padding(7.dp)
                            .border(5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    } else {
                        Modifier
                            .border(3.dp, Color.White.copy(alpha = if (talking) 0.8f else 0.25f), CircleShape)
                            .padding(6.dp)
                    },
                )
                .clip(CircleShape)
                .background(
                    when {
                        soft && talking -> NeuTokens.BlueAction
                        soft -> SoftMicOff
                        glassLook && talking -> GlassTokens.MicOn
                        glassLook -> GlassTokens.MicOff
                        talking -> Palette.GoGradient
                        else -> Brush.linearGradient(listOf(Color(0xFF3A3F5C), Color(0xFF23263B)))
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            RideManager.talkPress()
                            tryAwaitRelease()
                            RideManager.talkRelease()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Ico(if (talking) R.drawable.ms_mic else R.drawable.ms_touch_app, 40.dp, Color.White, "Push to talk")
        }
        Spacer(Modifier.height(6.dp))
        val (label, color) = when {
            talking && latched -> "Talking · tap to stop" to Palette.Go
            talking -> "Talking · let go to stop" to Palette.Go
            muted -> "Hold to talk · mic off" to Palette.Stop
            else -> "Hold to talk · tap to lock" to Palette.TextSecondary
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
