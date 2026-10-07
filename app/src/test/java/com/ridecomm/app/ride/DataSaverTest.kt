package com.ridecomm.app.ride

import com.ridecomm.app.music.MusicMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for org.json in the message test. */
@RunWith(RobolectricTestRunner::class)
class DataSaverTest {
    @Test
    fun savesAfterAWeakSpellAndRecoversOnlyAfterAGoodOne() {
        val w = WeakNetworkWatch()
        assertFalse(w.update(0, weak = true))
        assertFalse(w.update(4_000, weak = true))
        assertTrue(w.update(5_000, weak = true))
        assertTrue(w.saving)
        // Good for a moment, weak again: stays saving.
        w.update(6_000, weak = false)
        w.update(20_000, weak = false)
        w.update(21_000, weak = true)
        assertTrue(w.saving)
        w.update(22_000, weak = false)
        assertFalse(w.update(51_000, weak = false))
        assertTrue(w.update(52_000, weak = false))
        assertFalse(w.saving)
    }

    @Test
    fun aBlipDoesNotSwitch() {
        val w = WeakNetworkWatch()
        w.update(0, weak = true)
        w.update(3_000, weak = false)
        w.update(4_000, weak = true)
        assertFalse(w.update(8_000, weak = true))
        assertTrue(w.update(9_000, weak = true))
    }

    @Test
    fun savingMessageRoundTrips() {
        assertEquals(MusicMessage.Saving(true), MusicMessage.decode(MusicMessage.Saving(true).encode()))
        assertEquals(MusicMessage.Saving(false), MusicMessage.decode(MusicMessage.Saving(false).encode()))
    }
}
