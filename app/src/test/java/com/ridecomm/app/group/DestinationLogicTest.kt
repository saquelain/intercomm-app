package com.ridecomm.app.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DestinationLogicTest {
    @Test
    fun coordinatesFromTextAndLinks() {
        assertEquals(18.7546 to 73.4062, DestinationLogic.parseCoordinates("18.7546, 73.4062"))
        assertEquals(18.75 to 73.405, DestinationLogic.parseCoordinates("https://www.google.com/maps/@18.750,73.405,15z"))
        assertEquals(18.7546 to 73.4062, DestinationLogic.parseCoordinates("https://maps.google.com/?q=18.7546,73.4062"))
        assertEquals(-33.8568 to 151.2153, DestinationLogic.parseCoordinates("geo:-33.8568,151.2153"))
        // The place's own pin wins over the map's centre in a full Google link.
        assertEquals(
            18.7557 to 73.4091,
            DestinationLogic.parseCoordinates("https://www.google.com/maps/place/Lonavala/@18.7,73.3,12z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d18.7557!4d73.4091"),
        )
    }

    @Test
    fun notCoordinates() {
        assertNull(DestinationLogic.parseCoordinates("Lonavala"))
        assertNull(DestinationLogic.parseCoordinates("https://maps.app.goo.gl/abcDEF123"))
        assertNull(DestinationLogic.parseCoordinates("95.1234, 73.4062"))
    }

    @Test
    fun searchResults() {
        val json = """[
            {"lat":"18.7546","lon":"73.4062","name":"Lonavala","display_name":"Lonavala, Maval, Pune, Maharashtra, India"},
            {"lat":"bad","lon":"73.4","name":"Broken","display_name":"Broken"},
            {"lat":"19.0","lon":"73.0","name":"","display_name":"Khandala Ghat, Maharashtra, India"}
        ]"""
        val places = DestinationLogic.parseSearch(json)
        assertEquals(2, places.size)
        assertEquals(Place("Lonavala", "Maval, Pune, Maharashtra", 18.7546, 73.4062), places[0])
        assertEquals("Khandala Ghat", places[1].name)
        assertEquals("Maharashtra, India", places[1].detail)
        assertEquals(emptyList<Place>(), DestinationLogic.parseSearch("not json"))
    }

    @Test
    fun newestDestinationWins() {
        val a = Destination("a", 1.0, 2.0, "Lonavala", "Asha", "x", 100)
        assertTrue(DestinationLogic.newer(null, a))
        assertFalse(DestinationLogic.newer(a, a.copy(id = "0", atMs = 99)))
        assertTrue(DestinationLogic.newer(a, a.copy(id = "b")))
        assertTrue(DestinationLogic.newer(a, a.copy(id = "0", atMs = 101)))
    }

    @Test
    fun googlePlaceSearch() {
        val json = """{"places":[
            {"id":"a","displayName":{"text":"Hotel Shivam Dhaba"},"formattedAddress":"Hotel Shivam Dhaba, NH48, Khandala, Maharashtra 410301, India","location":{"latitude":18.76,"longitude":73.38}},
            {"id":"b","displayName":{"text":"No place"}},
            {"id":"c","displayName":{"text":"Lonavala"},"formattedAddress":"Lonavala, Maharashtra, India","location":{"latitude":18.75,"longitude":73.40}}]}"""
        val places = DestinationLogic.parseGoogleText(json)
        assertEquals(listOf("Hotel Shivam Dhaba", "Lonavala"), places.map { it.name })
        assertEquals("NH48, Khandala, Maharashtra 410301", places[0].detail)
        assertEquals(18.76, places[0].lat, 0.0)
        assertEquals(emptyList<Place>(), DestinationLogic.parseGoogleText("{}"))
        assertEquals(emptyList<Place>(), DestinationLogic.parseGoogleText("not json"))
    }

    @Test
    fun photonSearch() {
        val json = """{"features":[
            {"geometry":{"coordinates":[73.4062,18.7546]},"properties":{"name":"Lonavala","state":"Maharashtra","country":"India"}},
            {"geometry":{"coordinates":[73.4063,18.7547]},"properties":{"name":"Lonavala","state":"Maharashtra","country":"India"}},
            {"geometry":{"coordinates":[73.85,18.52]},"properties":{"street":"FC Road","housenumber":"12","city":"Pune","state":"Maharashtra"}},
            {"geometry":{"coordinates":[73.0]},"properties":{"name":"Broken"}}]}"""
        val places = DestinationLogic.parsePhoton(json)
        assertEquals(listOf("Lonavala", "FC Road 12"), places.map { it.name })
        assertEquals("Maharashtra, India", places[0].detail)
        assertEquals("Pune, Maharashtra", places[1].detail)
        assertEquals(18.7546, places[0].lat, 0.0)
        assertEquals(73.4062, places[0].lon, 0.0)
    }

    @Test
    fun placeNameInAFullGoogleLink() {
        assertEquals("Tiger Point", DestinationLogic.placeNameInLink("https://www.google.com/maps/place/Tiger+Point/@18.7,73.3,15z/data=!3d18.71!4d73.37"))
        assertNull(DestinationLogic.placeNameInLink("https://www.google.com/maps/@18.7,73.3,15z"))
        assertNull(DestinationLogic.placeNameInLink("https://www.google.com/maps/place/18.7,73.3/@18.7,73.3,15z"))
    }
}
