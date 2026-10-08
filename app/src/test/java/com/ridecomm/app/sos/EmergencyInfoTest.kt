package com.ridecomm.app.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmergencyInfoTest {
    private val info = EmergencyInfo("O+", "Allergic to penicillin", "Ammi", "+91 98450 12345")

    @Test
    fun roundTrip() {
        assertEquals(info, EmergencyInfo.parse(info.toJson().toString()))
        assertEquals(info, EmergencyInfo.fromJson(info.toJson()))
    }

    @Test
    fun emptyIsNotSent() {
        assertTrue(EmergencyInfo().isEmpty)
        assertNull(EmergencyInfo.fromJson(EmergencyInfo().toJson()))
        assertEquals(EmergencyInfo(), EmergencyInfo.parse(""))
    }

    @Test
    fun smsText() {
        assertEquals("Blood group O+. Allergic to penicillin. Contact: Ammi +91 98450 12345.", info.smsText())
        assertEquals("Blood group B+.", EmergencyInfo(bloodGroup = "B+").smsText())
    }

    @Test
    fun longTextFromOthersIsCut() {
        val o = EmergencyInfo(medical = "x".repeat(500)).toJson()
        assertEquals(EmergencyInfo.MAX_MEDICAL, EmergencyInfo.fromJson(o)!!.medical.length)
    }
}
