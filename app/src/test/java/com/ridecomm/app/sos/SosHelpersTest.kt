package com.ridecomm.app.sos

import org.junit.Assert.assertEquals
import org.junit.Test

class SosHelpersTest {

    @Test
    fun parsesTypedEmergencyNumbers() {
        assertEquals(
            listOf("+919876543210", "09812345678"),
            SmsSender.parseNumbers("+91 98765-43210, 098 1234 5678"),
        )
    }

    @Test
    fun ignoresJunkAndEmptyEntries() {
        assertEquals(listOf("9876543210"), SmsSender.parseNumbers("mom, , 9876543210;  12"))
    }

    @Test
    fun formatsDistanceForSpeech() {
        assertEquals("120 meters", SosManager.formatDistance(118f))
        assertEquals("2.5 kilometers", SosManager.formatDistance(2_460f))
    }

    @Test
    fun buildsMapsLink() {
        assertEquals("https://maps.google.com/?q=12.97,77.59", LocationHelper.mapsLink(12.97, 77.59))
    }
}
