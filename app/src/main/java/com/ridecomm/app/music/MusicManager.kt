package com.ridecomm.app.music

import com.ridecomm.app.ride.riderIds
import com.ridecomm.app.ride.Riders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.ride.DataSaver
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamBytesOptions
import io.livekit.android.room.datastream.incoming.ByteStreamReceiver
import io.livekit.android.room.datastream.outgoing.write
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

data class MusicState(
    /** Null when no music is on. */
    val title: String? = null,
    val djName: String? = null,
    val iAmDj: Boolean = false,
    val playing: Boolean = false,
    /** Short progress note such as "Getting song… 40%", or null. */
    val status: String? = null,
    /** Songs waiting after the current one (DJ only). */
    val queued: Int = 0,
    val volume: Float = 0.8f,
    val offForMe: Boolean = false,
    /** True while someone is talking and the music is turned down. */
    val ducked: Boolean = false,
)

/**
 * Shared music for a ride. One rider is the DJ: their phone sends each song file to everyone
 * once, then broadcasts small "now playing at position X" messages. Every phone plays the file
 * locally, so music survives network dead zones and each phone can turn it down on its own
 * whenever anyone talks.
 */
object MusicManager {
    const val TEXT_TOPIC = "rc-music"
    private const val FILE_TOPIC = "rc-song"
    private const val ATTR_ID = "id"
    private const val ATTR_TITLE = "title"

    private const val MAX_SONG_BYTES = 30L * 1024 * 1024
    private const val DUCK_LEVEL = 0.25f
    /** Wait this long after the last voice before bringing the music back up. */
    private const val DUCK_RELEASE_MS = 900L
    private const val FADE_DOWN_MS = 150L
    private const val FADE_UP_MS = 600L
    /** Listeners only jump in the song when they're this far off from the DJ. */
    private const val MAX_DRIFT_MS = 1_500L
    private const val RESYNC_INTERVAL_MS = 20_000L
    private const val KEEP_SONG_FILES = 3
    private const val NEED_RETRY_MS = 15_000L

    private class Song(val id: String, val title: String, val uri: Uri, val size: Long?)

    private val scope = safeMainScope()
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null
    private var player: ExoPlayer? = null
    private var loadedId: String? = null

    // DJ side
    private val queue = ArrayDeque<Song>()
    private var current: Song? = null
    private val djSongs = mutableMapOf<String, Song>()
    private val broadcastIds = mutableSetOf<String>()
    private val broadcastsInFlight = mutableMapOf<String, Deferred<Unit>>()
    private var resyncJob: Job? = null
    /** Riders saving data: song files skip them until they ask with a Need. */
    private val savers = mutableSetOf<String>()

    // Listener side
    private var lastNow: MusicMessage.Now? = null
    private var lastNowFrom: Participant.Identity? = null
    private val songFiles = LinkedHashMap<String, File>()
    private val downloading = mutableSetOf<String>()
    /** Song id → when we last asked the DJ for it. */
    private val requested = mutableMapOf<String, Long>()

    private var duckReleaseJob: Job? = null
    private var fadeJob: Job? = null

    private val musicDir get() = File(appContext.cacheDir, "music")

    /** Called with each new connection, before joining. Music already playing keeps going across reconnects. */
    fun attach(context: Context, r: Room) {
        appContext = context.applicationContext
        room = r
        r.registerTextStreamHandler(TEXT_TOPIC) { reader, from ->
            scope.launch {
                val text = runCatching { reader.readAll().joinToString("") }.getOrNull() ?: return@launch
                MusicMessage.decode(text)?.let { onMessage(it, from) }
            }
        }
        r.registerByteStreamHandler(FILE_TOPIC) { reader, _ -> scope.launch { receiveSong(reader) } }
    }

    /** After (re)joining: the DJ re-announces the song, everyone else asks what's playing. */
    fun onConnected() {
        scope.launch {
            if (DataSaver.active.value) send(MusicMessage.Saving(true))
            if (_state.value.iAmDj) broadcastNow() else send(MusicMessage.Sync)
        }
    }

    /**
     * Data saving switched on or off. On: the DJ stops sending me song files. Off: catch up by
     * asking for the song that's playing now.
     */
    private fun onDataSaverChanged(saving: Boolean) {
        if (room == null) return
        scope.launch {
            send(MusicMessage.Saving(saving))
            val now = lastNow ?: return@launch
            val dj = lastNowFrom ?: return@launch
            if (!saving && !_state.value.iAmDj && now.songId !in songFiles && now.songId !in downloading) {
                requested[now.songId] = System.currentTimeMillis()
                send(MusicMessage.Need(now.songId), listOf(dj))
                _state.update { it.copy(status = "Getting song…") }
            }
        }
    }

    /** The connection dropped; keep playing locally until it's back. */
    fun detach() {
        room = null
    }

    /** The rider left the ride: stop everything and delete downloaded songs. */
    fun release() {
        room = null
        if (::appContext.isInitialized) OtherAppsDucker.setDucked(appContext, false)
        resyncJob?.cancel()
        duckReleaseJob?.cancel()
        fadeJob?.cancel()
        player?.release()
        player = null
        loadedId = null
        queue.clear()
        current = null
        djSongs.clear()
        broadcastIds.clear()
        broadcastsInFlight.values.forEach { it.cancel() }
        broadcastsInFlight.clear()
        lastNow = null
        lastNowFrom = null
        savers.clear()
        songFiles.clear()
        downloading.clear()
        requested.clear()
        if (::appContext.isInitialized) musicDir.deleteRecursively()
        _state.update { MusicState(volume = it.volume) }
    }

    // ---- Ducking ----

    private var voicesActive = false
    private var announcing = false

    private var emergency = false

    init {
        scope.launch { DataSaver.active.drop(1).collect { onDataSaverChanged(it) } }
        scope.launch {
            Announcer.speaking.collect {
                announcing = it
                updateDucking()
            }
        }
        scope.launch {
            // An incoming SOS silences the music completely.
            SosManager.alarming.collect {
                emergency = it
                applyVolume(fadeMs = 0)
            }
        }
    }

    fun onSpeakersChanged(speakers: List<Participant>) {
        voicesActive = speakers.isNotEmpty()
        updateDucking()
    }

    /** Music goes down while anyone talks or an announcement plays, and comes back shortly after. */
    private fun updateDucking() {
        if (voicesActive || announcing) {
            duckReleaseJob?.cancel()
            duckReleaseJob = null
            setDucked(true)
        } else if (_state.value.ducked && duckReleaseJob == null) {
            duckReleaseJob = scope.launch {
                delay(DUCK_RELEASE_MS)
                duckReleaseJob = null
                setDucked(false)
            }
        }
    }

    private fun setDucked(ducked: Boolean) {
        if (_state.value.ducked == ducked) return
        _state.update { it.copy(ducked = ducked) }
        applyVolume()
        // Spotify, YouTube Music etc. playing alongside the ride go down too.
        if (::appContext.isInitialized && Prefs.keepOtherMusic(appContext)) OtherAppsDucker.setDucked(appContext, ducked)
    }

    fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        applyVolume(fadeMs = 0)
    }

    fun toggleOffForMe() {
        _state.update { it.copy(offForMe = !it.offForMe) }
        applyVolume(fadeMs = 0)
    }

    private fun targetVolume(): Float {
        val s = _state.value
        if (s.offForMe || emergency) return 0f
        return s.volume * if (s.ducked) DUCK_LEVEL else 1f
    }

    private fun applyVolume(fadeMs: Long? = null) {
        val p = player ?: return
        val target = targetVolume()
        val duration = fadeMs ?: if (target < p.volume) FADE_DOWN_MS else FADE_UP_MS
        fadeJob?.cancel()
        if (duration <= 0) {
            p.volume = target
            return
        }
        fadeJob = scope.launch {
            val start = p.volume
            val steps = (duration / 20).coerceAtLeast(1)
            for (i in 1..steps) {
                p.volume = start + (target - start) * i / steps
                delay(20)
            }
        }
    }

    // ---- DJ controls ----

    /** Adds songs picked from the phone; starts playing (and becomes DJ) if nothing is on. Returns how many were skipped. */
    fun addSongs(context: Context, uris: List<Uri>): Int {
        appContext = context.applicationContext
        val songs = uris.mapNotNull { describe(it) }
        if (songs.isEmpty()) return uris.size
        if (!_state.value.iAmDj) becomeDj()
        songs.forEach { djSongs[it.id] = it }
        queue.addAll(songs)
        if (current == null) playNext() else {
            _state.update { it.copy(queued = queue.size) }
            scope.launch { prefetchNext() }
        }
        return uris.size - songs.size
    }

    fun playPause() {
        val p = player ?: return
        if (!_state.value.iAmDj) return
        if (p.isPlaying) p.pause() else p.play()
        _state.update { it.copy(playing = p.playWhenReady) }
        scope.launch { broadcastNow() }
    }

    fun next() {
        if (_state.value.iAmDj) playNext()
    }

    fun stopMusic() {
        if (!_state.value.iAmDj) return
        queue.clear()
        current = null
        resyncJob?.cancel()
        stopPlayer()
        _state.update { MusicState(volume = it.volume, offForMe = it.offForMe, ducked = it.ducked) }
        scope.launch { send(MusicMessage.Stop) }
    }

    private fun becomeDj() {
        lastNow = null
        stopPlayer()
        _state.update { it.copy(iAmDj = true, djName = myName()) }
    }

    private fun playNext() {
        val song = queue.removeFirstOrNull() ?: return stopMusic()
        current = song
        _state.update {
            it.copy(title = song.title, djName = myName(), playing = false, queued = queue.size, status = "Sending song to riders…")
        }
        scope.launch {
            sendSong(song, to = emptyList())
            if (current !== song) return@launch
            load(song.id, MediaItem.fromUri(song.uri), positionMs = 0, playing = true)
            _state.update { it.copy(playing = true, status = null) }
            broadcastNow()
            startResync()
            prefetchNext()
        }
    }

    private suspend fun prefetchNext() {
        queue.firstOrNull()?.let { sendSong(it, to = emptyList()) }
    }

    /** Sends a song file. Broadcasts go out once; [to] targets riders who missed it. */
    private suspend fun sendSong(song: Song, to: List<Participant.Identity>) {
        if (to.isNotEmpty()) return streamSong(song, to)
        if (song.id in broadcastIds) return
        // A prefetch may already be sending this song; wait for it instead of sending twice.
        broadcastsInFlight.getOrPut(song.id) {
            scope.async { streamSong(song, emptyList()) }.also { job ->
                job.invokeOnCompletion { broadcastsInFlight.remove(song.id) }
            }
        }.await()
    }

    private suspend fun streamSong(song: Song, to: List<Participant.Identity>) {
        val r = room ?: return
        // Riders saving data are left out (they ask for the song once they stop saving), and so is
        // family watching the map from home.
        val watched = r.remoteParticipants.keys.any { Riders.isWatcher(it.value) }
        val destinations = if (to.isEmpty() && (savers.isNotEmpty() || watched)) {
            val wanted = r.riderIds().filter { it !in savers }.map { Participant.Identity(it) }
            if (wanted.isEmpty()) {
                broadcastIds += song.id
                return
            }
            wanted
        } else {
            to
        }
        try {
            val input = withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(song.uri) }
                ?: return
            input.use { stream ->
                val sender = r.localParticipant.streamBytes(
                    StreamBytesOptions(
                        topic = FILE_TOPIC,
                        attributes = mapOf(ATTR_ID to song.id, ATTR_TITLE to song.title),
                        destinationIdentities = destinations,
                        mimeType = "audio/*",
                        name = song.title,
                        totalSize = song.size,
                    ),
                )
                val result = sender.write(stream)
                sender.close()
                if (result.isSuccess && to.isEmpty()) broadcastIds += song.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Riders who miss the file ask for it with a Need message.
        }
    }

    private fun startResync() {
        resyncJob?.cancel()
        resyncJob = scope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS)
                if (player?.isPlaying == true) broadcastNow()
            }
        }
    }

    private fun nowMessage(): MusicMessage.Now? {
        val song = current ?: return null
        val p = player ?: return null
        return MusicMessage.Now(
            songId = song.id,
            title = song.title,
            djName = myName(),
            playing = p.playWhenReady,
            positionMs = p.currentPosition,
            atMs = System.currentTimeMillis(),
        )
    }

    private suspend fun broadcastNow(to: List<Participant.Identity> = emptyList()) {
        nowMessage()?.let { send(it, to) }
    }

    // ---- Messages ----

    private suspend fun onMessage(message: MusicMessage, from: Participant.Identity) {
        when (message) {
            is MusicMessage.Now -> onNow(message, from)
            MusicMessage.Stop -> if (!_state.value.iAmDj) {
                lastNow = null
                stopPlayer()
                _state.update { MusicState(volume = it.volume, offForMe = it.offForMe, ducked = it.ducked) }
            }
            MusicMessage.Sync -> if (_state.value.iAmDj) broadcastNow(to = listOf(from))
            is MusicMessage.Need -> if (_state.value.iAmDj) {
                djSongs[message.songId]?.let { sendSong(it, to = listOf(from)) }
            }
            is MusicMessage.Saving -> if (message.on) savers += from.value else savers -= from.value
        }
    }

    private suspend fun onNow(now: MusicMessage.Now, from: Participant.Identity) {
        if (_state.value.iAmDj) {
            // Someone else started music after us: last DJ wins.
            queue.clear()
            current = null
            resyncJob?.cancel()
            _state.update { it.copy(iAmDj = false, queued = 0) }
        }
        lastNow = now
        lastNowFrom = from
        _state.update { it.copy(title = now.title, djName = now.djName, playing = now.playing) }
        if (now.songId in songFiles) {
            syncToDj(now)
        } else if (DataSaver.active.value) {
            // Don't download songs while saving data; this catches up when saving ends.
            if (loadedId != now.songId) stopPlayer()
            _state.update { it.copy(status = "Music paused to save data") }
        } else {
            val askedAt = requested[now.songId]
            val t = System.currentTimeMillis()
            if (now.songId !in downloading && (askedAt == null || t - askedAt > NEED_RETRY_MS)) {
                requested[now.songId] = t
                send(MusicMessage.Need(now.songId), listOf(from))
            }
            if (loadedId != now.songId) stopPlayer()
            _state.update { it.copy(status = "Getting song…") }
        }
    }

    private fun syncToDj(now: MusicMessage.Now) {
        val file = songFiles[now.songId] ?: return
        val target = now.positionAt(System.currentTimeMillis())
        if (loadedId != now.songId) {
            load(now.songId, MediaItem.fromUri(Uri.fromFile(file)), target, now.playing)
        } else {
            val p = player ?: return
            if (abs(p.currentPosition - target) > MAX_DRIFT_MS) p.seekTo(target)
            p.playWhenReady = now.playing
        }
        _state.update { it.copy(status = null) }
    }

    private suspend fun receiveSong(reader: ByteStreamReceiver) {
        val id = reader.info.attributes[ATTR_ID] ?: return
        if (id in songFiles || !downloading.add(id)) return
        val total = reader.info.totalSize
        val dir = musicDir.apply { mkdirs() }
        val part = File(dir, "$id.part")
        try {
            withContext(Dispatchers.IO) {
                part.outputStream().use { out ->
                    var received = 0L
                    var lastPercent = -1
                    reader.flow.collect { chunk ->
                        out.write(chunk)
                        received += chunk.size
                        if (total != null && total > 0 && lastNow?.songId == id) {
                            val percent = (received * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.update { it.copy(status = "Getting song… $percent%") }
                            }
                        }
                    }
                }
            }
            val file = File(dir, id)
            part.renameTo(file)
            songFiles[id] = file
            requested.remove(id)
            trimSongFiles()
            lastNow?.takeIf { it.songId == id }?.let { syncToDj(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            part.delete()
            requested.remove(id)
        } finally {
            downloading.remove(id)
        }
    }

    /** Keeps only the last few songs on the phone. */
    private fun trimSongFiles() {
        while (songFiles.size > KEEP_SONG_FILES) {
            val oldest = songFiles.keys.first { it != loadedId && it != lastNow?.songId }
            songFiles.remove(oldest)?.delete()
        }
    }

    private suspend fun send(message: MusicMessage, to: List<Participant.Identity> = emptyList()) {
        val r = room ?: return
        // Best effort: a lost message is repaired by the DJ's periodic re-sync.
        r.trySendText(message.encode(), TEXT_TOPIC, to)
    }

    // ---- Player ----

    private fun load(id: String, item: MediaItem, positionMs: Long, playing: Boolean) {
        val p = player ?: createPlayer().also { player = it }
        loadedId = id
        p.setMediaItem(item, positionMs)
        p.prepare()
        p.playWhenReady = playing
        p.volume = targetVolume()
    }

    private fun stopPlayer() {
        player?.stop()
        player?.clearMediaItems()
        loadedId = null
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        // Play on the call audio path so music reaches the helmet headset together with voices.
        // LiveKit owns audio focus for the call, so the player must not request it.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_VOICE_COMMUNICATION)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false,
        )
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && _state.value.iAmDj) playNext()
                }
            })
        }

    // ---- Helpers ----

    private fun myName() = Prefs.riderName(appContext).ifBlank { "Rider" }

    private fun describe(uri: Uri): Song? {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var name: String? = null
        var size: Long? = null
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        if ((size ?: 0) > MAX_SONG_BYTES) return null
        val title = name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Song"
        return Song(UUID.randomUUID().toString(), title, uri, size)
    }
}
