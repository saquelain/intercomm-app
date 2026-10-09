package com.ridecomm.app.score

import com.ridecomm.app.trip.RoutePoint
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** What the year's Wrapped says about a rider, the first that fits (PROTOCOL.md "Ride Wrapped"). */
enum class RiderType(val title: String, val line: String) {
    LONG_HAULER("Long hauler", "Short hops aren't your thing: you ride far."),
    EARLY_BIRD("Early bird", "On the road before the sun, and the traffic."),
    ROAD_CAPTAIN("Road captain", "The group follows your lead."),
    GUARDIAN("Guardian", "Riding sweep and marking hazards: you look after everyone."),
    PACK_RIDER("Pack rider", "Better together: you ride with a crowd."),
    WEEKEND_WARRIOR("Weekend warrior", "Weekdays are for work, weekends are for the road."),
    EXPLORER("Explorer", "Every kind of ride, every kind of road."),
}

/** One year in review, worked out from the score book and the routes still in the ride history. */
data class WrappedYear(
    val year: Int,
    val rides: Int,
    val km: Double,
    val hours: Int,
    /** "That's like riding Mumbai to Goa 1.5 times". */
    val comparison: String,
    /** "3.1% of the way around the Earth". */
    val earth: String,
    /** Calendar.MONDAY…SUNDAY with the most rides. */
    val favouriteDay: Int,
    /** The most common start hour, 0–23. */
    val usualHour: Int,
    val longest: ScoreEntry,
    val longestRoute: List<RoutePoint>,
    val monthKm: List<Double>,
    val bestMonth: Int,
    val riders: Int,
    val buddies: List<Pair<String, Int>>,
    val badges: List<Badge>,
    val points: Int,
    val level: Level,
    val type: RiderType,
    val routes: List<List<RoutePoint>>,
)

object WrappedLogic {
    const val EARTH_KM = 40_075.0
    /** Famous rides to compare a year with, shortest first. */
    val ROUTES = listOf(
        "Mumbai to Pune" to 150.0,
        "Delhi to Jaipur" to 280.0,
        "Delhi to Manali" to 540.0,
        "Mumbai to Goa" to 590.0,
        "Delhi to Leh" to 1_000.0,
        "Kashmir to Kanyakumari" to 3_700.0,
    )

    /** The year Wrapped shows: this year, or last year until 15 January. */
    fun yearFor(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val c = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        val y = c.get(Calendar.YEAR)
        return if (c.get(Calendar.MONTH) == Calendar.JANUARY && c.get(Calendar.DAY_OF_MONTH) <= 15) y - 1 else y
    }

    /** The home / join card shows from 1 December to 15 January. */
    fun season(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val c = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        return c.get(Calendar.MONTH) == Calendar.DECEMBER || (c.get(Calendar.MONTH) == Calendar.JANUARY && c.get(Calendar.DAY_OF_MONTH) <= 15)
    }

    fun comparison(km: Double): String {
        val best = ROUTES.lastOrNull { km >= it.second }
        if (best == null) return "That's ${(km / ROUTES[0].second * 100).roundToInt()}% of the way from ${ROUTES[0].first}"
        val times = floor(km / best.second * 10) / 10
        return if (times < 1.05) "That's like riding ${best.first}" else "That's like riding ${best.first} ${fmt1(times)} times"
    }

    fun earth(km: Double): String {
        val pct = km / EARTH_KM * 100
        val text = if (pct < 10) fmt1(floor(pct * 10) / 10) else floor(pct).toInt().toString()
        return "$text% of the way around the Earth"
    }

    private fun fmt1(v: Double) = if (v == floor(v)) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

    fun riderType(e: List<ScoreEntry>, zone: TimeZone = TimeZone.getDefault()): RiderType {
        if (e.isEmpty()) return RiderType.EXPLORER
        val n = e.size.toDouble()
        fun cal(ms: Long) = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return when {
            e.sumOf { it.km } / n >= 150 -> RiderType.LONG_HAULER
            e.count { cal(it.atMs).get(Calendar.HOUR_OF_DAY) < 7 } / n >= 0.3 -> RiderType.EARLY_BIRD
            e.count { it.lead } / n >= 0.3 -> RiderType.ROAD_CAPTAIN
            (e.count { it.sweep } + e.sumOf { it.hazards }) / n >= 0.3 -> RiderType.GUARDIAN
            e.sumOf { it.others } / n >= 3 -> RiderType.PACK_RIDER
            e.count { cal(it.atMs).get(Calendar.DAY_OF_WEEK).let { d -> d == Calendar.SATURDAY || d == Calendar.SUNDAY } } / n >= 0.7 -> RiderType.WEEKEND_WARRIOR
            else -> RiderType.EXPLORER
        }
    }

    /**
     * The year in review, or null without a ride that year. [routes] are the history's rides as (start, route);
     * a ride's route is the one that started within a minute of it.
     */
    fun build(all: List<ScoreEntry>, routes: List<Pair<Long, List<RoutePoint>>>, year: Int, zone: TimeZone = TimeZone.getDefault()): WrappedYear? {
        val e = all.filter { ScoreBook.yearOf(it.atMs, zone) == year }.sortedBy { it.atMs }
        if (e.isEmpty()) return null
        fun cal(ms: Long) = Calendar.getInstance(zone).apply { timeInMillis = ms }
        val stats = ScoreBook.year(all, year, zone)
        val km = e.sumOf { it.km }
        // Monday first, so ties go to the earlier day of the week.
        val week = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        val byDay = e.groupingBy { cal(it.atMs).get(Calendar.DAY_OF_WEEK) }.eachCount()
        val favouriteDay = week.maxBy { byDay[it] ?: 0 }
        val byHour = e.groupingBy { cal(it.atMs).get(Calendar.HOUR_OF_DAY) }.eachCount()
        val usualHour = (0..23).maxBy { byHour[it] ?: 0 }
        val longest = e.maxBy { it.km }
        val yearRoutes = routes.filter { ScoreBook.yearOf(it.first, zone) == year && it.second.size >= 2 }.sortedBy { it.first }
        return WrappedYear(
            year = year,
            rides = e.size,
            km = km,
            hours = e.sumOf { it.movingMin } / 60,
            comparison = comparison(km),
            earth = earth(km),
            favouriteDay = favouriteDay,
            usualHour = usualHour,
            longest = longest,
            longestRoute = yearRoutes.firstOrNull { abs(it.first - longest.atMs) < 60_000 }?.second.orEmpty(),
            monthKm = stats.monthKm,
            bestMonth = stats.monthKm.indices.maxBy { stats.monthKm[it] },
            riders = e.flatMap { it.names }.toSet().size,
            buddies = stats.buddies,
            badges = ScoreBook.badges(all, zone).filter { b -> b.earnedAtMs?.let { ScoreBook.yearOf(it, zone) == year } == true }.map { it.badge },
            points = stats.points,
            level = Levels.of(ScoreBook.total(all)),
            type = riderType(e, zone),
            routes = yearRoutes.map { it.second },
        )
    }

    /** "around 6 am", "around 2 pm". */
    fun hourText(h: Int): String = "around " + when {
        h == 0 -> "midnight"
        h < 12 -> "$h am"
        h == 12 -> "noon"
        else -> "${h - 12} pm"
    }

    fun dayName(day: Int): String = when (day) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Sunday"
    }

    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
}
