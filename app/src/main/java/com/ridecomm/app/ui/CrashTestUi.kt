package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Slider
import com.ridecomm.app.Prefs
import com.ridecomm.app.crash.CrashTuning
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.crash.CrashDetector
import com.ridecomm.app.crash.CrashLogic
import java.util.Locale

/**
 * Settings → Safety → Test crash detection: the rider throws the phone onto a bed and sees, step by
 * step, what the detector felt and whether GPS or the phone's stillness decided. Same logic as on a
 * ride, except it never sends anything. Riders trust a safety feature they've seen work.
 */
@Composable
fun CrashTestDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by CrashDetector.test.collectAsState()
    DisposableEffect(Unit) {
        CrashDetector.startTest(context)
        onDispose { CrashDetector.stopTest(context) }
    }
    var tuning by remember { mutableStateOf(Prefs.crashTuning(context)) }
    GlassDialog(onDismiss = onClose) {
        CrashTestContent(
            state,
            onAgain = { CrashDetector.startTest(context) },
            onClose = onClose,
            tuning = tuning,
            onTune = {
                // Saved for rides too, and the test starts over with the new numbers.
                tuning = it
                Prefs.setCrashTuning(context, it)
                CrashDetector.startTest(context)
            },
        )
    }
}

/** The test's steps and live status, from plain state (screenshots render any moment of it). */
@Composable
fun CrashTestContent(
    state: CrashDetector.TestState,
    onAgain: () -> Unit,
    onClose: () -> Unit,
    tuning: CrashTuning = CrashTuning(),
    onTune: (CrashTuning) -> Unit = {},
) {
    Text("Test crash detection", style = MaterialTheme.typography.headlineMedium)
    Text(
        "1. Throw your phone onto a bed or sofa, or drop it onto a cushion.\n" +
            "2. Wait about ${tuning.waitS + 2} seconds. You can pick it up and walk around: as long as you don't ride off, it counts.",
        style = MaterialTheme.typography.bodyLarge,
        color = Palette.TextPrimary,
    )
    val (title, line, color) = when {
        state.noSensor -> Triple("No motion sensor", "This phone has no accelerometer, so crash detection can't work on it.", Palette.Stop)
        state.phase == CrashLogic.Phase.WAITING -> Triple(
            "Waiting for a hard hit…",
            "A hit needs ${g(tuning.g)}, or a fall and a landing. Hardest so far: ${g(state.peakG)}.",
            Palette.TextSecondary,
        )
        state.phase == CrashLogic.Phase.SETTLING -> Triple("Hit felt: ${g(state.impactG)}", "Checking what happens next…", Palette.Amber)
        state.phase == CrashLogic.Phase.STILL -> Triple(
            "Hit felt: ${g(state.impactG)}",
            (if (state.gps) "GPS: checking the bike stays put… " else "No GPS yet: keep the phone still… ") +
                "${((1 - state.stillProgress) * tuning.waitS).toInt().coerceAtLeast(1)} s",
            Palette.Amber,
        )
        state.phase == CrashLogic.Phase.MOVED -> Triple(
            "Not a crash",
            "Either GPS showed you moving on (riding), or, without GPS, the phone moved. " +
                "Without GPS the phone has to lie still for the ${tuning.waitS + 2} seconds. Try again.",
            Palette.TextSecondary,
        )
        else -> Triple(
            "Crash detected ✓",
            (if (state.reason == CrashLogic.Reason.STOPPED) "GPS showed the bike stayed put after the hit. " else "The phone lay still after the hit. ") +
                "On a ride, the SOS countdown would start now: 15 seconds to cancel before your group gets the SOS. Nothing was sent.",
            Palette.Go,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_car_crash, 26.dp, color)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = color)
        }
        Text(line, style = MaterialTheme.typography.bodyMedium)
        if (state.phase == CrashLogic.Phase.STILL) {
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(PillShape)
                    .background(if (LocalLook.current == UiLook.NEU) NeuTokens.Track else Color.White.copy(alpha = 0.12f)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(state.stillProgress).clip(PillShape).background(Palette.Amber))
            }
        }
    }
    Text(
        "On a ride it works the same way, with Crash detection on: after a hard hit, if the bike doesn't move on, " +
            "the SOS countdown starts, even if you got up. A phone dropped at a stop can start it too: tap to cancel. " +
            "With location off it falls back to the phone lying still.",
        style = MaterialTheme.typography.bodyMedium,
    )
    ButtonRow(2) {
        GlassButton("Try again", R.drawable.ms_replay, Modifier.share()) { onAgain() }
        PrimaryButton("Done", R.drawable.ms_check_circle, Modifier.share(), height = 56.dp) { onClose() }
    }
    TuningCard(tuning, onTune)
}

/**
 * Fine-tune: the hit strength, how long to watch after it, and how far the bike may move and still
 * count as stopped. Saved for rides too; the test restarts when a slider is let go.
 */
@Composable
private fun TuningCard(tuning: CrashTuning, onTune: (CrashTuning) -> Unit) {
    // While dragging, only the numbers move; letting go saves and restarts the test.
    var draft by remember(tuning) { mutableStateOf(tuning) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Fine-tune (also used on rides)")
        TuneSlider(
            "Hit strength", g(draft.g), "Lower catches softer hits, and more potholes.",
            draft.g, CrashTuning.G_MIN..CrashTuning.G_MAX, steps = 0,
            onChange = { draft = draft.copy(g = (it * 2).roundToInt() / 2f) }, onDone = { onTune(draft) },
        )
        TuneSlider(
            "Watch after the hit", "${draft.waitS} s", "How long it checks the bike stays put (or the phone lies still).",
            draft.waitS.toFloat(), CrashTuning.WAIT_MIN.toFloat()..CrashTuning.WAIT_MAX.toFloat(), steps = 0,
            onChange = { draft = draft.copy(waitS = it.roundToInt()) }, onDone = { onTune(draft) },
        )
        TuneSlider(
            "Bike may move", "${draft.moveM} m", "Further than this from the crash spot (by GPS) means you rode on.",
            draft.moveM.toFloat(), CrashTuning.MOVE_MIN.toFloat()..CrashTuning.MOVE_MAX.toFloat(), steps = 0,
            onChange = { draft = draft.copy(moveM = (it / 10).roundToInt() * 10) }, onDone = { onTune(draft) },
        )
        if (!tuning.isDefault) {
            GlassButton("Back to the usual settings", R.drawable.ms_replay, Modifier.fillMaxWidth(), height = 48.dp) { onTune(CrashTuning()) }
        }
    }
}

@Composable
private fun TuneSlider(
    label: String,
    value: String,
    hint: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitText(label, MaterialTheme.typography.titleMedium, Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
        }
        Slider(
            value = current,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
            colors = lookSliderColors(),
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
    }
}

private fun g(value: Float) = String.format(Locale.US, "%.1f g", value)
