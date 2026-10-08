package com.ridecomm.app.sos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R

/**
 * Emergency info on the lock screen: a quiet, always-there notification with my blood group,
 * allergies and who to call, readable without unlocking the phone, for a passer-by or medic.
 * Off unless switched on in Settings (anyone holding the phone can read it).
 */
object LockScreenInfo {
    private const val CHANNEL_ID = "emergency_info"
    private const val NOTIFICATION_ID = 7

    /** Shows, updates or removes the notification to match Settings. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = NotificationManagerCompat.from(app)
        val info = Prefs.emergencyInfo(app)
        if (!Prefs.lockScreenInfo(app) || info.isEmpty) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannel(app)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !manager.areNotificationsEnabled()) return
        val name = Prefs.riderName(app).ifBlank { "this rider" }
        val title = "In an emergency: $name"
        val text = lines(info).joinToString(" · ")
        val open = PendingIntent.getActivity(app, 7, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun build() = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines(info).joinToString("\n")))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        // The same text even when the phone hides notification details on the lock screen.
        val notification = build().setPublicVersion(build().build()).build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in between: nothing to show.
        }
    }

    /** "Blood group O+", "Allergic to penicillin", "Call Ammi: +91 98…". */
    fun lines(info: EmergencyInfo): List<String> = buildList {
        if (info.bloodGroup.isNotBlank()) add("Blood group ${info.bloodGroup.trim()}")
        if (info.medical.isNotBlank()) add(info.medical.trim())
        val contact = info.contactName.trim()
        val phone = info.contactPhone.trim()
        when {
            contact.isNotEmpty() && phone.isNotEmpty() -> add("Call $contact: $phone")
            phone.isNotEmpty() -> add("Call $phone")
            contact.isNotEmpty() -> add("Contact: $contact")
        }
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Emergency info on lock screen", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Puts the notification back after the phone restarts or the app updates. */
    class Restore : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) refresh(context)
        }
    }
}
