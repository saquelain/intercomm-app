package com.ridecomm.app.score

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One ride in the score book: plain facts, never points. Points are worked out from the facts with
 * [ScoreRules], so the rules can change and app and web always agree (PROTOCOL.md "Points & badges").
 */
data class ScoreEntry(
    val id: String,
    val atMs: Long,
    val km: Double,
    val movingMin: Int,
    val others: Int = 0,
    val names: List<String> = emptyList(),
    val breaks: Int = 0,
    val hazards: Int = 0,
    val lead: Boolean = false,
    val sweep: Boolean = false,
    val home: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("at", atMs)
        .put("km", (km * 10).roundToInt() / 10.0)
        .put("min", movingMin)
        .put("others", others)
        .put("names", JSONArray(names))
        .put("breaks", breaks)
        .put("hz", hazards)
        .put("lead", lead)
        .put("sweep", sweep)
        .put("home", home)

    companion object {
        fun fromJson(o: JSONObject): ScoreEntry? {
            val id = o.optString("id").ifBlank { return null }
            val at = o.optLong("at", 0).takeIf { it > 0 } ?: return null
            val names = o.optJSONArray("names")
            return ScoreEntry(
                id = id,
                atMs = at,
                km = o.optDouble("km", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                movingMin = o.optInt("min", 0).coerceAtLeast(0),
                others = o.optInt("others", 0).coerceAtLeast(0),
                names = if (names == null) emptyList() else (0 until names.length()).mapNotNull { names.optString(it).takeIf(String::isNotBlank) },
                breaks = o.optInt("breaks", 0).coerceAtLeast(0),
                hazards = o.optInt("hz", 0).coerceAtLeast(0),
                lead = o.optBoolean("lead"),
                sweep = o.optBoolean("sweep"),
                home = o.optBoolean("home"),
            )
        }

        fun listToJson(entries: List<ScoreEntry>): String = JSONArray(entries.map { it.toJson() }).toString()

        fun listFromJson(text: String): List<ScoreEntry> {
            val a = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::fromJson) }
        }
    }
}

/** One line of "how this ride scored": "Rode with 3 riders" +30. */
data class PointsLine(val label: String, val points: Int)

/** What earns points. Never speed: riding well and looking after the group. */
object ScoreRules {
    const val GROUP_PER_RIDER = 10
    const val GROUP_MAX_RIDERS = 5
    const val BREAK_POINTS = 15
    const val BREAK_MAX = 3
    const val HAZARD_POINTS = 10
    const val HAZARD_MAX = 5
    const val ROLE_POINTS = 25
    const val HOME_POINTS = 15
    /** Group and role points need a real ride, not a chat in the car park. */
    const val MIN_GROUP_KM = 5.0
    /** Breaks only count on rides long enough to need one. */
    const val BREAKS_FROM_MIN = 60

    fun breakdown(e: ScoreEntry): List<PointsLine> = buildList {
        val km = floor(e.km).toInt()
        if (km > 0) add(PointsLine("${km} km ridden", km))
        val real = e.km >= MIN_GROUP_KM
        val others = min(e.others, GROUP_MAX_RIDERS)
        if (real && others > 0) add(PointsLine("Rode with $others ${if (others == 1) "rider" else "riders"}", others * GROUP_PER_RIDER))
        val breaks = min(e.breaks, BREAK_MAX)
        if (e.movingMin >= BREAKS_FROM_MIN && breaks > 0) add(PointsLine("Took $breaks ${if (breaks == 1) "break" else "breaks"}", breaks * BREAK_POINTS))
        val hz = min(e.hazards, HAZARD_MAX)
        if (hz > 0) add(PointsLine("Marked $hz ${if (hz == 1) "hazard" else "hazards"}", hz * HAZARD_POINTS))
        if (real && e.lead) add(PointsLine("Led the group", ROLE_POINTS))
        if (real && e.sweep) add(PointsLine("Rode sweep", ROLE_POINTS))
        if (e.home) add(PointsLine("Home safe", HOME_POINTS))
    }

    fun points(e: ScoreEntry): Int = breakdown(e).sumOf { it.points }

    /** For the "How to earn points" list. */
    val howTo = listOf(
        "1 point for every km",
        "+$GROUP_PER_RIDER for each rider with you (up to $GROUP_MAX_RIDERS)",
        "+$BREAK_POINTS for each break of 10 minutes on rides of an hour or more (up to $BREAK_MAX)",
        "+$HAZARD_POINTS for each hazard you mark for the group (up to $HAZARD_MAX)",
        "+$ROLE_POINTS for leading or riding sweep",
        "+$HOME_POINTS for \"I'm home safe\"",
        "Never for speed",
    )
}

data class Level(val index: Int, val name: String, val minPoints: Int, val next: Level?) {
    /** 0..1 of the way to the next level (1 at the top level). */
    fun progress(total: Int): Float =
        next?.let { ((total - minPoints).toFloat() / (it.minPoints - minPoints)).coerceIn(0f, 1f) } ?: 1f
}

object Levels {
    private val table = listOf(
        "Rookie" to 0,
        "Rider" to 200,
        "Road Buddy" to 600,
        "Explorer" to 1_500,
        "Road Captain" to 3_000,
        "Road King" to 6_000,
        "Legend" to 12_000,
    )

    val all: List<Level> = table.indices.reversed().fold(emptyList<Level>()) { acc, i ->
        listOf(Level(i, table[i].first, table[i].second, acc.firstOrNull())) + acc
    }

    fun of(total: Int): Level = all.last { total >= it.minPoints }

    fun name(index: Int): String = all.getOrNull(index)?.name ?: all.first().name
}

/** A badge: [goal] of something counted over the score book (rides, km…). */
enum class Badge(val title: String, val how: String, val goal: Int, val unit: String = "") {
    FIRST("First ride", "Finish your first ride", 1),
    RIDES_10("10 rides", "Ride 10 times", 10, "rides"),
    RIDES_50("50 rides", "Ride 50 times", 50, "rides"),
    CENTURY("Century", "100 km in one ride", 100, "km"),
    LONG_HAUL("Long haul", "300 km in one ride", 300, "km"),
    KM_1000("1,000 km club", "1,000 km in all", 1_000, "km"),
    KM_5000("5,000 km club", "5,000 km in all", 5_000, "km"),
    KM_10000("10,000 km club", "10,000 km in all", 10_000, "km"),
    PACK("Pack ride", "Ride with 5 riders or more", 5, "riders"),
    SPOTTER("Hazard spotter", "Mark 10 hazards for the group", 10, "hazards"),
    LEADER("Road leader", "Lead the group on 5 rides", 5, "rides"),
    SWEEP("Sweep hero", "Ride sweep on 5 rides", 5, "rides"),
    HOME("Home safe", "Check in home safe 10 times", 10, "times"),
    SMART("Smart rider", "Take a break on 10 rides of an hour or more", 10, "rides"),
    EARLY("Early bird", "Start a ride between 4 and 6 am", 1),
    WEEKLY("Every week", "Ride 4 weeks in a row", 4, "weeks"),
}

/** How far along a badge is, and the ride that earned it. */
data class BadgeProgress(val badge: Badge, val value: Int, val earnedAtMs: Long?) {
    val earned: Boolean get() = earnedAtMs != null
    val fraction: Float get() = (value.toFloat() / badge.goal).coerceIn(0f, 1f)
}

data class YearStats(
    val year: Int,
    val rides: Int,
    val km: Double,
    val movingMin: Int,
    val points: Int,
    val longestKm: Double,
    /** km in each month, January first. */
    val monthKm: List<Double>,
    /** Riders ridden with most, with how many rides. */
    val buddies: List<Pair<String, Int>>,
)

/** Totals, levels, badges and yearly stats from the score book. Pure logic, for tests and both screens. */
object ScoreBook {
    /** Rides of this length count for week streaks. */
    const val STREAK_KM = 5.0

    fun total(entries: List<ScoreEntry>): Int = entries.sumOf { ScoreRules.points(it) }

    /** Every badge with its progress; earned ones carry the start of the ride that earned them. */
    fun badges(entries: List<ScoreEntry>, zone: TimeZone = TimeZone.getDefault()): List<BadgeProgress> {
        val sorted = entries.sortedBy { it.atMs }
        val value = IntArray(Badge.entries.size)
        val earned = arrayOfNulls<Long>(Badge.entries.size)
        var rides = 0
        var km = 0.0
        var hazards = 0
        var leads = 0
        var sweeps = 0
        var homes = 0
        var smart = 0
        var bestKm = 0.0
        var mostRiders = 0
        var early = 0
        val weeks = sortedSetOf<Long>()
        var bestStreak = 0
        for (e in sorted) {
            rides++
            km += e.km
            bestKm = max(bestKm, e.km)
            mostRiders = max(mostRiders, e.others + 1)
            hazards += e.hazards
            if (e.lead) leads++
            if (e.sweep) sweeps++
            if (e.home) homes++
            if (e.movingMin >= ScoreRules.BREAKS_FROM_MIN && e.breaks > 0) smart++
            if (hourOf(e.atMs, zone) in 4..5) early = 1
            if (e.km >= STREAK_KM) {
                weeks += weekIndex(e.atMs, zone)
                bestStreak = max(bestStreak, longestRun(weeks))
            }
            Badge.entries.forEach { b ->
                val v = when (b) {
                    Badge.FIRST -> rides
                    Badge.RIDES_10, Badge.RIDES_50 -> rides
                    Badge.CENTURY, Badge.LONG_HAUL -> floor(bestKm).toInt()
                    Badge.KM_1000, Badge.KM_5000, Badge.KM_10000 -> floor(km).toInt()
                    Badge.PACK -> mostRiders
                    Badge.SPOTTER -> hazards
                    Badge.LEADER -> leads
                    Badge.SWEEP -> sweeps
                    Badge.HOME -> homes
                    Badge.SMART -> smart
                    Badge.EARLY -> early
                    Badge.WEEKLY -> bestStreak
                }
                value[b.ordinal] = v
                if (earned[b.ordinal] == null && v >= b.goal) earned[b.ordinal] = e.atMs
            }
        }
        return Badge.entries.map { BadgeProgress(it, value[it.ordinal], earned[it.ordinal]) }
    }

    /** Badges [after] has that [before] didn't: "New badge: Century". */
    fun newBadges(before: List<ScoreEntry>, after: List<ScoreEntry>): List<Badge> {
        val had = badges(before).filter { it.earned }.map { it.badge }.toSet()
        return badges(after).filter { it.earned && it.badge !in had }.map { it.badge }
    }

    fun year(entries: List<ScoreEntry>, year: Int, zone: TimeZone = TimeZone.getDefault()): YearStats {
        val mine = entries.filter { yearOf(it.atMs, zone) == year }
        val months = DoubleArray(12)
        mine.forEach { months[monthOf(it.atMs, zone)] += it.km }
        val buddies = mine.flatMap { it.names.distinct() }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .take(3).map { it.key to it.value }
        return YearStats(
            year = year,
            rides = mine.size,
            km = mine.sumOf { it.km },
            movingMin = mine.sumOf { it.movingMin },
            points = total(mine),
            longestKm = mine.maxOfOrNull { it.km } ?: 0.0,
            monthKm = months.toList(),
            buddies = buddies,
        )
    }

    /** Weeks in a row with a ride, up to this week (or last week, so a streak isn't lost on Monday). */
    fun weekStreak(entries: List<ScoreEntry>, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val weeks = entries.filter { it.km >= STREAK_KM }.map { weekIndex(it.atMs, zone) }.toSet()
        var w = weekIndex(nowMs, zone)
        if (w !in weeks) w--
        var n = 0
        while (w in weeks) {
            n++
            w--
        }
        return n
    }

    private fun longestRun(weeks: Set<Long>): Int {
        var best = 0
        for (w in weeks) {
            if (w - 1 in weeks) continue
            var n = 1
            while (w + n in weeks) n++
            best = max(best, n)
        }
        return best
    }

    private fun cal(ms: Long, zone: TimeZone) = Calendar.getInstance(zone).apply { timeInMillis = ms }

    fun yearOf(ms: Long, zone: TimeZone = TimeZone.getDefault()) = cal(ms, zone).get(Calendar.YEAR)

    private fun monthOf(ms: Long, zone: TimeZone) = cal(ms, zone).get(Calendar.MONTH)

    private fun hourOf(ms: Long, zone: TimeZone) = cal(ms, zone).get(Calendar.HOUR_OF_DAY)

    /** Weeks since 1970 counted from Monday, in local time. */
    fun weekIndex(ms: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val localDays = Math.floorDiv(ms + zone.getOffset(ms), 86_400_000L)
        // 1 Jan 1970 was a Thursday: shift so weeks start on Monday.
        return Math.floorDiv(localDays + 3, 7L)
    }
}

/**
 * Counts breaks during a ride: riding time stood still for [MIN_MS] or more, after some riding.
 * Fed with the ride's riding time, like the break reminder. Pure logic, for tests.
 */
class BreakCounter {
    var count = 0
        private set
    private var lastRidingMs = 0L
    /** When riding time last went up: a stop is measured from there. */
    private var movedAtMs: Long? = null
    private var counted = false

    fun update(nowMs: Long, ridingMs: Long) {
        if (ridingMs != lastRidingMs) {
            // Moving again (or the time source changed): a new stretch starts.
            if (ridingMs > lastRidingMs) counted = false
            movedAtMs = nowMs
            lastRidingMs = ridingMs
        }
        val moved = movedAtMs ?: return
        if (!counted && lastRidingMs > 0 && nowMs - moved >= MIN_MS) {
            count++
            counted = true
        }
    }

    companion object {
        const val MIN_MS = 10 * 60_000L
    }
}
