package com.ridecomm.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ridecomm.app.music.MusicState
import com.ridecomm.app.ride.RideState
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ride.Signal
import com.ridecomm.app.sos.SosAlert
import com.ridecomm.app.sos.SosState
import com.ridecomm.app.vote.Ballot
import com.ridecomm.app.vote.Vote
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNGs (app/screenshots/) so the design can be reviewed without a
 * phone. Record with: ./gradlew recordRoborazziRelease
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {

    private val riders = listOf(
        Rider("me", "Saquelain", isMe = true, isSpeaking = false, isMuted = false, signal = Signal.GOOD),
        Rider("a", "Amit", isMe = false, isSpeaking = true, isMuted = false, signal = Signal.GOOD),
        Rider("r", "Rahul", isMe = false, isSpeaking = false, isMuted = true, signal = Signal.WEAK),
        Rider("v", "Vikram", isMe = false, isSpeaking = false, isMuted = false, signal = Signal.GOOD),
    )
    private val ride = RideState(status = RideStatus.CONNECTED, code = "XCQGCW", riders = riders)

    private fun shot(name: String, content: @Composable () -> Unit) = captureRoboImage("screenshots/$name.png") {
        RideCommTheme {
            GlassBackground { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { content() } }
        }
    }

    @Test
    fun home() = shot("1_home") { HomeScreen(RideState()) }

    @Test
    fun ride() = shot("2_ride") {
        RideContent(
            ride,
            MusicState(title = "Kesariya", djName = "Amit", playing = true, volume = 0.7f),
            VoteState(),
            SosState(),
        )
    }

    @Test
    fun rideVote() = shot("3_ride_vote") {
        val vote = Vote(
            id = "v1", kind = VoteKind.BREAK, starterName = "Rahul", startedAtMs = 0, riders = 4,
            ballots = mapOf("r" to Ballot("Rahul", true), "a" to Ballot("Amit", true)),
        )
        RideContent(ride.copy(micMuted = true), MusicState(), VoteState(active = vote), SosState())
    }

    @Test
    fun rideSos() = shot("4_ride_sos") {
        val alert = SosAlert("r", "Rahul", 12.97, 77.59, distanceM = 1240f, atMs = 0)
        RideContent(ride, MusicState(), VoteState(), SosState(alerts = listOf(alert)))
    }

    @Test
    fun sosCountdown() = shot("5_sos_countdown") { SosCountdown(3) }

    @Test
    fun floatingMenu() = renderMenu("6_floating_menu", buttonY = 2640 * 0.5f)

    /** Button dragged near the top: the fan opens lower so every option stays on screen. */
    @Test
    fun floatingMenuNearTop() = renderMenu("7_floating_menu_top", buttonY = 3 * 70f)

    private fun renderMenu(name: String, buttonY: Float) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        fun opt(icon: Int, label: String, color: Int) = com.ridecomm.app.overlay.SlideOption(icon, label, color, "") {}
        val green = android.graphics.Color.rgb(0x34, 0xE8, 0x9E)
        val red = android.graphics.Color.rgb(0xFF, 0x4D, 0x6D)
        val orange = android.graphics.Color.rgb(0xFF, 0x8A, 0x1F)
        val violet = android.graphics.Color.rgb(0x7C, 0x5C, 0xFF)
        val amber = android.graphics.Color.rgb(0xFF, 0xC9, 0x3C)
        val cyan = android.graphics.Color.rgb(0x22, 0xD3, 0xEE)
        val menu = com.ridecomm.app.overlay.SlideMenuView(
            context,
            inner = listOf(
                opt(com.ridecomm.app.R.drawable.ms_mic_off, "Mute", red),
                opt(com.ridecomm.app.R.drawable.ms_pause, "Pause", orange),
                opt(com.ridecomm.app.R.drawable.ms_skip_next, "Next song", orange),
                opt(com.ridecomm.app.R.drawable.ms_smartphone, "Open app", violet),
            ),
            outer = listOf(
                opt(com.ridecomm.app.R.drawable.ms_sos, "SOS", red),
                opt(com.ridecomm.app.R.drawable.ms_coffee, "Break?", amber),
                opt(com.ridecomm.app.R.drawable.ms_local_gas_station, "Fuel?", amber),
                opt(com.ridecomm.app.R.drawable.ms_restaurant, "Food?", amber),
                opt(com.ridecomm.app.R.drawable.ms_speed, "Slow down", cyan),
                opt(com.ridecomm.app.R.drawable.ms_front_hand, "Wait for me", cyan),
            ),
        )
        val w = 1200
        val h = 2640
        menu.anchorX = w - 40 * 3f
        menu.anchorY = buttonY
        menu.centerX = menu.anchorX
        val margin = com.ridecomm.app.overlay.SlideMenuView.FIT_MARGIN_DP * 3
        val gap = com.ridecomm.app.overlay.SlideMenuView.MOVED_FAN_GAP_DP * 3
        menu.centerY = if (buttonY < margin) (buttonY + gap).coerceIn(margin, h - margin) else buttonY
        menu.centerIsClose = true
        menu.selected = 1
        menu.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        menu.layout(0, 0, w, h)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        // Stand-in for the app underneath (e.g. Maps).
        canvas.drawColor(android.graphics.Color.rgb(0xDD, 0xE6, 0xD8))
        menu.draw(canvas)
        bitmap.captureRoboImage("screenshots/$name.png")
    }
}
