package com.ridecomm.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.music.MusicState

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

    GlassCard(spacing = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Brand),
                contentAlignment = Alignment.Center,
            ) { Ico(R.drawable.ms_music_note, 30.dp, Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    music.title ?: "Group music",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle(music),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (music.ducked) Palette.Amber else Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (music.title == null) {
            GlassButton("Share songs from this phone", R.drawable.ms_library_music, Modifier.fillMaxWidth(), height = 58.dp, onClick = pickSongs)
            MusicAppsRow()
            return@GlassCard
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (music.iAmDj) {
                GlassIconButton(R.drawable.ms_stop, "Stop music", tint = Palette.Stop, onClick = MusicManager::stopMusic)
                GlassIconButton(
                    if (music.playing) R.drawable.ms_pause else R.drawable.ms_play_arrow,
                    if (music.playing) "Pause" else "Play",
                    size = 72.dp,
                    iconSize = 36.dp,
                    brush = Palette.Brand,
                    onClick = MusicManager::playPause,
                )
                GlassIconButton(R.drawable.ms_skip_next, "Next song", onClick = MusicManager::next)
                GlassIconButton(R.drawable.ms_add, "Add songs", onClick = pickSongs)
            } else {
                GlassButton(
                    if (music.offForMe) "Turn music on" else "Music off for me",
                    if (music.offForMe) R.drawable.ms_volume_up else R.drawable.ms_volume_off,
                    Modifier.weight(1f),
                    onClick = MusicManager::toggleOffForMe,
                )
                Spacer(Modifier.width(10.dp))
                GlassIconButton(R.drawable.ms_add, "Play my songs", onClick = pickSongs)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ms_remove, "Volume down", size = 48.dp, iconSize = 22.dp) {
                MusicManager.setVolume(music.volume - VOLUME_STEP)
            }
            VolumeBar(music.volume, Modifier.weight(1f))
            GlassIconButton(R.drawable.ms_add, "Volume up", size = 48.dp, iconSize = 22.dp) {
                MusicManager.setVolume(music.volume + VOLUME_STEP)
            }
        }
    }
}

@Composable
private fun VolumeBar(volume: Float, modifier: Modifier) {
    Box(
        modifier
            .padding(horizontal = 14.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(volume.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(Palette.Brand),
        )
    }
}

/**
 * Shortcuts to the music apps installed on this phone. Their music keeps playing during the ride
 * and goes quieter whenever someone talks.
 */
@Composable
private fun MusicAppsRow() {
    val context = LocalContext.current
    if (!Prefs.keepOtherMusic(context)) return
    val apps = remember {
        MUSIC_APPS.mapNotNull { (label, pkg) ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { label to it }
        }
    }
    Text(
        "Or play Spotify / YouTube Music as usual. It turns down whenever someone talks.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (apps.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { (label, intent) ->
            GlassButton(label, R.drawable.ms_play_arrow, Modifier.weight(1f), height = 52.dp) {
                context.startActivity(intent)
            }
        }
    }
}

/** Music apps offered as shortcuts (also listed under <queries> in the manifest). */
private val MUSIC_APPS = listOf(
    "Spotify" to "com.spotify.music",
    "YT Music" to "com.google.android.apps.youtube.music",
)

private fun subtitle(music: MusicState): String = when {
    music.title == null -> "DJ mode: songs from one phone, played on everyone's"
    else -> buildString {
        append(music.status ?: if (music.playing) "Playing" else "Paused")
        append(" · DJ ")
        append(if (music.iAmDj) "you" else music.djName ?: "?")
        if (music.iAmDj && music.queued > 0) append(" · ${music.queued} up next")
        if (music.ducked) append(" · lowered for talk")
    }
}
