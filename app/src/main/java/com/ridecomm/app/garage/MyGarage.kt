package com.ridecomm.app.garage

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ridecomm.app.Announcer
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * My garage: my bikes with their odometer (kept up to date by my rides), service reminders by km or
 * months, and insurance / PUC / licence end dates with notifications. Everything stays on this phone.
 */
object MyGarage {
    private const val CHANNEL_ID = "garage"
    private const val EXTRA_KEY = "key"

    private val _state = MutableStateFlow(Garage())
    val state: StateFlow<Garage> = _state.asStateFlow()
    private var loaded = false

    fun load(context: Context): Garage {
        if (!loaded) {
            _state.value = GarageLogic.fromJson(Prefs.garageJson(context))
            loaded = true
        }
        return _state.value
    }

    /** Saves the garage and sets the document reminders again. */
    fun save(context: Context, g: Garage) {
        val old = load(context)
        _state.value = g
        Prefs.setGarageJson(context, GarageLogic.toJson(g))
        cancelAll(context, old)
        schedule(context, g)
    }

    fun addBike(context: Context, name: String, reg: String, odoKm: Double) {
        val bike = GarageLogic.newBike(name.ifBlank { "My bike" }, reg, odoKm, System.currentTimeMillis())
        val g = load(context)
        save(context, g.copy(bikes = g.bikes + bike, currentId = bike.id))
    }

    fun updateBike(context: Context, bike: Bike) = save(context, load(context).replace(bike))

    fun deleteBike(context: Context, id: String) {
        val g = load(context)
        val left = g.bikes.filter { it.id != id }
        save(context, g.copy(bikes = left, currentId = if (g.currentId == id) left.firstOrNull()?.id else g.currentId))
    }

    fun setCurrent(context: Context, id: String) = save(context, load(context).copy(currentId = id))

    fun setLicence(context: Context, untilMs: Long) = save(context, load(context).copy(licenceUntilMs = untilMs))

    /** A ride started: say the first thing that's due, once. */
    fun onRideStart(context: Context) {
        if (!Prefs.garage(context)) return
        GarageLogic.rideStartReminder(load(context), System.currentTimeMillis())?.let { Announcer.speak(context, it) }
    }

    /** A ride ended: its km go on the bike I'm riding. */
    fun onRideEnd(context: Context, km: Double) {
        if (!Prefs.garage(context)) return
        val g = load(context)
        val next = GarageLogic.addRide(g, km)
        if (next != g) save(context, next)
    }

    /** The switch changed: reminders on or off. */
    fun applySettings(context: Context) {
        val g = load(context)
        cancelAll(context, g)
        schedule(context, g)
    }

    // ---- Document reminders ----

    /** Every document with a date: (key, what it is, its end). */
    private fun docs(g: Garage): List<Triple<String, String, Long>> = buildList {
        g.bikes.forEach { b ->
            add(Triple("ins:${b.id}", "Insurance for ${b.name}", b.insuranceUntilMs))
            add(Triple("puc:${b.id}", "PUC for ${b.name}", b.pucUntilMs))
        }
        add(Triple("licence", "Your driving licence", g.licenceUntilMs))
    }

    private fun pending(context: Context, key: String, n: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        (key + n).hashCode(),
        Intent(context, Reminder::class.java).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, g: Garage) {
        if (!Prefs.garage(context)) return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        docs(g).forEach { (key, _, until) ->
            GarageLogic.reminderTimes(until, now).forEachIndexed { n, at ->
                runCatching { alarms.set(AlarmManager.RTC_WAKEUP, at, pending(context, key, n)) }
            }
        }
    }

    private fun cancelAll(context: Context, g: Garage) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        docs(g).forEach { (key, _, _) -> repeat(3) { alarms.cancel(pending(context, key, it)) } }
    }

    private fun notify(context: Context, key: String) {
        val g = load(context)
        val (_, what, until) = docs(g).firstOrNull { it.first == key } ?: return
        if (until <= 0) return
        val days = GarageLogic.daysLeft(until, System.currentTimeMillis())
        val text = when {
            days < 0 -> "$what has expired."
            days == 0 -> "$what ends today."
            days == 1 -> "$what ends tomorrow."
            else -> "$what ends in $days days, on ${GarageLogic.dateText(until)}."
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Garage reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, key.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Renew soon")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(key.hashCode(), notification) }
    }

    /** Shows a document reminder, and sets them again after a restart or update. */
    class Reminder : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> applySettings(context)
                else -> {
                    val key = intent.getStringExtra(EXTRA_KEY) ?: return
                    if (Prefs.garage(context)) notify(context, key)
                }
            }
        }
    }
}
