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
 * step, what the detector felt. Same logic as on a ride, except it never sends anything. Riders trust a safety feature they've seen work.
 */
@Composable
fun CrashTestDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by CrashDetector.test.collectAsState()
    DisposableEffect(Unit) {
        CrashDetector.startTest(context)
        onDispose { CrashDetector.stopTest(context) }
    }
    GlassDialog(onDismiss = onClose) {
        CrashTestContent(state, onAgain = { CrashDetector.startTest(context) }, onClose = onClose)
    }
}

/** The test's steps and live status, from plain state (screenshots render any moment of it). */
@Composable
fun CrashTestContent(state: CrashDetector.TestState, onAgain: () -> Unit, onClose: () -> Unit) {
    Text("Test crash detection", style = MaterialTheme.typography.headlineMedium)
    Text(
        "1. Throw your phone onto a bed or sofa, or drop it onto a cushion.\n" +
            "2. Leave it there, untouched, for about 12 seconds.",
        style = MaterialTheme.typography.bodyLarge,
        color = Palette.TextPrimary,
    )
    val (title, line, color) = when {
        state.noSensor -> Triple("No motion sensor", "This phone has no accelerometer, so crash detection can't work on it.", Palette.Stop)
        state.phase == CrashLogic.Phase.WAITING -> Triple(
            "Waiting for a hard hit…",
            "A hit needs ${CrashLogic.IMPACT_G.toInt()} g, or a fall and a landing. Hardest so far: ${g(state.peakG)}.",
            Palette.TextSecondary,
        )
        state.phase == CrashLogic.Phase.SETTLING -> Triple("Hit felt: ${g(state.impactG)}", "Now don't touch the phone…", Palette.Amber)
        state.phase == CrashLogic.Phase.STILL -> Triple(
            "Hit felt: ${g(state.impactG)}",
            "Keep it still… ${((1 - state.stillProgress) * 10).toInt().coerceAtLeast(1)} s",
            Palette.Amber,
        )
        state.phase == CrashLogic.Phase.MOVED -> Triple(
            "It moved, so not a crash",
            "After a crash the phone lies still. Leave it alone for the whole 12 seconds, or try again.",
            Palette.TextSecondary,
        )
        else -> Triple(
            "Crash detected ✓",
            "On a ride, the SOS countdown would start now: 15 seconds to cancel before your group gets the SOS. " +
                "Nothing was sent.",
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
        "On a ride it works the same way, with Crash detection on. A phone dropped at a stop can start the " +
            "countdown too: just tap to cancel it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    ButtonRow(2) {
        GlassButton("Try again", R.drawable.ms_replay, Modifier.share()) { onAgain() }
        PrimaryButton("Done", R.drawable.ms_check_circle, Modifier.share(), height = 56.dp) { onClose() }
    }
}

private fun g(value: Float) = String.format(Locale.US, "%.1f g", value)
