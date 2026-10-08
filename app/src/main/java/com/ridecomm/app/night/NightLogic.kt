package com.ridecomm.app.night

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class NightModeSetting(val label: String) { OFF("Off"), AUTO("Auto"), ON("On") }

/** Sunrise and sunset maths (plain Kotlin, so it can be unit-tested). */
object SunTimes {
    private const val ZENITH = 90.833 // the sun's upper edge at the horizon, with refraction
    private const val DAY_MS = 86_400_000L

    sealed interface Day {
        /** Sunrise and sunset as instants (epoch ms). */
        data class Normal(val riseMs: Long, val setMs: Long) : Day
        data object AlwaysDark : Day
        data object AlwaysLight : Day
    }

    /** Sunrise and sunset on the UTC day that starts at [utcDayStartMs]. */
    fun day(utcDayStartMs: Long, lat: Double, lon: Double): Day {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDayStartMs }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val rise = eventUtcHours(dayOfYear, lat, lon, rising = true)
        val set = eventUtcHours(dayOfYear, lat, lon, rising = false)
        if (rise !is Event.At || set !is Event.At) return if (rise == Event.NeverUp || set == Event.NeverUp) Day.AlwaysDark else Day.AlwaysLight
        return Day.Normal(utcDayStartMs + (rise.hours * 3_600_000).toLong(), utcDayStartMs + (set.hours * 3_600_000).toLong())
    }

    /** Whether the sun is down at [nowMs] at this place. */
    fun isDark(nowMs: Long, lat: Double, lon: Double): Boolean {
        val today = nowMs - Math.floorMod(nowMs, DAY_MS)
        // Sunset in UTC can fall on the next UTC day (west of Greenwich), so look at the days around.
        val events = mutableListOf<Pair<Long, Boolean>>() // time, is sunrise
        for (d in -1..1) {
            when (val day = day(today + d * DAY_MS, lat, lon)) {
                is Day.Normal -> {
                    events += day.riseMs to true
                    events += day.setMs to false
                }
                Day.AlwaysDark -> if (d == 0) return true
                Day.AlwaysLight -> if (d == 0) return false
            }
        }
        val last = events.filter { it.first <= nowMs }.maxByOrNull { it.first } ?: return false
        return !last.second
    }

    private sealed interface Event {
        data class At(val hours: Double) : Event
        data object NeverUp : Event
        data object NeverDown : Event
    }

    /** Hours after UTC midnight ("Almanac for Computers" method, good to a couple of minutes). */
    private fun eventUtcHours(dayOfYear: Int, lat: Double, lon: Double, rising: Boolean): Event {
        val lngHour = lon / 15
        val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)
        var ra = norm360(Math.toDegrees(atan(0.91764 * tanD(l))))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1) return Event.NeverUp
        if (cosH < -1) return Event.NeverDown
        val h = (if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))) / 15
        val localT = h + ra - 0.06571 * t - 6.622
        return Event.At(((localT - lngHour) % 24 + 24) % 24)
    }

    private fun norm360(x: Double) = ((x % 360) + 360) % 360
    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
}

/** Whether night mode should be on now. */
object NightLogic {
    /** Without a location, night is 6:30 pm to 6 am on the phone's clock. */
    private const val EVENING_MIN = 18 * 60 + 30
    private const val MORNING_MIN = 6 * 60

    fun isNight(setting: NightModeSetting, nowMs: Long, lat: Double?, lon: Double?, zone: TimeZone = TimeZone.getDefault()): Boolean =
        when (setting) {
            NightModeSetting.OFF -> false
            NightModeSetting.ON -> true
            NightModeSetting.AUTO -> if (lat != null && lon != null) {
                SunTimes.isDark(nowMs, lat, lon)
            } else {
                val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
                val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                minute >= EVENING_MIN || minute < MORNING_MIN
            }
        }
}
