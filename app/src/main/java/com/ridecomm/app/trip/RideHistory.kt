package com.ridecomm.app.trip

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.safeMainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My past rides, kept only on this phone in its own small database (SQLite, part of Android).
 * Nothing here is sent anywhere; the rider can share a summary picture if they want.
 */
object RideHistory {
    private const val KEEP = 100

    private class Db(context: Context) : SQLiteOpenHelper(context, "rides.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE rides (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, started_at INTEGER, ended_at INTEGER, " +
                    "distance_m REAL, moving_ms INTEGER, top_kmh REAL, avg_kmh REAL, riders TEXT, route TEXT, stops TEXT)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    @Volatile private var db: Db? = null
    private fun db(context: Context) = db ?: synchronized(this) { db ?: Db(context.applicationContext).also { db = it } }

    private val _changes = MutableStateFlow(0)
    /** Goes up whenever the history changes, so screens showing it reload. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _justFinished = MutableStateFlow<Long?>(null)
    /** The ride that just ended (its summary opens on the home screen), until it's been shown. */
    val justFinished: StateFlow<Long?> = _justFinished.asStateFlow()

    fun shown() {
        _justFinished.value = null
    }

    /** Saves a new ride, or updates it when [RideSummary.id] is set. Returns its id. */
    fun save(context: Context, s: RideSummary): Long {
        val values = ContentValues().apply {
            put("code", s.code)
            put("started_at", s.startedAtMs)
            put("ended_at", s.endedAtMs)
            put("distance_m", s.distanceM)
            put("moving_ms", s.movingMs)
            put("top_kmh", s.topKmh)
            put("avg_kmh", s.averageKmh)
            put("riders", RideLog.namesJson(s.riders))
            put("route", RideLog.encode(s.route))
            put("stops", RideLog.stopsJson(s.stops))
        }
        val w = db(context).writableDatabase
        val id = if (s.id != 0L && w.update("rides", values, "id = ?", arrayOf(s.id.toString())) > 0) {
            s.id
        } else {
            w.insert("rides", null, values)
        }
        // Keep the newest rides only.
        w.execSQL("DELETE FROM rides WHERE id NOT IN (SELECT id FROM rides ORDER BY started_at DESC LIMIT $KEEP)")
        _changes.value++
        return id
    }

    fun list(context: Context, limit: Int = KEEP): List<RideSummary> =
        db(context).readableDatabase.query("rides", null, null, null, null, null, "started_at DESC", limit.toString()).use { c ->
            buildList { while (c.moveToNext()) add(c.toSummary()) }
        }

    fun get(context: Context, id: Long): RideSummary? =
        db(context).readableDatabase.query("rides", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toSummary() else null
        }

    fun count(context: Context): Int =
        runCatching { db(context).readableDatabase.rawQuery("SELECT COUNT(*) FROM rides", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
            .getOrDefault(0)

    fun delete(context: Context, id: Long) {
        db(context).writableDatabase.delete("rides", "id = ?", arrayOf(id.toString()))
        _changes.value++
    }

    fun clear(context: Context) {
        db(context).writableDatabase.delete("rides", null, null)
        _changes.value++
    }

    private fun Cursor.toSummary() = RideSummary(
        id = getLong(getColumnIndexOrThrow("id")),
        code = getString(getColumnIndexOrThrow("code")).orEmpty(),
        startedAtMs = getLong(getColumnIndexOrThrow("started_at")),
        endedAtMs = getLong(getColumnIndexOrThrow("ended_at")),
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        movingMs = getLong(getColumnIndexOrThrow("moving_ms")),
        topKmh = getFloat(getColumnIndexOrThrow("top_kmh")),
        averageKmh = getFloat(getColumnIndexOrThrow("avg_kmh")),
        riders = RideLog.namesFrom(getString(getColumnIndexOrThrow("riders"))),
        route = RideLog.decode(getString(getColumnIndexOrThrow("route")).orEmpty()),
        stops = RideLog.stopsFrom(getString(getColumnIndexOrThrow("stops"))),
    )

    // ---- Recording the ride that's going on ----

    private val scope = safeMainScope()
    private var appContext: Context? = null
    private var recorder: RouteRecorder? = null
    private var code = ""
    private var startedAtMs = 0L
    private var savedId = 0L
    private val riders = LinkedHashSet<String>()
    private var saver: Job? = null

    /** A ride started: record it if Ride history is on. Saved every minute, so little is lost if the app is closed. */
    fun begin(context: Context, rideCode: String) {
        finish(show = false)
        if (!Prefs.rideHistory(context)) return
        appContext = context.applicationContext
        recorder = RouteRecorder()
        code = rideCode
        startedAtMs = System.currentTimeMillis()
        savedId = 0
        riders.clear()
        saver = scope.launch {
            while (isActive) {
                delay(60_000)
                store()
            }
        }
    }

    fun onFix(location: Location) {
        recorder?.add(location.latitude, location.longitude, System.currentTimeMillis(), if (location.hasAccuracy()) location.accuracy else 50f)
    }

    /** Everyone seen in the ride, for "Rode with Amit, Rahul". */
    fun noteRiders(names: Collection<String>) {
        if (recorder != null) riders.addAll(names.filter { it.isNotBlank() })
    }

    /** The ride ended: keep it (if it's long enough) and show its summary on the home screen. */
    fun finish(show: Boolean = true) {
        saver?.cancel()
        saver = null
        if (recorder == null) return
        val id = store()
        recorder = null
        if (show && id != null) _justFinished.value = id
    }

    private fun summaryNow(): RideSummary? {
        val r = recorder ?: return null
        val trip = TripTracker.state.value
        return RideSummary(
            id = savedId,
            code = code,
            startedAtMs = startedAtMs,
            endedAtMs = System.currentTimeMillis(),
            distanceM = trip.distanceM,
            movingMs = trip.movingMs,
            topKmh = trip.topKmh,
            averageKmh = trip.averageKmh,
            riders = riders.toList(),
            route = r.route,
            stops = r.stops(),
        )
    }

    private fun store(): Long? {
        val context = appContext ?: return null
        val s = summaryNow()?.takeIf { it.worthKeeping } ?: return null
        return runCatching { save(context, s) }.getOrNull()?.also { savedId = it }
    }

    /** For screens: loads off the main thread. */
    suspend fun listAsync(context: Context, limit: Int = KEEP) = withContext(Dispatchers.IO) { runCatching { list(context, limit) }.getOrDefault(emptyList()) }

    suspend fun getAsync(context: Context, id: Long) = withContext(Dispatchers.IO) { runCatching { get(context, id) }.getOrNull() }
}
