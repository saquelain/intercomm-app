package com.ridecomm.app.ui

import kotlin.math.roundToInt
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import com.ridecomm.app.trip.UpdateEvery
import com.ridecomm.app.trip.BreakEvery
import com.ridecomm.app.trip.BreakReminder
import com.ridecomm.app.group.RideDestination
import com.ridecomm.app.home.HomeSafe
import com.ridecomm.app.home.HomeLogic
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.sos.LockScreenInfo
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.ridecomm.app.trip.TripTracker
import android.Manifest
import android.graphics.Bitmap
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.ridecomm.app.CrashLog
import com.ridecomm.app.Prefs
import com.ridecomm.app.hazard.Hazards
import com.ridecomm.app.night.NightMode
import com.ridecomm.app.night.NightModeSetting
import com.ridecomm.app.ride.TalkMode
import com.ridecomm.app.sos.EmergencyInfo
import com.ridecomm.app.R
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.audio.GateSettings
import com.ridecomm.app.crash.CrashDetector
import com.ridecomm.app.profile.Profile
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.headset.HeadsetButtons
import com.ridecomm.app.ride.InviteLink
import com.ridecomm.app.ride.DataSaver
import com.ridecomm.app.ride.DataSaverMode
import com.ridecomm.app.ride.RecentRide
import com.ridecomm.app.voice.VoiceCommands
import com.ridecomm.app.ride.RecentRides
import com.ridecomm.app.ride.RideCode
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideState

@Composable
fun HomeScreen(state: RideState) {
    val context = LocalContext.current
    val glassLook = LocalLook.current == UiLook.GLASS
    var name by rememberSaveable { mutableStateOf(Prefs.riderName(context)) }
    // Name as saved; the name field only shows until one is saved.
    var savedName by remember { mutableStateOf(Prefs.riderName(context)) }
    val myPhoto by Profile.photo.collectAsStateWithLifecycle()
    var joinCode by rememberSaveable { mutableStateOf("") }
    var serverReady by remember { mutableStateOf(Prefs.serverConfigured(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    val now = remember { System.currentTimeMillis() }
    // A ride that ended without me leaving (app closed, phone restarted): offer it back in one tap.
    var unfinished by remember { mutableStateOf(RecentRides.rejoin(Prefs.unfinishedRide(context), now)) }
    val recent = remember { RecentRides.shown(Prefs.recentRides(context), now, except = unfinished?.code) }
    // Left a ride without saying I got home: offer it here.
    var checkIn by remember {
        mutableStateOf(Prefs.pendingHomeCheckIn(context)?.takeIf { Prefs.homeSafe(context) && now - it.atMs in 0..HomeLogic.CHECK_IN_WINDOW_MS })
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            // Optional: lets an SOS include where you are.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val code = pendingCode
        pendingCode = null
        if (code != null && result[Manifest.permission.RECORD_AUDIO] == true) {
            RideManager.start(context, code)
        } else if (code != null) {
            Toast.makeText(context, "RideComm needs the microphone to talk to your group", Toast.LENGTH_LONG).show()
        }
    }

    // Invite link tapped: fill in the code and, if the app is set up, join straight away.
    val invite by InviteLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(invite) {
        val code = invite ?: return@LaunchedEffect
        InviteLink.consume()
        joinCode = code
        if (savedName.isNotBlank() && serverReady) {
            pendingCode = code
            permissionLauncher.launch(permissions)
        }
    }

    fun startRide(code: String) {
        Prefs.setRiderName(context, name.trim())
        pendingCode = code
        permissionLauncher.launch(permissions)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (glassLook) 16.dp else 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = if (glassLook) Modifier.padding(top = 8.dp, bottom = 9.dp) else Modifier) {
            Logo(glassLook)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (glassLook) {
                    Text(
                        "RideComm",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = (-1.4).sp),
                        maxLines = 1,
                    )
                    Text(
                        "Group intercom for riders",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = Color(0xFFD2C9FF),
                        maxLines = 1,
                    )
                } else {
                    Text("RideComm", style = MaterialTheme.typography.headlineMedium)
                    Text("Group intercom for riders", style = MaterialTheme.typography.bodyMedium)
                }
            }
            GlassIconButton(
                R.drawable.ms_settings,
                "Settings",
                size = if (glassLook) 53.dp else 48.dp,
                iconSize = if (glassLook) 24.dp else 22.dp,
            ) { showSettings = true }
        }

        CrashReportCard()

        state.error?.let { error ->
            GlassCard(tint = Palette.Stop, fillAlpha = 0.18f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(R.drawable.ms_warning, 24.dp, Palette.Stop)
                    Spacer(Modifier.width(12.dp))
                    Text(error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    GlassIconButton(R.drawable.ms_close, "Dismiss", size = 40.dp, iconSize = 18.dp, onClick = RideManager::clearError)
                }
            }
        }

        val ready = serverReady && name.isNotBlank()
        unfinished?.takeIf { ready }?.let { ride ->
            RejoinCard(
                ride,
                now,
                onDismiss = {
                    Prefs.clearUnfinishedRide(context)
                    unfinished = null
                },
            ) { startRide(ride.code) }
        }

        checkIn?.takeIf { serverReady }?.let { ride ->
            HomeCheckInCard(ride, savedName.ifBlank { name }) {
                Prefs.setPendingHomeCheckIn(context, null)
                checkIn = null
            }
        }

        if (!serverReady) {
            GlassCard(tint = Palette.Amber, fillAlpha = 0.14f) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (Prefs.activeRideServer(context).isNotBlank()) {
                        "Enter your group key in Settings to join your group's rides."
                    } else {
                        "Add your ride server details once to start riding."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                GlassButton("Open settings", R.drawable.ms_settings) { showSettings = true }
            }
        }

        GlassCard(spacing = 18.dp) {
            if (savedName.isBlank()) {
                // First run only; afterwards the name lives in Settings.
                GlassTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = "Your name",
                    placeholder = "e.g. Rahul",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            } else {
                ProfileRow(savedName, myPhoto, glassLook) { showSettings = true }
            }
            PrimaryButton(
                if (glassLook) "Start a new ride  ›" else "Start a new ride",
                R.drawable.ms_two_wheeler,
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { startRide(RideCode.generate()) }
        }

        GlassCard(spacing = 18.dp) {
            GlassTextField(
                value = joinCode,
                onValueChange = { joinCode = RideCode.clean(it) },
                label = "Join your group",
                placeholder = "RIDE CODE",
                textStyle = if (glassLook) {
                    MaterialTheme.typography.displayMedium.copy(fontSize = 22.sp)
                } else {
                    MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                center = true,
            )
            GlassButton(
                "Join ride",
                R.drawable.ms_arrow_forward,
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && joinCode.length == RideCode.LENGTH,
                height = 60.dp,
            ) { startRide(joinCode) }
            if (recent.isNotEmpty() && ready) {
                if (glassLook) GlassRecentRides(recent, now) { startRide(it) } else RecentRidesRow(recent, now) { startRide(it) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(R.drawable.ms_headset_mic, "Talk", Modifier.weight(1f), glassLook)
            Feature(R.drawable.ms_library_music, "Music", Modifier.weight(1f), glassLook)
            Feature(R.drawable.ms_thumb_up, "Votes", Modifier.weight(1f), glassLook)
            Feature(R.drawable.ms_sos, "SOS", Modifier.weight(1f), glassLook)
        }
    }

    if (showSettings) {
        SettingsDialog(
            onClose = { showSettings = false },
            onSaved = {
                serverReady = Prefs.serverConfigured(context)
                savedName = Prefs.riderName(context)
                name = savedName
            },
        )
    }
}

/**
 * "Home safe?" after leaving a ride: tells the riders still in it (joining for a moment, no mic),
 * or shares a message with the group's chat when nobody is left in the ride.
 */
@Composable
internal fun HomeCheckInCard(ride: RecentRide, myName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val share = {
        val text = "Reached home safe \uD83C\uDFE0 ${myName.ifBlank { "" }}".trim() + " (RideComm ride ${ride.code})"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Tell your group"))
    }
    GlassCard(tint = Palette.Go, fillAlpha = 0.12f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_home, 26.dp, Palette.Go)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Home safe?", style = MaterialTheme.typography.titleMedium)
                Text(
                    result ?: "Tell the riders still in ride ${ride.code} that you got home.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            GlassIconButton(R.drawable.ms_close, "Dismiss", size = 40.dp, iconSize = 18.dp, onClick = onDismiss)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result == null) {
                PrimaryButton(
                    if (sending) "Telling them…" else "I'm home safe",
                    R.drawable.ms_home,
                    Modifier.weight(1.3f),
                    brush = Palette.GoGradient,
                    contentColor = Color(0xFF052E1F),
                    height = 52.dp,
                    enabled = !sending,
                ) {
                    sending = true
                    scope.launch {
                        val heard = HomeSafe.checkInLater(context, ride.code)
                        sending = false
                        result = when (heard) {
                            null -> "Couldn't reach the ride. Share it with your group instead."
                            0 -> "Nobody is in the ride any more. Share it with your group instead."
                            1 -> "Told the 1 rider still in the ride."
                            else -> "Told the $heard riders still in the ride."
                        }
                    }
                }
            }
            GlassButton("Share", R.drawable.ms_share, Modifier.weight(0.8f), height = 52.dp, onClick = share)
        }
    }
}

/** "Rejoin ride XCQGCW": the ride was cut off without me leaving it. */
@Composable
internal fun RejoinCard(ride: RecentRide, nowMs: Long, onDismiss: () -> Unit, onRejoin: () -> Unit) {
    GlassCard(tint = Palette.Go, fillAlpha = 0.14f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_replay, 26.dp, Palette.Go)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Back to ride ${ride.code}?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Joined ${RecentRides.ago(ride.atMs, nowMs)} and closed without you leaving. Your group may still be on it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Not now", modifier = Modifier.weight(1f), onClick = onDismiss)
            PrimaryButton("Rejoin", R.drawable.ms_replay, Modifier.weight(1f), height = 56.dp, onClick = onRejoin)
        }
    }
}

/** Codes of recent rides; one tap joins the same ride again. */
@Composable
internal fun RecentRidesRow(rides: List<RecentRide>, nowMs: Long, onJoin: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Ride again")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rides.take(3).forEach { ride ->
                Column(
                    Modifier
                        .weight(1f)
                        .glass(RoundedCornerShape(16.dp), fillAlpha = 0.06f, rimAlpha = 0.2f)
                        .clickable { onJoin(ride.code) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(ride.code, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(RecentRides.ago(ride.atMs, nowMs), style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                }
            }
            // Keep chips the same width when there are fewer than three.
            repeat(3 - rides.take(3).size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Glass look: recent rides as full-width rows ("GMMCBR · 11 min ago ›"). */
@Composable
internal fun GlassRecentRides(rides: List<RecentRide>, nowMs: Long, onJoin: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp).height(1.dp).background(GlassTokens.Divider))
        SectionLabel("Ride again")
        rides.take(3).forEach { ride ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassTokens.Recent)
                    .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(20.dp))
                    .clickable { onJoin(ride.code) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ride.code, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1)
                    Text(RecentRides.ago(ride.atMs, nowMs), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                }
                Box(
                    Modifier
                        .size(37.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.13f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ico(R.drawable.ms_arrow_forward, 18.dp, Color.White) }
            }
        }
    }
}

@Composable
private fun Logo(glassLook: Boolean = false) {
    if (glassLook) {
        val shape = RoundedCornerShape(23.dp)
        Box(
            Modifier
                .size(68.dp)
                .glow(GlassTokens.LogoGlow, 30.dp, shape, offsetY = 8.dp)
                .clip(shape)
                .background(GlassTokens.Logo)
                .border(1.5.dp, Color(0xB8FFD6F6), shape),
            contentAlignment = Alignment.Center,
        ) { Ico(R.drawable.ms_two_wheeler, 39.dp, Color.White) }
        return
    }
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Brand)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) { Ico(R.drawable.ms_two_wheeler, 32.dp, Color.White) }
}

@Composable
private fun Feature(icon: Int, label: String, modifier: Modifier, glassLook: Boolean = false) {
    if (glassLook) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier
                .height(91.dp)
                .clip(shape)
                .frostedBackdrop()
                .background(GlassTokens.Tile)
                .border(1.dp, Color.White.copy(alpha = 0.40f), shape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Ico(icon, 25.dp, Color.White)
            Spacer(Modifier.height(9.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = GlassTokens.TileText)
        }
        return
    }
    Column(
        modifier
            .glass(RoundedCornerShape(20.dp), fillAlpha = 0.05f, rimAlpha = 0.18f)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ico(icon, 24.dp, Palette.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
    }
}

/**
 * App settings, reachable from the home screen and during a ride. In a ride the server ID is
 * hidden (it can't change mid-ride) and the other settings apply straight away.
 */
@Composable
fun SettingsDialog(onClose: () -> Unit, onSaved: () -> Unit = {}, inRide: Boolean = false) {
    val context = LocalContext.current
    var tokenId by remember { mutableStateOf(Prefs.tokenServerId(context)) }
    var serverUrl by remember { mutableStateOf(Prefs.rideServerUrl(context)) }
    var groupKey by remember { mutableStateOf(Prefs.groupKey(context)) }
    var privateServer by remember { mutableStateOf(Prefs.privateServer(context)) }
    var bubbleOn by remember { mutableStateOf(Prefs.bubbleEnabled(context)) }
    var numbers by remember { mutableStateOf(Prefs.emergencyNumbers(context)) }
    var shareLocation by remember { mutableStateOf(Prefs.shareLocation(context)) }
    var keepMusic by remember { mutableStateOf(Prefs.keepOtherMusic(context)) }
    var riderName by remember { mutableStateOf(Prefs.riderName(context)) }
    var crashDetection by remember { mutableStateOf(Prefs.crashDetection(context)) }
    var speakerOverlay by remember { mutableStateOf(Prefs.speakerOverlay(context)) }
    var headsetButtons by remember { mutableStateOf(Prefs.headsetButtons(context)) }
    var windGate by remember { mutableStateOf(Prefs.windGate(context)) }
    var riderAlerts by remember { mutableStateOf(Prefs.riderAlerts(context)) }
    var testingFilter by remember { mutableStateOf(false) }
    var speedLimit by remember { mutableStateOf(Prefs.speedLimit(context)) }
    var rideUpdates by remember { mutableStateOf(Prefs.rideUpdates(context)) }
    var dataSaver by remember { mutableStateOf(Prefs.dataSaver(context)) }
    var talkMode by remember { mutableStateOf(Prefs.talkMode(context)) }
    var nightMode by remember { mutableStateOf(Prefs.nightMode(context)) }
    var hazardAlerts by remember { mutableStateOf(Prefs.hazardAlerts(context)) }
    var roleAlerts by remember { mutableStateOf(Prefs.roleAlerts(context)) }
    var emergencyInfo by remember { mutableStateOf(Prefs.emergencyInfo(context)) }
    var shareInfo by remember { mutableStateOf(Prefs.shareEmergencyInfo(context)) }
    var lockScreenInfo by remember { mutableStateOf(Prefs.lockScreenInfo(context)) }
    var talkToOne by remember { mutableStateOf(Prefs.talkToOne(context)) }
    var destination by remember { mutableStateOf(Prefs.sharedDestination(context)) }
    var homeSafe by remember { mutableStateOf(Prefs.homeSafe(context)) }
    var homeSpot by remember { mutableStateOf(Prefs.homeSpot(context)) }
    var breakEvery by remember { mutableStateOf(Prefs.breakEvery(context)) }
    var look by remember { mutableStateOf(Prefs.look(context)) }
    // Android 13+: the lock screen note is a notification, which needs permission.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Allow notifications for RideComm to show it on the lock screen", Toast.LENGTH_LONG).show()
    }
    // The test can change the filter live during a ride; closing without saving puts it back.
    val cancel = {
        MicGate.setSettings(Prefs.windGate(context))
        onClose()
    }
    val voiceAvailable = remember { VoiceCommands.available(context) }
    var voiceCommands by remember { mutableStateOf(Prefs.voiceCommands(context) && voiceAvailable) }

    GlassDialog(onDismiss = cancel) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
        ProfileEditor(riderName) { riderName = it }
        OptionChips(
            "Look",
            when (look) {
                UiLook.CLASSIC -> "The original RideComm look."
                UiLook.GLASS -> "Frosted glass over a violet glow, on every screen."
            },
            UiLook.entries,
            look,
            { it.label },
        ) { look = it }
        if (!inRide) {
            GlassTextField(
                value = tokenId,
                onValueChange = { tokenId = it },
                label = "LiveKit token server ID",
                placeholder = "ridecomm-xxxxxx",
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "LiveKit Cloud → Settings → Development token server. Everyone in the group uses the same one.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SettingSwitch(
                "Lock rides to my group",
                "Only riders with your group key can join. Needs your group's private ride server; invite links " +
                    "then carry the key, so new riders get in with one tap.",
                privateServer,
            ) { privateServer = it }
            if (privateServer) {
                GlassTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = "Private ride server",
                    placeholder = "https://ridecomm-token.….workers.dev",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                GlassTextField(
                    value = groupKey,
                    onValueChange = { groupKey = it },
                    label = "Group key",
                    placeholder = "Ask your group admin",
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "While this is on, the token server ID above isn't used.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        GlassTextField(
            value = numbers,
            onValueChange = { numbers = it },
            label = "Emergency SMS numbers",
            placeholder = "+91 98xxxxxxxx, …",
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Text("Used for SOS when there's no internet.", style = MaterialTheme.typography.bodyMedium)
        EmergencyInfoSetting(emergencyInfo, shareInfo, onInfo = { emergencyInfo = it }, onShare = { shareInfo = it })
        SettingSwitch(
            "Emergency info on lock screen",
            "A quiet notification anyone can read without unlocking your phone: blood group, allergies and who to call. " +
                "Off unless you switch it on. Also check your phone shows notifications on the lock screen.",
            lockScreenInfo,
        ) {
            lockScreenInfo = it
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        OptionChips(
            "Talk mode",
            when (talkMode) {
                TalkMode.OPEN_MIC -> "Just talk: the wind filter sends your voice and holds back wind."
                TalkMode.PUSH_TO_TALK -> "Silent until you hold the mic button. Tap it once to talk hands-free, tap again to stop. " +
                    "The headset button and floating button start and stop talking too."
            },
            TalkMode.entries,
            talkMode,
            { it.label },
        ) { talkMode = it }
        WindGateSetting(windGate, onTest = { testingFilter = true }) { windGate = it }
        OptionChips(
            "Night mode",
            when (nightMode) {
                NightModeSetting.OFF -> "Normal colours, day and night."
                NightModeSetting.AUTO -> "Dim red screen and quieter alerts from sunset to sunrise where you are."
                NightModeSetting.ON -> "Dim red screen and quieter alerts, all the time. Red light keeps your eyes used to the dark road."
            },
            NightModeSetting.entries,
            nightMode,
            { it.label },
        ) { nightMode = it }
        SettingSwitch("Floating ride button", "Controls over Maps and other apps", bubbleOn) { bubbleOn = it }
        SettingSwitch(
            "Crash detection",
            "After a hard impact and no movement, starts a 15-second SOS countdown you can cancel",
            crashDetection,
        ) { crashDetection = it }
        SettingSwitch(
            "Headset button",
            "1 press: mute/unmute · 2: next song or music off · 3: SOS. While Spotify plays, the button may control Spotify instead.",
            headsetButtons,
        ) { headsetButtons = it }
        SettingSwitch(
            "Voice commands",
            if (voiceAvailable) {
                "Say \"RideComm\" then: break, fuel, food, yes, no, slow down, wait for me, mute, next song, " +
                    "music off, who's here, battery, speed, where is everyone, regroup here, pothole, police, SOS, cancel. Works while your mic is on."
            } else {
                "Needs Android 13 or newer with Google speech recognition"
            },
            voiceCommands,
            warning = !voiceAvailable,
        ) { if (voiceAvailable) voiceCommands = it }
        SettingSwitch(
            "Rider alerts",
            "Speaks when someone joins, drops out or is back, and when a phone's battery gets low",
            riderAlerts,
        ) { riderAlerts = it }
        DataSaverSetting(dataSaver) { dataSaver = it }
        SpeedAlertSetting(speedLimit) { speedLimit = it }
        RideUpdatesSetting(rideUpdates) { rideUpdates = it }
        SettingSwitch("Show who's talking", "Small photos at the top-left over Maps and other apps", speakerOverlay) {
            speakerOverlay = it
        }
        SettingSwitch(
            "Group map",
            "Share your location with the group: a live map of everyone, how far each rider is, alerts when someone " +
                "falls behind, and regroup points. Uses GPS; turned off unless you switch it on.",
            shareLocation,
        ) { shareLocation = it }
        SettingSwitch(
            "Hazard alerts",
            "Mark potholes, speed breakers, police, accidents… for the riders behind, and hear \"Pothole in 300 meters\" " +
                "for the ones ahead. Only the hazard's spot is shared.",
            hazardAlerts,
        ) { hazardAlerts = it }
        SettingSwitch(
            "Lead & sweep alerts",
            "Tap a rider to make them lead or sweep. The lead hears when someone gets ahead, the sweep when someone " +
                "drops behind. Needs Group map on.",
            roleAlerts,
        ) { roleAlerts = it }
        SettingSwitch(
            "Talk to one rider",
            "Hold a rider's name to talk only to them (lead to sweep, say). The others don't hear you while you hold. " +
                "Private between RideComm phones, not encrypted.",
            talkToOne,
        ) { talkToOne = it }
        SettingSwitch(
            "Shared destination",
            "Set where the group is heading: everyone hears it, sees how far it is, and gets one Navigate button. " +
                "Search uses OpenStreetMap. Your own location isn't shared.",
            destination,
        ) { destination = it }
        HomeSafeSetting(homeSafe, homeSpot, onChange = { homeSafe = it }, onHome = { homeSpot = it })
        OptionChips(
            "Break reminder",
            if (breakEvery == BreakEvery.OFF) {
                "No reminders."
            } else {
                "After ${breakEvery.label} of riding: \"Time for a break?\" One tap asks the group with a Break vote. " +
                    "A passed Break vote or a 10-minute stop starts the count again."
            },
            BreakEvery.entries,
            breakEvery,
            { it.label },
        ) { breakEvery = it }
        SettingSwitch(
            "Keep music apps playing",
            "Spotify, YouTube Music… get quieter when someone talks" + if (inRide) ". Applies from your next ride." else "",
            keepMusic,
        ) { keepMusic = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton("Cancel", modifier = Modifier.weight(1f), onClick = cancel)
            PrimaryButton("Save", modifier = Modifier.weight(1f), height = 56.dp) {
                Prefs.setTokenServerId(context, tokenId)
                Prefs.setRideServerUrl(context, serverUrl)
                Prefs.setPrivateServer(context, privateServer)
                Prefs.setGroupKey(context, groupKey)
                Prefs.setBubbleEnabled(context, bubbleOn)
                Prefs.setEmergencyNumbers(context, numbers)
                Prefs.setShareLocation(context, shareLocation)
                Prefs.setKeepOtherMusic(context, keepMusic)
                if (riderName.isNotBlank()) Prefs.setRiderName(context, riderName.trim())
                Prefs.setCrashDetection(context, crashDetection)
                Prefs.setSpeakerOverlay(context, speakerOverlay)
                Prefs.setHeadsetButtons(context, headsetButtons)
                Prefs.setWindGate(context, windGate)
                Prefs.setRiderAlerts(context, riderAlerts)
                Prefs.setSpeedLimit(context, speedLimit)
                Prefs.setRideUpdates(context, rideUpdates)
                TripTracker.applySettings(context)
                Prefs.setDataSaver(context, dataSaver)
                Prefs.setTalkMode(context, talkMode)
                RideManager.applyTalkMode(context)
                Prefs.setNightMode(context, nightMode)
                NightMode.refresh(context)
                Prefs.setHazardAlerts(context, hazardAlerts)
                Hazards.applySettings()
                Prefs.setRoleAlerts(context, roleAlerts)
                Prefs.setEmergencyInfo(context, emergencyInfo)
                Prefs.setShareEmergencyInfo(context, shareInfo)
                Prefs.setLockScreenInfo(context, lockScreenInfo)
                LockScreenInfo.refresh(context)
                Prefs.setTalkToOne(context, talkToOne)
                Prefs.setSharedDestination(context, destination)
                RideDestination.applySettings()
                Prefs.setHomeSafe(context, homeSafe)
                Prefs.setHomeSpot(context, homeSpot)
                HomeSafe.applySettings()
                Prefs.setBreakEvery(context, breakEvery)
                LookSetting.set(context, look)
                BreakReminder.applySettings(context)
                DataSaver.applySettings(context)
                if (voiceAvailable) Prefs.setVoiceCommands(context, voiceCommands)
                VoiceCommands.applySettings(context)
                MicGate.setSettings(windGate)
                HeadsetButtons.applySettings(context)
                CrashDetector.applySettings(context)
                GroupTracker.applySettings()
                onSaved()
                onClose()
            }
        }
    }

    if (testingFilter) {
        WindFilterTestDialog(
            settings = windGate,
            onSettings = {
                windGate = it
                if (inRide) MicGate.setSettings(it)
            },
            inRide = inRide,
            onClose = { testingFilter = false },
        )
    }
}

/** Off / Low / Medium / High choice for the mic's wind noise gate, and a way to see it work. */
@Composable
internal fun WindGateSetting(value: GateSettings?, onTest: () -> Unit = {}, onChange: (GateSettings?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wind noise filter", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sends your mic only while you speak, so others don't hear wind and engine. " +
                "Higher catches quieter voices but lets more noise through.",
            style = MaterialTheme.typography.bodyMedium,
        )
        GateChips(value, onChange)
        GlassButton("Fine-tune and test", R.drawable.ms_graphic_eq, Modifier.fillMaxWidth(), height = 48.dp, onClick = onTest)
    }
}

/** "Speed alert": a switch, and the limit when it's on. */
@Composable
internal fun SpeedAlertSetting(limitKmh: Int, onChange: (Int) -> Unit) {
    Column {
        SettingSwitch(
            "Speed alert",
            if (limitKmh > 0) "Says your speed in the headset when you go over $limitKmh km/h" else "Says your speed in the headset when you go over a limit you set",
            limitKmh > 0,
        ) { onChange(if (it) DEFAULT_SPEED_LIMIT else 0) }
        if (limitKmh > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = limitKmh.toFloat(),
                    onValueChange = { onChange(((it / 5).roundToInt() * 5)) },
                    valueRange = 30f..160f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Palette.Orange,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Text("$limitKmh km/h", style = MaterialTheme.typography.titleMedium, color = Palette.Orange)
            }
        }
    }
}

private const val DEFAULT_SPEED_LIMIT = 90

/** A setting with a few choices shown as a row of chips. */
@Composable
internal fun <T> OptionChips(title: String, description: String, options: List<T>, value: T, label: (T) -> String, onChange: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val selected = option == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .glass(
                            RoundedCornerShape(14.dp),
                            tint = if (selected) Palette.Orange else Color.White,
                            fillAlpha = if (selected) 0.32f else 0.06f,
                        )
                        .clickable { onChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Palette.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Blood group, allergies and an emergency contact. Kept on the phone; sent only with my SOS, so
 * the group (or a stranger holding my phone) knows what to tell the ambulance.
 */
@Composable
internal fun EmergencyInfoSetting(info: EmergencyInfo, share: Boolean, onInfo: (EmergencyInfo) -> Unit, onShare: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Emergency info", style = MaterialTheme.typography.titleMedium)
        Text(
            "Shown on your screen when your SOS is on, for anyone helping you, and sent to the group with your SOS.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Blood group", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        EmergencyInfo.BLOOD_GROUPS.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { group ->
                    val selected = info.bloodGroup == group
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .glass(RoundedCornerShape(14.dp), tint = if (selected) Palette.Stop else Color.White, fillAlpha = if (selected) 0.34f else 0.06f)
                            .clickable { onInfo(info.copy(bloodGroup = if (selected) "" else group)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(group, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Palette.TextSecondary)
                    }
                }
            }
        }
        GlassTextField(
            value = info.medical,
            onValueChange = { onInfo(info.copy(medical = it.take(EmergencyInfo.MAX_MEDICAL))) },
            label = "Allergies, conditions, medicines",
            placeholder = "e.g. Allergic to penicillin",
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(
                value = info.contactName,
                onValueChange = { onInfo(info.copy(contactName = it.take(EmergencyInfo.MAX_SHORT))) },
                label = "Contact name",
                placeholder = "e.g. Ammi",
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            GlassTextField(
                value = info.contactPhone,
                onValueChange = { onInfo(info.copy(contactPhone = it.take(EmergencyInfo.MAX_SHORT))) },
                label = "Phone",
                placeholder = "+91 98…",
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
        SettingSwitch("Send with my SOS", "Off: it only shows on your own screen", share, onChange = onShare)
    }
}

/**
 * Home safe on/off, and where home is (for the automatic check-in). Home's position stays on the
 * phone: only "home safe" is ever sent.
 */
@Composable
internal fun HomeSafeSetting(on: Boolean, home: Pair<Double, Double>?, onChange: (Boolean) -> Unit, onHome: (Pair<Double, Double>?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var finding by remember { mutableStateOf(false) }
    val findHome = {
        finding = true
        scope.launch {
            val fix = LocationHelper.fresh(context)
            finding = false
            if (fix == null) {
                Toast.makeText(context, "Couldn't find your location. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
            } else {
                onHome(fix.latitude to fix.longitude)
                Toast.makeText(context, "Home saved (it stays on this phone)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) findHome() else Toast.makeText(context, "Allow location to save home", Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingSwitch(
            "Home safe check-in",
            "At the end of the ride tap \"I'm home safe\" and the riders still on the road hear it. With home saved, " +
                "it happens by itself when you get there.",
            on,
            onChange = onChange,
        )
        if (on) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(
                    when {
                        finding -> "Finding you…"
                        home == null -> "I'm home: save this spot"
                        else -> "Home saved · update"
                    },
                    R.drawable.ms_home,
                    Modifier.weight(1f),
                    height = 48.dp,
                    enabled = !finding,
                ) {
                    if (LocationHelper.hasPermission(context)) findHome() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (home != null) GlassButton("Forget", modifier = Modifier.weight(0.5f), height = 48.dp) { onHome(null) }
            }
        }
    }
}

/** Off / Auto / Always choice for saving mobile data. */
@Composable
internal fun DataSaverSetting(value: DataSaverMode, onChange: (DataSaverMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Data saver", style = MaterialTheme.typography.titleMedium)
        Text(
            when (value) {
                DataSaverMode.OFF -> "Best voice quality and shared music, always."
                DataSaverMode.AUTO -> "When your network is weak: lighter voice and shared music paused. " +
                    "Back to normal, and music catches up, once the network is good again."
                DataSaverMode.ALWAYS -> "Lighter voice (half the data, still clear) and no shared music downloads."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DataSaverMode.entries.forEach { option ->
                val selected = option == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .glass(
                            RoundedCornerShape(14.dp),
                            tint = if (selected) Palette.Orange else Color.White,
                            fillAlpha = if (selected) 0.32f else 0.06f,
                        )
                        .clickable { onChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Palette.TextSecondary,
                    )
                }
            }
        }
    }
}

/** How often to hear distance, riding time and average speed. */
@Composable
internal fun RideUpdatesSetting(value: UpdateEvery, onChange: (UpdateEvery) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ride updates", style = MaterialTheme.typography.titleMedium)
        Text("Hear distance, riding time and average speed every:", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UpdateEvery.entries.forEach { option ->
                val selected = option == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .glass(
                            RoundedCornerShape(14.dp),
                            tint = if (selected) Palette.Orange else Color.White,
                            fillAlpha = if (selected) 0.32f else 0.06f,
                        )
                        .clickable { onChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Palette.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    warning: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Top) {
                if (warning) {
                    Ico(R.drawable.ms_warning, 16.dp, Palette.Amber)
                    Spacer(Modifier.width(6.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (warning) Palette.Amber else Palette.TextSecondary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Orange,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
            ),
        )
    }
}

/** Shown after a crash: lets the rider send the saved details to whoever maintains the app. */
@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return
    GlassCard(tint = Palette.Amber, fillAlpha = 0.14f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_warning, 24.dp, Palette.Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RideComm closed unexpectedly", style = MaterialTheme.typography.titleMedium)
                Text("Share the report so it can be fixed.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Dismiss", modifier = Modifier.weight(1f)) {
                CrashLog.clear(context)
                report = null
            }
            PrimaryButton("Share report", R.drawable.ms_share, Modifier.weight(1f), height = 56.dp) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share crash report"))
            }
        }
    }
}

/** "Riding as Saquelain" with my photo; tap to edit in Settings. */
@Composable
private fun ProfileRow(name: String, photo: Bitmap?, glassLook: Boolean = false, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
            .then(if (glassLook) Modifier.padding(bottom = 7.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glassLook) {
            // A light-blue ring with a soft glow around the photo.
            Box(Modifier.glow(GlassTokens.AvatarGlow, 14.dp, CircleShape).border(2.dp, GlassTokens.AvatarRing, CircleShape).padding(2.dp)) {
                Avatar(name, 70.dp, Palette.Brand, ring = null, photo = photo)
            }
            Spacer(Modifier.width(15.dp))
        } else {
            Avatar(name, 56.dp, Palette.Brand, ring = null, photo = photo)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            SectionLabel("Riding as")
            if (glassLook) Spacer(Modifier.height(5.dp))
            Text(
                name,
                style = if (glassLook) MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp, letterSpacing = (-0.8).sp) else MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
        }
        GlassIconButton(
            R.drawable.ms_edit,
            "Edit profile",
            size = if (glassLook) 47.dp else 44.dp,
            iconSize = if (glassLook) 22.dp else 20.dp,
            onClick = onEdit,
        )
    }
}

/** Photo and name at the top of Settings. Tap the photo to pick a new one. */
@Composable
private fun ProfileEditor(name: String, onNameChange: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photo by Profile.photo.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (!Profile.setPhoto(context, uri)) {
                    Toast.makeText(context, "Couldn't use that image", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).clickable(onClick = pick)) {
            Avatar(name.ifBlank { "?" }, 76.dp, Palette.Brand, ring = null, photo = photo)
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(if (photo == null) "Add photo" else "Change photo", R.drawable.ms_photo_camera, height = 44.dp, onClick = pick)
            if (photo != null) {
                Text(
                    "Remove photo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Stop,
                    modifier = Modifier.clickable { Profile.removePhoto(context) }.padding(start = 6.dp),
                )
            }
        }
    }
    GlassTextField(
        value = name,
        onValueChange = { onNameChange(it.take(20)) },
        label = "Your name",
        placeholder = "e.g. Rahul",
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}
