package com.ridecomm.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.music.MusicState
import kotlin.math.roundToInt

private const val VOLUME_STEP = 0.1f

@Composable
fun MusicCard(music: MusicState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val skipped = MusicManager.addSongs(context, uris)
        if (skipped > 0) Toast.makeText(context, "Skipped $skipped song(s) over 30 MB", Toast.LENGTH_LONG).show()
    }
    val pickSongs = { picker.launch(arrayOf("audio/*")) }

    if (music.title == null) {
        OutlinedButton(onClick = pickSongs, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("🎵  Play music for the group", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🎵 ${music.title}", fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle(music), fontSize = 15.sp, color = if (music.ducked) Orange else Muted)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (music.iAmDj) {
                CardButton(if (music.playing) "⏸ Pause" else "▶ Play", onClick = MusicManager::playPause)
                CardButton("⏭ Next", onClick = MusicManager::next)
                CardButton("＋ Add", onClick = pickSongs)
                CardButton("⏹ Stop", danger = true, onClick = MusicManager::stopMusic)
            } else {
                CardButton(if (music.offForMe) "🔈 Music on" else "🔇 Music off", onClick = MusicManager::toggleOffForMe)
                CardButton("＋ Play mine", onClick = pickSongs)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton("−", onClick = { MusicManager.setVolume(music.volume - VOLUME_STEP) })
            Text(
                "Volume ${(music.volume * 100).roundToInt()}%",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(120.dp),
            )
            CardButton("+", onClick = { MusicManager.setVolume(music.volume + VOLUME_STEP) })
        }
    }
}

private fun subtitle(music: MusicState): String = buildString {
    append(music.status ?: if (music.playing) "Playing" else "Paused")
    append(" · DJ: ")
    append(if (music.iAmDj) "you" else music.djName ?: "?")
    if (music.iAmDj && music.queued > 0) append(" · ${music.queued} up next")
    if (music.ducked) append(" · lowered for talk")
}

@Composable
private fun RowScope.CardButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Danger.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (danger) Danger else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}
