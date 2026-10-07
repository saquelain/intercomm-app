package com.ridecomm.app.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class DataUsageTest {
    @Test
    fun formatsSizes() {
        assertEquals("0.0 MB", DataUsage.format(0))
        assertEquals("0.4 MB", DataUsage.format(400_000))
        assertEquals("9.9 MB", DataUsage.format(9_900_000))
        assertEquals("18 MB", DataUsage.format(18_400_000))
        assertEquals("1.3 GB", DataUsage.format(1_300_000_000))
    }
}
