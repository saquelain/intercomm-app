package com.ridecomm.app.ride

import android.util.Log
import io.livekit.android.room.Room
import io.livekit.android.room.datastream.StreamTextOptions
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus

/** Logs a failed background task instead of crashing the app (rides must keep going). */
private val logFailures = CoroutineExceptionHandler { _, e -> Log.e("RideComm", "Background task failed", e) }

/** Main-thread scope whose failed tasks are logged, not fatal. */
fun safeMainScope(): CoroutineScope = MainScope() + logFailures

/**
 * Sends a small text message to the ride. Returns false instead of throwing when it can't be
 * sent: LiveKit's sendText throws (rather than returning a failed Result) when the connection
 * isn't open yet or has just dropped.
 */
suspend fun Room.trySendText(text: String, topic: String, to: List<Participant.Identity> = emptyList()): Boolean =
    try {
        localParticipant.sendText(text, StreamTextOptions(topic = topic, destinationIdentities = to)).isSuccess
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("RideComm", "Couldn't send on $topic", e)
        false
    }
