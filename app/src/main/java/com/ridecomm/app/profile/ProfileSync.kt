package com.ridecomm.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.ridecomm.app.ride.safeMainScope
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamBytesOptions
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Shares profile photos within a ride: each phone sends its small photo to everyone when it
 * joins, and to each rider who joins later. Received photos are kept in memory by rider identity.
 */
object ProfileSync {
    private const val TOPIC = "rc-avatar"
    /** Safety limit; real photos are a few KB. */
    private const val MAX_BYTES = 100_000

    private val scope = safeMainScope()
    private val _photos = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    /** Rider identity → photo. */
    val photos: StateFlow<Map<String, Bitmap>> = _photos.asStateFlow()

    private lateinit var appContext: Context
    private var room: Room? = null

    init {
        // A new photo picked during a ride goes out right away.
        scope.launch { Profile.photo.drop(1).collect { sendMine(to = emptyList()) } }
    }

    fun attach(context: Context, r: Room) {
        appContext = context.applicationContext
        room = r
        r.registerByteStreamHandler(TOPIC) { reader, from ->
            scope.launch {
                val bytes = runCatching { reader.readAll().fold(ByteArray(0)) { acc, chunk -> acc + chunk } }.getOrNull()
                    ?: return@launch
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@launch
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                _photos.value = _photos.value + (from.value to bitmap)
            }
        }
    }

    /** Joined the ride: send my photo to everyone already there. */
    fun onConnected() {
        scope.launch { sendMine(to = emptyList()) }
    }

    /** Someone joined after me: send them my photo. */
    fun onRiderJoined(identity: Participant.Identity) {
        scope.launch { sendMine(to = listOf(identity)) }
    }

    fun detach() {
        room = null
    }

    fun release() {
        room = null
        _photos.value = emptyMap()
    }

    private suspend fun sendMine(to: List<Participant.Identity>) {
        val r = room ?: return
        if (!::appContext.isInitialized) return
        val bytes = Profile.photoBytes(appContext) ?: return
        try {
            val sender = r.localParticipant.streamBytes(
                StreamBytesOptions(topic = TOPIC, destinationIdentities = to, mimeType = "image/jpeg", totalSize = bytes.size.toLong()),
            )
            sender.write(bytes).onFailure { Log.w("RideComm", "Couldn't send profile photo", it) }
            sender.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("RideComm", "Couldn't send profile photo", e)
        }
    }
}
