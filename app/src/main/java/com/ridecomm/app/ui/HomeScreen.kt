package com.ridecomm.app.ui

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
import com.ridecomm.app.R
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.audio.NoiseGate
import com.ridecomm.app.crash.CrashDetector
import com.ridecomm.app.profile.Profile
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.headset.HeadsetButtons
import com.ridecomm.app.ride.InviteLink
import com.ridecomm.app.ride.RideCode
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideState

@Composable
fun HomeScreen(state: RideState) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(Prefs.riderName(context)) }
    // Name as saved; the name field only shows until one is saved.
    var savedName by remember { mutableStateOf(Prefs.riderName(context)) }
    val myPhoto by Profile.photo.collectAsStateWithLifecycle()
    var joinCode by rememberSaveable { mutableStateOf("") }
    var serverReady by remember { mutableStateOf(Prefs.serverConfigured(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingCode by remember { mutableStateOf<String?>(null) }

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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("RideComm", style = MaterialTheme.typography.headlineMedium)
                Text("Group intercom for riders", style = MaterialTheme.typography.bodyMedium)
            }
            GlassIconButton(R.drawable.ms_settings, "Settings", size = 48.dp, iconSize = 22.dp) { showSettings = true }
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

        if (!serverReady) {
            GlassCard(tint = Palette.Amber, fillAlpha = 0.14f) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (Prefs.rideServerUrl(context).isNotBlank()) {
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
                ProfileRow(savedName, myPhoto) { showSettings = true }
            }
            PrimaryButton(
                "Start a new ride",
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
                textStyle = MaterialTheme.typography.displayMedium.copy(fontSize = 30.sp),
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
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(R.drawable.ms_headset_mic, "Talk", Modifier.weight(1f))
            Feature(R.drawable.ms_library_music, "Music", Modifier.weight(1f))
            Feature(R.drawable.ms_thumb_up, "Votes", Modifier.weight(1f))
            Feature(R.drawable.ms_sos, "SOS", Modifier.weight(1f))
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

@Composable
private fun Logo() {
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
private fun Feature(icon: Int, label: String, modifier: Modifier) {
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
    var showAdvanced by remember { mutableStateOf(serverUrl.isNotBlank()) }
    var bubbleOn by remember { mutableStateOf(Prefs.bubbleEnabled(context)) }
    var numbers by remember { mutableStateOf(Prefs.emergencyNumbers(context)) }
    var shareLocation by remember { mutableStateOf(Prefs.shareLocation(context)) }
    var keepMusic by remember { mutableStateOf(Prefs.keepOtherMusic(context)) }
    var riderName by remember { mutableStateOf(Prefs.riderName(context)) }
    var crashDetection by remember { mutableStateOf(Prefs.crashDetection(context)) }
    var speakerOverlay by remember { mutableStateOf(Prefs.speakerOverlay(context)) }
    var headsetButtons by remember { mutableStateOf(Prefs.headsetButtons(context)) }
    var windGate by remember { mutableStateOf(Prefs.windGate(context)) }

    GlassDialog(onDismiss = onClose) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp))
        ProfileEditor(riderName) { riderName = it }
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
            // The private ride server isn't set up yet; keep its fields out of the way.
            Text(
                if (showAdvanced) "Hide advanced" else "Advanced: private ride server",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Cyan,
                modifier = Modifier.clickable { showAdvanced = !showAdvanced },
            )
            if (showAdvanced) {
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
                    "When a private server is set, only riders with the group key can join, and the token server ID above isn't used.",
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
        WindGateSetting(windGate) { windGate = it }
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
        SettingSwitch("Show who's talking", "Small photos at the top-left over Maps and other apps", speakerOverlay) {
            speakerOverlay = it
        }
        SettingSwitch(
            "Share my location (beta)",
            "Under construction: distances and separation alerts may not work correctly yet",
            shareLocation,
            warning = true,
        ) { shareLocation = it }
        SettingSwitch(
            "Keep music apps playing",
            "Spotify, YouTube Music… get quieter when someone talks" + if (inRide) ". Applies from your next ride." else "",
            keepMusic,
        ) { keepMusic = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton("Cancel", modifier = Modifier.weight(1f), onClick = onClose)
            PrimaryButton("Save", modifier = Modifier.weight(1f), height = 56.dp) {
                Prefs.setTokenServerId(context, tokenId)
                Prefs.setRideServerUrl(context, serverUrl)
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
                MicGate.setSensitivity(windGate)
                HeadsetButtons.applySettings(context)
                CrashDetector.applySettings(context)
                GroupTracker.applySettings()
                onSaved()
                onClose()
            }
        }
    }
}

/** Off / Low / Medium / High choice for the mic's wind noise gate; applies straight away, even mid-ride. */
@Composable
internal fun WindGateSetting(value: NoiseGate.Sensitivity?, onChange: (NoiseGate.Sensitivity?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wind noise filter", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sends your mic only while you speak, so others don't hear wind and engine. " +
                "Higher catches quieter voices but lets more noise through.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null to "Off", NoiseGate.Sensitivity.LOW to "Low", NoiseGate.Sensitivity.MEDIUM to "Medium", NoiseGate.Sensitivity.HIGH to "High")
                .forEach { (option, label) ->
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
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else Palette.TextSecondary,
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
private fun ProfileRow(name: String, photo: Bitmap?, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, 56.dp, Palette.Brand, ring = null, photo = photo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SectionLabel("Riding as")
            Text(name, style = MaterialTheme.typography.titleLarge)
        }
        GlassIconButton(R.drawable.ms_edit, "Edit profile", size = 44.dp, iconSize = 20.dp, onClick = onEdit)
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
