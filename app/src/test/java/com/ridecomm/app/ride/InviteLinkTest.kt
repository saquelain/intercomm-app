package com.ridecomm.app.ride

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InviteLinkTest {

    private fun open(uri: String): String? {
        InviteLink.consume()
        InviteLink.handle(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        return InviteLink.consume()
    }

    @Test
    fun readsCodeFromAppLink() {
        assertEquals("CQNQNE", open("ridecomm://join/CQNQNE"))
        assertEquals("CQNQNE", open("ridecomm://join/cqnqne"))
    }

    @Test
    fun ignoresBrokenOrForeignLinks() {
        assertNull(open("ridecomm://join/ABC"))
        assertNull(open("https://example.com/join/CQNQNE"))
        assertNull(open("ridecomm://other/CQNQNE"))
    }

    @Test
    fun shareTextPointsToInvitePage() {
        assertEquals("https://saquelain.github.io/intercomm-app/join/?code=CQNQNE", InviteLink.url("CQNQNE"))
    }

    @Test
    fun groupKeyTravelsAfterTheHash() {
        assertEquals(
            "https://saquelain.github.io/intercomm-app/join/?code=CQNQNE#k=our%20gang%2B1",
            InviteLink.url("CQNQNE", "our gang+1"),
        )
        assertEquals("our gang+1", InviteLink.keyFrom(Uri.parse("ridecomm://join/CQNQNE?k=our%20gang%2B1")))
        assertNull(InviteLink.keyFrom(Uri.parse("ridecomm://join/CQNQNE")))
        // The code still comes through with a key attached.
        assertEquals("CQNQNE", open("ridecomm://join/CQNQNE?k=secret"))
    }

    @Test
    fun linksCarryAPlanAndTheKeyAfterTheHash() {
        assertEquals("https://saquelain.github.io/intercomm-app/join/?code=CQNQNE#p=abc&k=our%20key", InviteLink.url("CQNQNE", "our key", "abc"))
        assertEquals("https://saquelain.github.io/intercomm-app/join/?code=CQNQNE#p=abc", InviteLink.url("CQNQNE", plan = "abc"))
        assertEquals("https://saquelain.github.io/intercomm-app/watch/?code=CQNQNE", InviteLink.familyUrl("CQNQNE"))
    }
}
