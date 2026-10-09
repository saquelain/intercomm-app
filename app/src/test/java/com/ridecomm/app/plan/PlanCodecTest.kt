package com.ridecomm.app.plan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanCodecTest {
    private val plan = RidePlan(
        code = "CQNQNE",
        title = "Sunday Lonavala ride",
        atMs = 1_800_000_000_000,
        meet = PlanPlace("Shell pump, Hinjewadi", 18.59, 73.74),
        stops = listOf(PlanPlace("Food Mall", 18.75, 73.40)),
        dest = PlanPlace("Lonavala", 18.7546, 73.4062),
        by = "Asha",
    )

    @Test
    fun roundTripsThroughTheLink() {
        val text = PlanCodec.encode(plan)
        assertTrue("url-safe, no padding", text.none { it == '+' || it == '/' || it == '=' })
        assertEquals(plan, PlanCodec.decode(text, "CQNQNE"))
    }

    @Test
    fun readsWhatTheWebPageWrites() {
        // Same JSON as PROTOCOL.md; unknown fields are ignored.
        val json = """{"v":1,"at":1800000000000,"meet":{"name":"Gate","lat":18.5,"lon":73.8},"stops":[],"extra":true}"""
        val p = PlanCodec.fromJson(JSONObject(json), "ABCDEF")!!
        assertEquals("Gate", p.meet.name)
        assertEquals("Ride", p.label)
        assertNull(p.dest)
    }

    @Test
    fun rejectsBrokenPlans() {
        assertNull(PlanCodec.decode("not base64 !!", "ABCDEF"))
        assertNull(PlanCodec.fromJson(JSONObject("""{"at":1}"""), "ABCDEF"))
        assertNull(PlanCodec.fromJson(JSONObject("""{"meet":{"name":"x","lat":18,"lon":73}}"""), "ABCDEF"))
        assertNull(PlanCodec.fromJson(JSONObject("""{"at":5,"meet":{"name":"x","lat":200,"lon":73}}"""), "ABCDEF"))
    }

    @Test
    fun limitsSizes() {
        val long = plan.copy(title = "x".repeat(200), stops = List(9) { PlanPlace("s$it", 18.0, 73.0) })
        val back = PlanCodec.decode(PlanCodec.encode(long), "CQNQNE")!!
        assertEquals(PlanCodec.MAX_TEXT, back.title.length)
        assertEquals(PlanCodec.MAX_STOPS, back.stops.size)
    }

    @Test
    fun keepsUpcomingPlansSoonestFirst() {
        val now = plan.atMs
        val later = plan.copy(code = "BBBBBB", atMs = now + 86_400_000)
        val old = plan.copy(code = "OLDOLD", atMs = now - PlanCodec.KEEP_AFTER_MS - 1)
        val merged = PlanCodec.merge(listOf(later, old), plan, now)
        assertEquals(listOf("CQNQNE", "BBBBBB"), merged.map { it.code })
        // The same ride again replaces it.
        assertEquals(2, PlanCodec.merge(merged, plan.copy(title = "New"), now).size)
        assertEquals(merged, PlanCodec.listFrom(PlanCodec.listToJson(merged)))
    }

    @Test
    fun remindsAnHourBeforeAndAtTheStart() {
        assertEquals(listOf(plan.atMs - 3_600_000, plan.atMs), PlanCodec.reminderTimes(plan, plan.atMs - 2 * 3_600_000))
        assertEquals(listOf(plan.atMs), PlanCodec.reminderTimes(plan, plan.atMs - 60_000))
        assertTrue(PlanCodec.reminderTimes(plan, plan.atMs + 1).isEmpty())
    }
}
