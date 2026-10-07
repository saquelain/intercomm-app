package com.ridecomm.app.ride

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ridecomm.app.Announcer
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.overlay.AppVisibility
import com.ridecomm.app.overlay.BubbleOverlay
import com.ridecomm.app.sos.LocationHelper
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.sos.SosOverlay
import com.ridecomm.app.vote.VoteManager
import com.ridecomm.app.vote.VoteState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the ride (mic + audio) running with the screen off or while
 * other apps are open. The ride itself lives in [RideManager]; this only mirrors its state
 * into the ongoing notification and stops when the ride ends.
 */
class RideService : Service() {

    private val scope = safeMainScope()
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0
    private lateinit var bubble: BubbleOverlay
    private lateinit var sosOverlay: SosOverlay

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(RideManager.state.value, VoteManager.state.value),
            foregroundTypes(),
        )
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideComm:ride")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        bubble = BubbleOverlay(this)
        sosOverlay = SosOverlay(this)
        scope.launch {
            combine(SosManager.state, AppVisibility.inForeground) { sos, appVisible -> sos to appVisible }
                .collect { (sos, appVisible) -> sosOverlay.update(sos, appVisible) }
        }
        scope.launch {
            // Floating button: only during a ride, only while RideComm itself isn't on screen.
            combine(RideManager.state, AppVisibility.inForeground, VoteManager.state) { ride, appVisible, vote ->
                Triple(ride, appVisible, vote)
            }.collect { (ride, appVisible, vote) ->
                val wanted = ride.status != RideStatus.IDLE && !appVisible && Prefs.bubbleEnabled(this@RideService)
                if (wanted) bubble.show() else bubble.hide()
                bubble.setMuted(ride.micMuted)
                bubble.setVotePending(vote.needsMyVote)
            }
        }
        scope.launch {
            combine(RideManager.state, VoteManager.state) { ride, vote -> ride to vote }.collect { (ride, vote) ->
                if (ride.status == RideStatus.IDLE) {
                    // Only the latest start may stop us, so a quick leave-then-rejoin isn't killed.
                    stopSelfResult(lastStartId)
                } else if (NotificationManagerCompat.from(this@RideService).areNotificationsEnabled()) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(ride, vote))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_TOGGLE_MUTE -> RideManager.toggleMute()
            ACTION_LEAVE -> RideManager.leave()
            ACTION_VOTE_YES -> VoteManager.cast(yes = true)
            ACTION_VOTE_NO -> VoteManager.cast(yes = false)
        }
        // The ride may have ended before this start was delivered.
        if (RideManager.state.value.status == RideStatus.IDLE) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        bubble.hide()
        sosOverlay.hide()
        Announcer.shutdown()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Microphone for the call; location too (if allowed) so an SOS can get a GPS fix from the background. */
    private fun foregroundTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (LocationHelper.hasPermission(this)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return types
    }

    private fun buildNotification(state: RideState, vote: VoteState): android.app.Notification {
        val pending = vote.active?.takeIf { vote.needsMyVote }
        val title = when {
            pending != null -> "${pending.starterName} ${pending.kind.asking}"
            state.status == RideStatus.CONNECTING -> "Joining ride ${state.code}…"
            state.status == RideStatus.RECONNECTING -> "Reconnecting to ride ${state.code}…"
            else -> "On ride ${state.code}"
        }
        val text = buildString {
            if (pending != null) append("Vote now · ")
            append(if (state.micMuted) "Mic off" else "Mic on")
            if (state.riders.isNotEmpty()) append(" · ${state.riders.size} riders")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
        if (pending != null) {
            builder
                .addAction(0, "Yes", actionIntent(ACTION_VOTE_YES, 3))
                .addAction(0, "No", actionIntent(ACTION_VOTE_NO, 4))
        }
        builder.addAction(0, if (state.micMuted) "Unmute" else "Mute", actionIntent(ACTION_TOGGLE_MUTE, 1))
        if (pending == null) builder.addAction(0, "Leave ride", actionIntent(ACTION_LEAVE, 2))
        return builder.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, RideService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "ride"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE_MUTE = "com.ridecomm.app.TOGGLE_MUTE"
        private const val ACTION_LEAVE = "com.ridecomm.app.LEAVE"
        private const val ACTION_VOTE_YES = "com.ridecomm.app.VOTE_YES"
        private const val ACTION_VOTE_NO = "com.ridecomm.app.VOTE_NO"
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L // longer than any day ride

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Active ride", NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
        }
    }
}
