package com.ridecomm.app.score

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.group.RideRoles
import com.ridecomm.app.ride.riderIds
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.ride.trySendText
import com.ridecomm.app.trip.RideLog
import com.ridecomm.app.trip.TripTracker
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A rider's points as shown on the ride's leaderboard. */
data class RiderScore(val id: String, val name: String, val level: Int, val ride: Int, val year: Int, val isMe: Boolean = false)

data class RideScoreState(
    /** Points & badges is on and a ride is going. */
    val active: Boolean = false,
    /** Me first, then the riders present who share their points. */
    val board: List<RiderScore> = emptyList(),
)

/**
 * Points & badges: each ride earns points for riding well and looking after the group (never for
 * speed), kept in a score book on this phone. During a ride, riders see each other's ride points.
 */
object Score {
    private const val TOPIC = "rc-score"
    private const val FILE = "score.json"
    private const val KEEP = 1_000
    private const val TICK_MS = 15_000L
    private const val SEND_EVERY_MS = 120_000L
    /** "I'm home safe" after leaving counts for a ride started up to this long ago. */
    private const val HOME_LATER_MS = 24 * 3_600_000L

    private val scope = safeMainScope()

    // ---- The score book ----

    private var book: List<ScoreEntry>? = null
    private val _entries = MutableStateFlow<List<ScoreEntry>>(emptyList())
    /** Every ride's entry, newest first (loaded on first use). */
    val entries: StateFlow<List<ScoreEntry>> = _entries.asStateFlow()

    fun enabled(context: Context) = Prefs.points(context)

    /** Loads the book once; cheap afterwards. */
    fun load(context: Context): List<ScoreEntry> {
        book?.let { return it }
        val list = runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()
            ?.let(ScoreEntry::listFromJson).orEmpty().sortedByDescending { it.atMs }
        book = list
        _entries.value = list
        return list
    }

    private fun write(context: Context, list: List<ScoreEntry>) {
        val kept = list.sortedByDescending { it.atMs }.take(KEEP)
        book = kept
        _entries.value = kept
        runCatching {
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(ScoreEntry.listToJson(kept))
            tmp.renameTo(f)
        }
    }

    private fun upsert(context: Context, e: ScoreEntry) = write(context, listOf(e) + load(context).filter { it.id != e.id })

    fun reset(context: Context) = write(context, emptyList())

    // ---- The ride going on ----

    private lateinit var appContext: Context
    private var ride: ScoreEntry? = null
    private val names = LinkedHashSet<String>()
    private var breaks = BreakCounter()
    private var ticker: Job? = null
    private var room: Room? = null
    private var lastSentRide = -1
    private var lastSentMs = 0L
    private val peers = linkedMapOf<String, RiderScore>()

    private val _state = MutableStateFlow(RideScoreState())
    val state: StateFlow<RideScoreState> = _state.asStateFlow()

    private fun myId() = Prefs.deviceId(appContext)
    private fun myName() = Prefs.riderName(appContext).ifBlank { "Rider" }

    fun begin(context: Context) {
        finish(announce = false)
        appContext = context.applicationContext
        if (!enabled(appContext)) return
        load(appContext)
        ride = ScoreEntry(id = UUID.randomUUID().toString(), atMs = System.currentTimeMillis(), km = 0.0, movingMin = 0)
        names.clear()
        breaks = BreakCounter()
        lastSentRide = -1
        peers.clear()
        var saved = 0L
        ticker = scope.launch {
            while (isActive) {
                tick()
                if (System.currentTimeMillis() - saved >= 60_000) {
                    store()
                    saved = System.currentTimeMillis()
                }
                delay(TICK_MS)
            }
        }
        scope.launch {
            // Lead or sweep at any point of the ride counts.
            RideRoles.state.collect { roles ->
                val me = myId()
                ride?.let { if (roles.leadId == me && !it.lead) ride = it.copy(lead = true) }
                ride?.let { if (roles.sweepId == me && !it.sweep) ride = it.copy(sweep = true) }
            }
        }.also { roleJob = it }
        publish()
    }

    private var roleJob: Job? = null

    /** My ride so far, with the latest distance and riding time. */
    private fun current(): ScoreEntry? {
        val r = ride ?: return null
        val trip = TripTracker.state.value
        return r.copy(
            km = maxOf(r.km, trip.distanceM / 1000),
            movingMin = maxOf(r.movingMin, (trip.movingMs / 60_000).toInt()),
            others = names.size,
            names = names.take(8),
            breaks = breaks.count,
        ).also { ride = it }
    }

    private fun tick() {
        val trip = TripTracker.state.value
        if (trip.active) breaks.update(System.currentTimeMillis(), trip.movingMs)
        current()
        publish()
        val now = System.currentTimeMillis()
        val pts = myRidePoints()
        if (pts != lastSentRide && now - lastSentMs >= SEND_EVERY_MS) send(emptyList())
    }

    private fun store() {
        val e = current() ?: return
        if (!worthKeeping(e)) return
        runCatching { upsert(appContext, e) }
    }

    /** Same rule as the ride history: 300 m or 5 minutes. */
    private fun worthKeeping(e: ScoreEntry) =
        e.km * 1000 >= RideLog.MIN_DISTANCE_M || System.currentTimeMillis() - e.atMs >= RideLog.MIN_DURATION_MS

    /** Everyone seen in the ride counts for "Rode with 3 riders". */
    fun noteRiders(seen: Collection<String>) {
        if (ride != null) names.addAll(seen.filter { it.isNotBlank() })
    }

    /** I marked a new hazard for the group. */
    fun noteHazard() {
        val r = ride ?: return
        ride = r.copy(hazards = r.hazards + 1)
        publish()
    }

    /** I said "I'm home safe": counts for this ride, or my last ride if it started within a day. */
    fun noteHome(context: Context) {
        val app = context.applicationContext
        if (!enabled(app)) return
        val r = ride
        if (r != null) {
            ride = r.copy(home = true)
            publish()
            return
        }
        val last = load(app).firstOrNull() ?: return
        if (last.home || System.currentTimeMillis() - last.atMs > HOME_LATER_MS) return
        upsert(app, last.copy(home = true))
    }

    /** The ride ended: keep its entry and say any new badge. */
    fun finish(announce: Boolean = true) {
        ticker?.cancel()
        ticker = null
        roleJob?.cancel()
        roleJob = null
        val e = current()
        ride = null
        room = null
        peers.clear()
        _state.value = RideScoreState()
        if (e == null || !::appContext.isInitialized || !worthKeeping(e)) return
        val before = load(appContext)
        runCatching { upsert(appContext, e) }
        val earned = ScoreBook.newBadges(before.filter { it.id != e.id }, load(appContext))
        if (earned.isNotEmpty() && announce) {
            Announcer.speak(appContext, if (earned.size == 1) "New badge: ${earned[0].title}" else "${earned.size} new badges")
        }
    }

    private fun myRidePoints() = current()?.let(ScoreRules::points) ?: 0

    /** Points this year from finished rides, plus this one. */
    private fun myYearPoints(): Int {
        val year = ScoreBook.yearOf(System.currentTimeMillis())
        val id = ride?.id
        return load(appContext).filter { it.id != id && ScoreBook.yearOf(it.atMs) == year }.sumOf(ScoreRules::points) + myRidePoints()
    }

    private fun myLevel(): Int {
        val id = ride?.id
        return Levels.of(load(appContext).filter { it.id != id }.sumOf(ScoreRules::points) + myRidePoints()).index
    }

    // ---- Sharing points in the ride ----

    fun attach(context: Context, r: Room) {
        appContext = context.applicationContext
        room = r
        r.registerTextStreamHandler(TOPIC) { reader, from ->
            scope.launch {
                val text = runCatching { reader.readAll().joinToString("") }.getOrNull() ?: return@launch
                runCatching { onMessage(JSONObject(text), from.value) }
            }
        }
    }

    fun detach() {
        room = null
    }

    /** Joined, or back after a drop: tell everyone my points. */
    fun onConnected() = send(emptyList())

    fun onRiderJoined(identity: Participant.Identity) = send(listOf(identity))

    fun forget(identity: String) {
        if (peers.remove(identity) != null) publish()
    }

    /** Points & badges switched on or off in Settings mid-ride: off stops sending and showing. */
    fun applySettings(context: Context) {
        if (!enabled(context)) {
            ride = null
            ticker?.cancel()
            ticker = null
            peers.clear()
            _state.value = RideScoreState()
        }
    }

    private fun send(to: List<Participant.Identity>) {
        val r = room ?: return
        if (ride == null) return
        val pts = myRidePoints()
        val o = JSONObject().put("t", "score").put("name", myName()).put("level", myLevel()).put("ride", pts).put("year", myYearPoints())
        lastSentRide = pts
        lastSentMs = System.currentTimeMillis()
        scope.launch { r.trySendText(o.toString(), TOPIC, to) }
    }

    private fun onMessage(o: JSONObject, from: String) {
        if (o.optString("t") != "score" || ride == null) return
        peers[from] = RiderScore(
            id = from,
            name = o.optString("name").ifBlank { "Rider" }.take(40),
            level = o.optInt("level", 0).coerceIn(0, Levels.all.lastIndex),
            ride = o.optInt("ride", 0).coerceAtLeast(0),
            year = o.optInt("year", 0).coerceAtLeast(0),
        )
        publish()
    }

    private fun publish() {
        if (ride == null || !::appContext.isInitialized) {
            _state.value = RideScoreState()
            return
        }
        val present = room?.riderIds()?.toSet()
        val others = peers.values.filter { present == null || it.id in present }
        val me = RiderScore(myId(), myName(), myLevel(), myRidePoints(), myYearPoints(), isMe = true)
        _state.value = RideScoreState(active = true, board = (others + me).sortedWith(compareByDescending<RiderScore> { it.ride }.thenBy { !it.isMe }))
    }
}
