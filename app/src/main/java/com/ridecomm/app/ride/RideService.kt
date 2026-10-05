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
import com.ridecomm.app.MainActivity
import com.ridecomm.app.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the ride (mic + audio) running with the screen off or while
 * other apps are open. The ride itself lives in [RideManager]; this only mirrors its state
 * into the ongoing notification and stops when the ride ends.
 */
class RideService : Service() {

    private val scope = MainScope()
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(RideManager.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideComm:ride")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

        scope.launch {
            RideManager.state.collect { state ->
                if (state.status == RideStatus.IDLE) {
                    // Only the latest start may stop us, so a quick leave-then-rejoin isn't killed.
                    stopSelfResult(lastStartId)
                } else if (NotificationManagerCompat.from(this@RideService).areNotificationsEnabled()) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_TOGGLE_MUTE -> RideManager.toggleMute()
            ACTION_LEAVE -> RideManager.leave()
        }
        // The ride may have ended before this start was delivered.
        if (RideManager.state.value.status == RideStatus.IDLE) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: RideState): android.app.Notification {
        val title = when (state.status) {
            RideStatus.CONNECTING -> "Joining ride ${state.code}…"
            RideStatus.RECONNECTING -> "Reconnecting to ride ${state.code}…"
            else -> "On ride ${state.code}"
        }
        val text = buildString {
            append(if (state.micMuted) "Mic off" else "Mic on")
            if (state.riders.isNotEmpty()) append(" · ${state.riders.size} riders")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(0, if (state.micMuted) "Unmute" else "Mute", actionIntent(ACTION_TOGGLE_MUTE, 1))
            .addAction(0, "Leave ride", actionIntent(ACTION_LEAVE, 2))
            .build()
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
