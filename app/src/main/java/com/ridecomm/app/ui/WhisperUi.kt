package com.ridecomm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ridecomm.app.R
import com.ridecomm.app.whisper.WhisperState

/** Private talk is violet, so it never looks like talking to the whole group (green). */
val WhisperColor = Palette.Violet

/** A button that talks only to one rider while held down. */
@Composable
fun HoldToTalkButton(text: String, active: Boolean, modifier: Modifier = Modifier, height: Dp = 60.dp, onStart: () -> Unit, onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .height(height)
            .clip(PillShape)
            .background(
                if (active) {
                    Brush.linearGradient(listOf(WhisperColor, Palette.Pink))
                } else {
                    Brush.linearGradient(listOf(WhisperColor.copy(alpha = 0.35f), WhisperColor.copy(alpha = 0.18f)))
                },
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.7f else 0.3f), PillShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart()
                        tryAwaitRelease()
                        onStop()
                    },
                )
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Ico(R.drawable.ms_record_voice_over, 24.dp, Color.White)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

/**
 * Top of the ride screen while talking privately: "Talking only to Bilal" for me, or "Asha is
 * talking only to you" with a hold-to-reply button for the rider being talked to.
 */
@Composable
fun WhisperBanner(whisper: WhisperState, canReply: Boolean, onReplyStart: () -> Unit, onReplyStop: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            // Nearly opaque: the banner floats over the screen and must stay readable.
            .background(Brush.linearGradient(listOf(Color(0xF52A1F52), Color(0xF5161230))))
            .border(1.dp, Brush.linearGradient(listOf(WhisperColor.copy(alpha = 0.8f), WhisperColor.copy(alpha = 0.2f))), CardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val to = whisper.talkingTo
        val from = whisper.fromMe
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.ms_record_voice_over, 26.dp, WhisperColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                when {
                    to != null -> {
                        Text(if (whisper.live) "Only ${whisper.talkingToName} hears you" else "Connecting…", style = MaterialTheme.typography.bodyMedium)
                        Text("Talking to ${whisper.talkingToName}", style = MaterialTheme.typography.titleMedium)
                    }
                    from != null -> {
                        Text(if (whisper.fromMeNow) "Only you can hear this" else "Private talk ended", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (whisper.fromMeNow) "${from.fromName} is talking to you" else "${from.fromName} talked only to you",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        if (to == null && from != null && canReply) {
            HoldToTalkButton("Hold to reply to ${from.fromName}", active = false, modifier = Modifier.fillMaxWidth(), height = 52.dp, onStart = onReplyStart, onStop = onReplyStop)
        }
    }
}
