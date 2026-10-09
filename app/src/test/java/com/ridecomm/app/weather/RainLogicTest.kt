package com.ridecomm.app.weather

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RainLogicTest {
    private val now = RainLogic.parseUtc("2026-10-09T14:00")!! + 5 * 60_000L // 14:05

    private fun place(mm: List<Double>, chance: Int = 70) = JSONObject(
        """{"minutely_15":{"time":[${(0 until mm.size).joinToString(",") { "\"2026-10-09T${14 + it / 4}:${"%02d".format(it % 4 * 15)}\"" }}],
            "precipitation":[${mm.joinToString(",")}]},
           "hourly":{"time":["2026-10-09T14:00","2026-10-09T15:00","2026-10-09T16:00"],"precipitation_probability":[$chance,$chance,$chance]}}""",
    )

    @Test
    fun rainInHalfAnHour() {
        assertEquals(30, RainLogic.minutesToRain(place(listOf(0.0, 0.0, 0.5, 1.0)), now))
    }

    @Test
    fun alreadyRainingIsntNews() {
        assertNull(RainLogic.minutesToRain(place(listOf(0.8, 0.5, 0.5)), now))
    }

    @Test
    fun drizzleOrUnlikelyRainIsIgnored() {
        assertNull(RainLogic.minutesToRain(place(listOf(0.0, 0.1, 0.2, 0.1)), now))
        assertNull(RainLogic.minutesToRain(place(listOf(0.0, 0.0, 0.5), chance = 20), now))
    }

    @Test
    fun hereAndAhead() {
        val json = "[${place(listOf(0.0, 0.0, 0.0, 0.0))},${place(listOf(0.0, 0.0, 0.0, 0.0, 0.6))}]"
        val o = RainLogic.outlook(json, 25, now)
        assertNull(o.hereInMin)
        assertEquals(60, o.aheadInMin)
        assertEquals("Rain ahead in the next hour, about 25 kilometers on", RainLogic.spoken(o))
    }

    @Test
    fun roughLocation() {
        assertEquals("18.52", RainLogic.rough(18.523456))
    }
}
