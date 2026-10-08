package com.ridecomm.app.ride

import android.content.Context
import com.ridecomm.app.Prefs
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import io.livekit.android.token.TokenSourceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A problem getting into a ride, worded for the rider. */
class RidePassError(message: String) : Exception(message)

/**
 * Gets the pass (LiveKit access token) for a ride: from the group's private ride server when one
 * is set up (it checks the group key), otherwise from LiveKit Cloud's development token server.
 */
object RidePass {
    private const val TIMEOUT_MS = 15_000

    /** [identity] is normally this phone's id; a quick "home safe" check-in uses a second one. */
    suspend fun fetch(context: Context, code: String, identity: String = Prefs.deviceId(context)): TokenSourceResponse {
        val room = "ride-$code"
        val name = Prefs.riderName(context).ifBlank { "Rider" }
        val server = Prefs.activeRideServer(context)
        if (server.isNotBlank()) return fromPrivateServer(server, Prefs.groupKey(context), room, name, identity)

        val tokenServerId = Prefs.tokenServerId(context)
        if (tokenServerId.isBlank()) throw RidePassError("Set up the ride server in Settings first")
        return TokenSource.fromDevelopmentTokenServer(tokenServerId)
            .fetch(TokenRequestOptions(roomName = room, participantName = name, participantIdentity = identity))
            .getOrElse { throw RidePassError("Could not reach the ride server. Check internet and the token server ID.") }
    }

    private suspend fun fromPrivateServer(
        server: String,
        groupKey: String,
        room: String,
        name: String,
        identity: String,
    ): TokenSourceResponse = withContext(Dispatchers.IO) {
        if (groupKey.isBlank()) throw RidePassError("Enter your group key in Settings to join rides")
        val body = JSONObject()
            .put("room_name", room)
            .put("participant_name", name)
            .put("participant_identity", identity)
            .toString()
        val connection = try {
            (URL(server).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-RideComm-Key", groupKey)
                outputStream.use { it.write(body.toByteArray()) }
            }
        } catch (e: Exception) {
            throw RidePassError("Could not reach the ride server. Check your internet connection.")
        }
        try {
            when (val status = connection.responseCode) {
                200 -> {
                    val o = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    TokenSourceResponse(
                        serverUrl = o.getString("server_url"),
                        participantToken = o.getString("participant_token"),
                    )
                }
                401 -> throw RidePassError("Wrong group key. Check it in Settings.")
                else -> throw RidePassError("The ride server had a problem ($status). Try again in a moment.")
            }
        } catch (e: RidePassError) {
            throw e
        } catch (e: Exception) {
            throw RidePassError("Could not reach the ride server. Check your internet connection.")
        } finally {
            connection.disconnect()
        }
    }
}
