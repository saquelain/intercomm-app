package com.ridecomm.app.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class ScoreLogicTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun at(y: Int, m: Int, d: Int, h: Int = 9) =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun ride(km: Double, atMs: Long = at(2026, 10, 4), min: Int = (km * 1.5).toInt(), id: String = "r$atMs$km", others: Int = 0) =
        ScoreEntry(id = id, atMs = atMs, km = km, movingMin = min, others = others)

    @Test
    fun soloRideIsAPointPerKm() {
        assertEquals(42, ScoreRules.points(ride(42.7)))
    }

    @Test
    fun fullGroupRideAddsItAllUp() {
        val e = ScoreEntry("a", at(2026, 10, 4), km = 120.0, movingMin = 180, others = 7, breaks = 5, hazards = 9, lead = true, sweep = false, home = true)
        val lines = ScoreRules.breakdown(e)
        assertEquals(listOf(120, 50, 45, 50, 25, 15), lines.map { it.points })
        assertEquals("Rode with 5 riders", lines[1].label)
        assertEquals(305, ScoreRules.points(e))
    }

    @Test
    fun noGroupOrRolePointsForACarParkChat() {
        val e = ScoreEntry("a", at(2026, 10, 4), km = 1.2, movingMin = 3, others = 4, lead = true, sweep = true)
        assertEquals(1, ScoreRules.points(e))
    }

    @Test
    fun breaksOnlyCountOnLongRides() {
        assertEquals(30, ScoreRules.points(ScoreEntry("a", at(2026, 10, 4), km = 30.0, movingMin = 45, breaks = 2)))
        assertEquals(60, ScoreRules.points(ScoreEntry("a", at(2026, 10, 4), km = 30.0, movingMin = 60, breaks = 2)))
    }

    @Test
    fun levelsGoUpWithPoints() {
        assertEquals("Rookie", Levels.of(0).name)
        assertEquals("Rookie", Levels.of(199).name)
        assertEquals("Rider", Levels.of(200).name)
        assertEquals("Legend", Levels.of(50_000).name)
        assertEquals(0.5f, Levels.of(400).progress(400), 0.001f)
        assertNull(Levels.of(12_000).next)
        assertEquals(7, Levels.all.size)
        assertEquals("Road Buddy", Levels.all[1].next?.name)
    }

    @Test
    fun badgesAreDatedByTheRideThatEarnedThem() {
        val first = ride(60.0, at(2026, 9, 1))
        val big = ride(130.0, at(2026, 9, 8))
        val badges = ScoreBook.badges(listOf(big, first), zone).associateBy { it.badge }
        assertEquals(first.atMs, badges.getValue(Badge.FIRST).earnedAtMs)
        assertEquals(big.atMs, badges.getValue(Badge.CENTURY).earnedAtMs)
        assertFalse(badges.getValue(Badge.LONG_HAUL).earned)
        assertEquals(130, badges.getValue(Badge.LONG_HAUL).value)
        assertEquals(190, badges.getValue(Badge.KM_1000).value)
    }

    @Test
    fun newBadgesAfterARide() {
        val before = listOf(ride(20.0, at(2026, 9, 1)))
        val after = before + ride(101.0, at(2026, 9, 2))
        assertEquals(listOf(Badge.CENTURY), ScoreBook.newBadges(before, after))
    }

    @Test
    fun earlyBirdAndPack() {
        val early = ride(30.0, at(2026, 9, 1, h = 5), others = 4)
        val badges = ScoreBook.badges(listOf(early), zone).associateBy { it.badge }
        assertTrue(badges.getValue(Badge.EARLY).earned)
        assertTrue(badges.getValue(Badge.PACK).earned)
        assertFalse(ScoreBook.badges(listOf(ride(30.0, at(2026, 9, 1, h = 7))), zone).first { it.badge == Badge.EARLY }.earned)
    }

    @Test
    fun weekStreaksStartOnMonday() {
        // Weeks of 14, 21 (two rides) and 28 Sep (Sunday 4 Oct); today is Friday 9 Oct, no ride yet this week.
        val rides = listOf(at(2026, 9, 14), at(2026, 9, 21), at(2026, 9, 27), at(2026, 10, 4)).map { ride(20.0, it) }
        assertEquals(3, ScoreBook.weekStreak(rides, at(2026, 10, 9), zone))
        // A Monday without a ride yet keeps last week's streak.
        assertEquals(3, ScoreBook.weekStreak(rides, at(2026, 10, 5), zone))
        assertEquals(0, ScoreBook.weekStreak(rides, at(2026, 10, 12), zone))
        assertEquals(ScoreBook.weekIndex(at(2026, 10, 5), zone), ScoreBook.weekIndex(at(2026, 10, 11, h = 23), zone))
        assertEquals(ScoreBook.weekIndex(at(2026, 10, 5), zone) - 1, ScoreBook.weekIndex(at(2026, 10, 4, h = 23), zone))
    }

    @Test
    fun everyWeekBadgeNeedsFourWeeksInARow() {
        val three = listOf(at(2026, 9, 7), at(2026, 9, 14), at(2026, 9, 21)).map { ride(20.0, it) }
        assertFalse(ScoreBook.badges(three, zone).first { it.badge == Badge.WEEKLY }.earned)
        val four = three + ride(20.0, at(2026, 9, 30))
        assertTrue(ScoreBook.badges(four, zone).first { it.badge == Badge.WEEKLY }.earned)
        // Short rides don't count for streaks.
        val short = three + ride(2.0, at(2026, 9, 30))
        assertFalse(ScoreBook.badges(short, zone).first { it.badge == Badge.WEEKLY }.earned)
    }

    @Test
    fun yearStats() {
        val rides = listOf(
            ScoreEntry("a", at(2026, 1, 10), km = 100.0, movingMin = 120, others = 2, names = listOf("Amit", "Rahul")),
            ScoreEntry("b", at(2026, 3, 5), km = 50.0, movingMin = 60, others = 1, names = listOf("Amit")),
            ScoreEntry("c", at(2025, 12, 30), km = 300.0, movingMin = 300),
        )
        val y = ScoreBook.year(rides, 2026, zone)
        assertEquals(2, y.rides)
        assertEquals(150.0, y.km, 0.01)
        assertEquals(180, y.movingMin)
        assertEquals(100.0, y.longestKm, 0.01)
        assertEquals(100.0, y.monthKm[0], 0.01)
        assertEquals(50.0, y.monthKm[2], 0.01)
        assertEquals(listOf("Amit" to 2, "Rahul" to 1), y.buddies)
        assertEquals(100 + 20 + 50 + 10, y.points)
    }

    @Test
    fun jsonRoundTrip() {
        val e = ScoreEntry("a", 1_790_000_000_000, km = 12.345, movingMin = 30, others = 2, names = listOf("Amit"), breaks = 1, hazards = 2, lead = true, home = true)
        val back = ScoreEntry.listFromJson(ScoreEntry.listToJson(listOf(e)))
        assertEquals(listOf(e.copy(km = 12.3)), back)
        assertEquals(emptyList<ScoreEntry>(), ScoreEntry.listFromJson("not json"))
        assertEquals(emptyList<ScoreEntry>(), ScoreEntry.listFromJson("""[{"id":"x"}]"""))
    }

    @Test
    fun breakCounterCountsEachLongStopOnce() {
        val c = BreakCounter()
        var now = 0L
        var riding = 0L
        fun step(minutes: Int, moving: Boolean) = repeat(minutes * 4) {
            now += 15_000
            if (moving) riding += 15_000
            c.update(now, riding)
        }
        step(5, moving = false) // waiting at the start: no break
        assertEquals(0, c.count)
        step(30, moving = true)
        step(15, moving = false)
        assertEquals(1, c.count)
        step(5, moving = false)
        assertEquals(1, c.count)
        step(20, moving = true)
        step(4, moving = false) // a red light
        step(10, moving = true)
        assertEquals(1, c.count)
        step(10, moving = false)
        assertEquals(2, c.count)
    }
}
