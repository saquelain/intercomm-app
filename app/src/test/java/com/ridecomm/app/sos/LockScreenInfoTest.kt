package com.ridecomm.app.sos

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockScreenInfoTest {
    @Test
    fun linesForWhoeverPicksUpThePhone() {
        val info = EmergencyInfo("O+", "Allergic to penicillin", "Ammi", "+91 98450 12345")
        assertEquals(listOf("Blood group O+", "Allergic to penicillin", "Call Ammi: +91 98450 12345"), LockScreenInfo.lines(info))
        assertEquals(listOf("Call +91 98"), LockScreenInfo.lines(EmergencyInfo(contactPhone = "+91 98")))
        assertEquals(listOf("Contact: Ammi"), LockScreenInfo.lines(EmergencyInfo(contactName = "Ammi")))
    }
}
