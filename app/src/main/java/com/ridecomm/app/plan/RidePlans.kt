package com.ridecomm.app.plan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.group.GroupTracker
import com.ridecomm.app.ride.InviteLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Upcoming rides planned ahead, kept on this phone (made here or opened from an invite link), with
 * a reminder notification an hour before and at the start.
 */
object RidePlans {
    private const val CHANNEL_ID = "plans"
    private const val EXTRA_CODE = "code"
    private const val EXTRA_SOON = "soon"

    private val _plans = MutableStateFlow<List<RidePlan>>(emptyList())
    val plans: StateFlow<List<RidePlan>> = _plans.asStateFlow()

    fun load(context: Context) {
        val all = PlanCodec.listFrom(Prefs.plansJson(context))
        _plans.value = PlanCodec.merge(all, null, System.currentTimeMillis())
    }

    fun forCode(code: String): RidePlan? = _plans.value.firstOrNull { it.code == code }

    /** Keeps a plan (replacing one for the same ride) and sets its reminders. */
    fun save(context: Context, plan: RidePlan) {
        val all = PlanCodec.merge(PlanCodec.listFrom(Prefs.plansJson(context)), plan, System.currentTimeMillis())
        Prefs.setPlansJson(context, PlanCodec.listToJson(all))
        _plans.value = all
        schedule(context, plan)
    }

    fun delete(context: Context, code: String) {
        forCode(code)?.let { cancel(context, it) }
        val all = PlanCodec.listFrom(Prefs.plansJson(context)).filter { it.code != code }
        Prefs.setPlansJson(context, PlanCodec.listToJson(all))
        _plans.value = PlanCodec.merge(all, null, System.currentTimeMillis())
    }

    /** A plan from an invite link (`p=` after the code). */
    fun fromLink(context: Context, code: String, encoded: String) {
        if (!Prefs.planner(context)) return
        PlanCodec.decode(encoded, code)?.let { save(context, it) }
    }

    /** The planner switch changed: reminders on or off. */
    fun applySettings(context: Context) {
        val on = Prefs.planner(context)
        _plans.value.forEach { if (on) schedule(context, it) else cancel(context, it) }
    }

    // ---- Sharing ----

    fun link(context: Context, plan: RidePlan): String =
        InviteLink.url(plan.code, InviteLink.groupKeyToShare(context), PlanCodec.encode(plan))

    fun shareText(context: Context, plan: RidePlan): String =
        "${plan.label} · ${whenText(plan.atMs)}\nMeet at ${plan.meet.name}\n" +
            (if (plan.stops.isNotEmpty()) "Stops: ${plan.stops.joinToString(", ") { it.name }}\n" else "") +
            (plan.dest?.let { "Riding to ${it.name}\n" } ?: "") +
            "Plan and join: ${link(context, plan)}"

    /** Opens the phone's calendar with the ride filled in (no permission needed; the rider saves it). */
    fun calendarIntent(context: Context, plan: RidePlan): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, plan.atMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, plan.atMs + 3 * 60 * 60_000L)
        .putExtra(CalendarContract.Events.TITLE, plan.label)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, plan.meet.name)
        .putExtra(CalendarContract.Events.DESCRIPTION, shareText(context, plan))

    /** "Sun 12 Oct, 6:00 am". */
    fun whenText(atMs: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(atMs))

    // ---- Reminders ----

    private fun pending(context: Context, plan: RidePlan, soon: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        (plan.code + soon).hashCode(),
        Intent(context, Reminder::class.java).putExtra(EXTRA_CODE, plan.code).putExtra(EXTRA_SOON, soon),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, plan: RidePlan) {
        cancel(context, plan)
        if (!Prefs.planner(context)) return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        PlanCodec.reminderTimes(plan, now).forEach { at ->
            // Within a few minutes is fine for a ride reminder, so no exact-alarm permission is needed.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, plan, soon = at < plan.atMs)) }
        }
    }

    private fun cancel(context: Context, plan: RidePlan) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context, plan, soon = true))
        alarms.cancel(pending(context, plan, soon = false))
    }

    private fun notify(context: Context, code: String, soon: Boolean) {
        load(context)
        val plan = forCode(code) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ride reminders", NotificationManager.IMPORTANCE_HIGH))
        // Tapping it opens the app with the ride ready to join.
        val join = PendingIntent.getActivity(
            context,
            code.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("ridecomm://join/$code"), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val navigate = PendingIntent.getActivity(
            context,
            code.hashCode() + 1,
            Intent(Intent.ACTION_VIEW, Uri.parse(GroupTracker.directionsLink(plan.meet.lat, plan.meet.lon))),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(plan.atMs))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (soon) "Ride in an hour: ${plan.label}" else "Time to ride: ${plan.label}")
            .setContentText("Meet at ${plan.meet.name} at $time. Tap to join the ride.")
            .setContentIntent(join)
            .addAction(R.drawable.ms_navigation, "Navigate", navigate)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { manager.notify(code.hashCode(), notification) }
    }

    /** Shows a reminder when it's time, and sets the reminders again after a restart or update. */
    class Reminder : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    load(context)
                    applySettings(context)
                }
                else -> {
                    val code = intent.getStringExtra(EXTRA_CODE) ?: return
                    if (Prefs.planner(context)) notify(context, code, intent.getBooleanExtra(EXTRA_SOON, false))
                }
            }
        }
    }
}
