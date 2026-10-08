package com.ridecomm.app.ride

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RiderVolumeTest {
    @Test
    fun roundTripKeepsOnlyChanges() {
        val volumes = mapOf(
            "a" to RiderVolume(0.5f),
            "b" to RiderVolume(1f, mutedForMe = true),
            "c" to RiderVolume(), // default: not stored
        )
        val back = RiderVolume.parse(RiderVolume.format(volumes))
        assertEquals(mapOf("a" to RiderVolume(0.5f), "b" to RiderVolume(1f, true)), back)
    }

    @Test
    fun badInputIsIgnored() {
        assertEquals(emptyMap<String, RiderVolume>(), RiderVolume.parse(""))
        assertEquals(emptyMap<String, RiderVolume>(), RiderVolume.parse("not json"))
        // Out-of-range volumes are clamped.
        assertEquals(RiderVolume(RiderVolume.MAX), RiderVolume.parse("""{"a":{"v":9}}""")["a"])
    }
}
