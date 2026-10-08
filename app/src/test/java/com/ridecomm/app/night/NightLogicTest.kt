package com.ridecomm.app.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

class NightLogicTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun dayStart(y: Int, mo: Int, d: Int) = utc(y, mo, d, 0, 0)

    private fun assertNear(expectedMs: Long, actualMs: Long, label: String) {
        val minutes = (actualMs - expectedMs) / 60_000.0
        assertTrue("$label off by $minutes min", kotlin.math.abs(minutes) <= 8)
    }

    @Test
    fun bangaloreSunriseAndSunset() {
        // 8 Oct 2026: sunrise about 6:06 am, sunset about 6:02 pm IST (UTC+5:30).
        val day = SunTimes.day(dayStart(2026, 10, 8), 12.9716, 77.5946) as SunTimes.Day.Normal
        assertNear(utc(2026, 10, 8, 0, 36), day.riseMs, "sunrise")
        assertNear(utc(2026, 10, 8, 12, 32), day.setMs, "sunset")
    }

    @Test
    fun londonMidsummer() {
        // 21 Jun 2026: sunrise 4:43 am, sunset 9:21 pm BST (UTC+1).
        val day = SunTimes.day(dayStart(2026, 6, 21), 51.5074, -0.1278) as SunTimes.Day.Normal
        assertNear(utc(2026, 6, 21, 3, 43), day.riseMs, "sunrise")
        assertNear(utc(2026, 6, 21, 20, 21), day.setMs, "sunset")
    }

    @Test
    fun darkAfterSunsetWestOfGreenwich() {
        // New York, 8 Oct: sunset about 6:28 pm EDT = 22:28 UTC, which is "the next UTC day" evening.
        val lat = 40.7128
        val lon = -74.0060
        assertFalse(SunTimes.isDark(utc(2026, 10, 8, 21, 0), lat, lon)) // 5 pm
        assertTrue(SunTimes.isDark(utc(2026, 10, 8, 23, 30), lat, lon)) // 7:30 pm
        assertTrue(SunTimes.isDark(utc(2026, 10, 9, 2, 0), lat, lon)) // 10 pm
        assertTrue(SunTimes.isDark(utc(2026, 10, 9, 10, 30), lat, lon)) // 6:30 am, before sunrise
        assertFalse(SunTimes.isDark(utc(2026, 10, 9, 12, 0), lat, lon)) // 8 am
    }

    @Test
    fun bangaloreEveningRide() {
        assertFalse(SunTimes.isDark(utc(2026, 10, 8, 12, 0), 12.97, 77.59)) // 5:30 pm IST
        assertTrue(SunTimes.isDark(utc(2026, 10, 8, 13, 0), 12.97, 77.59)) // 6:30 pm IST
        assertTrue(SunTimes.isDark(utc(2026, 10, 8, 23, 30), 12.97, 77.59)) // 5 am IST
    }

    @Test
    fun polarNightAndMidnightSun() {
        assertEquals(SunTimes.Day.AlwaysDark, SunTimes.day(dayStart(2026, 12, 21), 69.65, 18.96))
        assertEquals(SunTimes.Day.AlwaysLight, SunTimes.day(dayStart(2026, 6, 21), 69.65, 18.96))
        assertTrue(SunTimes.isDark(utc(2026, 12, 21, 12, 0), 69.65, 18.96))
        assertFalse(SunTimes.isDark(utc(2026, 6, 21, 23, 0), 69.65, 18.96))
    }

    @Test
    fun settings() {
        val noon = utc(2026, 10, 8, 6, 30) // noon IST
        assertFalse(NightLogic.isNight(NightModeSetting.OFF, noon, null, null))
        assertTrue(NightLogic.isNight(NightModeSetting.ON, noon, null, null))
        assertFalse(NightLogic.isNight(NightModeSetting.AUTO, noon, 12.97, 77.59))
    }

    @Test
    fun autoWithoutLocationUsesTheClock() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        assertFalse(NightLogic.isNight(NightModeSetting.AUTO, utc(2026, 10, 8, 12, 0), null, null, ist)) // 5:30 pm
        assertTrue(NightLogic.isNight(NightModeSetting.AUTO, utc(2026, 10, 8, 13, 30), null, null, ist)) // 7 pm
        assertTrue(NightLogic.isNight(NightModeSetting.AUTO, utc(2026, 10, 8, 23, 0), null, null, ist)) // 4:30 am
        assertFalse(NightLogic.isNight(NightModeSetting.AUTO, utc(2026, 10, 9, 1, 0), null, null, ist)) // 6:30 am
    }
}
