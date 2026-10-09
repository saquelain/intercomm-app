package com.ridecomm.app.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AheadLogicTest {
    // Riding north from Pune. 0.009° of latitude is about 1 km.
    private val lat = 18.50
    private val lon = 73.85

    private fun poi(id: String, dLat: Double, dLon: Double) = Poi(id, id, lat + dLat, lon + dLon)

    @Test
    fun aPumpBehindIsIgnoredEvenIfCloser() {
        val behind = poi("behind", -0.009, 0.0) // 1 km behind
        val ahead = poi("ahead", 0.045, 0.0) // 5 km ahead
        val list = AheadLogic.ahead(lat, lon, 0.0, listOf(behind, ahead))
        assertEquals(listOf("ahead"), list.map { it.poi.id })
    }

    @Test
    fun withoutADirectionItsTheNearest() {
        val behind = poi("behind", -0.009, 0.0)
        val ahead = poi("ahead", 0.045, 0.0)
        assertEquals(listOf("behind", "ahead"), AheadLogic.ahead(lat, lon, null, listOf(ahead, behind)).map { it.poi.id })
    }

    @Test
    fun prefersTheOneNearTheRoad() {
        val offRoad = poi("off", 0.027, 0.020) // 3 km ahead, 2 km to the right
        val onRoad = poi("on", 0.036, 0.001) // 4 km ahead, on the road
        assertEquals("on", AheadLogic.ahead(lat, lon, 0.0, listOf(offRoad, onRoad)).first().poi.id)
    }

    @Test
    fun saysWhichSide() {
        val right = AheadLogic.ahead(lat, lon, 0.0, listOf(poi("r", 0.009, 0.002))).single()
        val left = AheadLogic.ahead(lat, lon, 0.0, listOf(poi("l", 0.009, -0.002))).single()
        val straight = AheadLogic.ahead(lat, lon, 0.0, listOf(poi("s", 0.009, 0.0))).single()
        assertEquals("right", right.side)
        assertEquals("left", left.side)
        assertNull(straight.side)
        assertTrue(AheadLogic.describe(right, true).endsWith("ahead · on your right"))
    }

    @Test
    fun aCloseOneBesideMeCounts() {
        // 200 m away at 70° off: just beside the road.
        val beside = poi("b", 0.0006, 0.0018)
        assertEquals(1, AheadLogic.ahead(lat, lon, 0.0, listOf(beside)).size)
    }

    @Test
    fun pointAheadIsWhereExpected() {
        val (aLat, aLon) = AheadLogic.pointAhead(lat, lon, 90.0, 10_000.0)
        assertEquals(lat, aLat, 0.001)
        assertTrue(aLon > lon + 0.09 && aLon < lon + 0.1)
    }

    @Test
    fun readsOpenStreetMapAndGoogle() {
        val osm = """{"elements":[{"type":"node","id":1,"lat":18.6,"lon":73.8,"tags":{"brand":"Indian Oil"}},
            {"type":"way","id":2,"center":{"lat":18.7,"lon":73.9},"tags":{}}]}"""
        val pois = AheadLogic.parseOverpass(osm, PoiKind.FUEL)
        assertEquals(listOf("Indian Oil", "Petrol pump"), pois.map { it.name })
        assertEquals(18.7, pois[1].lat, 1e-9)
        val google = """{"places":[{"id":"abc","displayName":{"text":"HP Petrol Pump"},"location":{"latitude":18.61,"longitude":73.81}}]}"""
        assertEquals("HP Petrol Pump", AheadLogic.parseGoogle(google).single().name)
        assertTrue(AheadLogic.parseOverpass("<html>busy</html>", PoiKind.FUEL).isEmpty())
    }
}
