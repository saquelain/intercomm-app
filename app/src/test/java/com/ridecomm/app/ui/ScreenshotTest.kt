package com.ridecomm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ridecomm.app.group.GroupState
import com.ridecomm.app.group.Relation
import com.ridecomm.app.group.RiderPosition
import com.ridecomm.app.music.MusicState
import com.ridecomm.app.ride.RideState
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.ride.Rider
import com.ridecomm.app.ride.Signal
import com.ridecomm.app.sos.SosAlert
import com.ridecomm.app.sos.SosState
import com.ridecomm.app.vote.Ballot
import com.ridecomm.app.vote.Vote
import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteManager
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

    /** Returning rider: name already saved, so no name field. */
    @Test
    fun homeWithProfile() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ridecomm.app.Prefs.setRiderName(context, "Saquelain")
        com.ridecomm.app.Prefs.setTokenServerId(context, "ridecomm-test")
        shot("1b_home_profile") { HomeScreen(RideState()) }
    }

    @Test
    fun crashCountdown() = shot("5b_crash_countdown") { SosCountdown(12, crash = true) }

    @Test
    fun speakersStrip() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = com.ridecomm.app.overlay.SpeakerOverlay.SpeakersView(context)
        view.speakers = listOf(
            com.ridecomm.app.overlay.Speaker("a", "Amit Kumar", null),
            com.ridecomm.app.overlay.Speaker("r", "Rahul", null),
        )
        val spec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val w = 1200
        val h = 600
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(0xDD, 0xE6, 0xD8)) // stand-in for Maps
        canvas.save()
        canvas.translate(36f, 120f)
        // Same transparency as the real window.
        canvas.saveLayerAlpha(0f, 0f, view.measuredWidth.toFloat(), view.measuredHeight.toFloat(), (0.78f * 255).toInt())
        view.draw(canvas)
        canvas.restore()
        canvas.restore()
        bitmap.captureRoboImage("screenshots/10_speakers_strip.png")
    }

    /** The ✕ while dragging the floating button: idle on the left, with the button over it on the right. */
    @Test
    fun dismissTarget() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = context.resources.displayMetrics.density
        val size = (120 * d).toInt()
        val bubbleSize = (68 * d).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size * 2 + 120, size + 80, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(0xDD, 0xE6, 0xD8)) // stand-in for Maps
        listOf(false, true).forEachIndexed { i, active ->
            canvas.save()
            canvas.translate(40f + i * (size + 40f), 40f)
            if (active) {
                val bubble = com.ridecomm.app.overlay.BubbleOverlay.BubbleView(context)
                bubble.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(bubbleSize, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(bubbleSize, android.view.View.MeasureSpec.EXACTLY),
                )
                bubble.layout(0, 0, bubbleSize, bubbleSize)
                canvas.save()
                canvas.translate((size - bubbleSize) / 2f, (size - bubbleSize) / 2f)
                bubble.draw(canvas)
                canvas.restore()
            }
            val target = com.ridecomm.app.overlay.BubbleOverlay.DismissTargetView(context)
            target.active = active
            target.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
            )
            target.layout(0, 0, size, size)
            canvas.saveLayerAlpha(0f, 0f, size.toFloat(), size.toFloat(), (0.8f * 255).toInt())
            target.draw(canvas)
            canvas.restore()
            canvas.restore()
        }
        bitmap.captureRoboImage("screenshots/14_dismiss_target.png")
    }

    @Test
    fun ride() = shot("2_ride") {
        RideContent(
            ride,
            MusicState(title = "Kesariya", djName = "Amit", playing = true, volume = 0.7f),
            VoteState(),
            SosState(),
            GroupState(
                sharing = true,
                positions = mapOf(
                    "a" to RiderPosition(0.0, 0.0, 0, distanceM = 90.0, relation = Relation.NEARBY),
                    "r" to RiderPosition(0.0, 0.0, 0, distanceM = 1_240.0, relation = Relation.BEHIND),
                    "v" to RiderPosition(0.0, 0.0, 0, distanceM = 420.0, relation = Relation.AHEAD),
                ),
            ),
            dataUsed = 18_400_000,
            onCall = setOf("v"),
            trip = com.ridecomm.app.trip.TripState(
                active = true,
                startedAtMs = System.currentTimeMillis() - 74 * 60_000L,
                distanceM = 42_300.0,
                speedKmh = 68f,
                gps = true,
            ),
            batteries = mapOf(
                "r" to com.ridecomm.app.alerts.BatteryInfo(8, charging = false),
                "v" to com.ridecomm.app.alerts.BatteryInfo(24, charging = false),
                "a" to com.ridecomm.app.alerts.BatteryInfo(60, charging = false),
            ),
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
    fun rideResultAndSent() = shot("8_vote_result_and_sent") {
        val now = System.currentTimeMillis()
        val done = Vote(
            id = "v2", kind = VoteKind.FOOD, starterName = "Saquelain", startedAtMs = 0, riders = 4,
            ballots = mapOf("me" to Ballot("Saquelain", true), "a" to Ballot("Amit", true), "v" to Ballot("Vikram", true)),
            approved = true,
        )
        RideContent(
            ride,
            MusicState(),
            VoteState(lastResult = done, resultSinceMs = now - VoteManager.RESULT_SHOWN_MS * 4 / 10),
            SosState(),
            sent = VoteManager.Sent(QuickMessage.SLOW_DOWN, now - VoteManager.SENT_SHOWN_MS / 3),
        )
    }

    @Test
    fun rideSos() = shot("4_ride_sos") {
        val alert = SosAlert("r", "Rahul", 12.97, 77.59, distanceM = 1240f, atMs = 0)
        RideContent(ride, MusicState(), VoteState(), SosState(alerts = listOf(alert)))
    }

    @Test
    fun musicCardEmpty() = shot("9_music_card") {
        Box(Modifier.padding(16.dp)) { MusicCard(MusicState()) }
    }

    @Test
    fun rejoinAndRecent() = shot("12_rejoin_recent") {
        val now = 100L * 60 * 60 * 1000
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            RejoinCard(com.ridecomm.app.ride.RecentRide("XCQGCW", now - 25 * 60_000), now, {}) {}
            GlassCard { RecentRidesRow(listOf(com.ridecomm.app.ride.RecentRide("CQNQNE", now - 26 * 60 * 60_000), com.ridecomm.app.ride.RecentRide("HKP4TZ", now - 3 * 24 * 60 * 60_000)), now) {} }
        }
    }

    @Test
    fun windFilterTest() = shot("13_wind_filter_test") {
        val rate = com.ridecomm.app.audio.FilterTester.RATE
        val rnd = kotlin.random.Random(4)
        val lp = com.ridecomm.app.audio.Biquad.lowPass(150f, rate)
        fun voice(t: Int) = (0.25 * kotlin.math.sin(2 * Math.PI * 220 * t / rate) * (0.6 + 0.4 * kotlin.math.sin(2 * Math.PI * 3 * t / rate)) +
            0.2 * kotlin.math.sin(2 * Math.PI * 1200 * t / rate)).toFloat()
        // 0.5 s quiet, 2 s talk, 3 s wind, 1.5 s talk, 1 s quiet.
        val clip = FloatArray(rate * 8) { t ->
            val sec = t.toFloat() / rate
            when {
                sec < 0.5f -> 0f
                sec < 2.5f -> voice(t)
                sec < 5.5f -> lp.filter(0.8f * (rnd.nextFloat() * 2 - 1)) * 3f
                sec < 7f -> voice(t)
                else -> 0f
            }
        }
        val analysis = com.ridecomm.app.audio.GateAnalysis.run(clip, rate, com.ridecomm.app.audio.GateSettings.MEDIUM)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LiveMeter(analysis.frames[80])
            ClipView(clip, analysis, playhead = 0.42f)
            TotalsRow(analysis.totals)
        }
    }

    @Test
    fun windFilterFineTune() = shot("15_wind_fine_tune") {
        Box(Modifier.padding(16.dp)) {
            FineTune(com.ridecomm.app.audio.GateSettings(thresholdDb = -46f, rumbleAllowanceDb = 0f, holdMs = 700, reductionDb = 24f)) {}
        }
    }

    @Test
    fun speedAndUpdatesSettings() = shot("16_speed_settings") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SpeedAlertSetting(90) {}
            RideUpdatesSetting(com.ridecomm.app.trip.UpdateEvery.MIN_15) {}
        }
    }

    @Test
    fun windGateSetting() = shot("11_wind_gate_setting") {
        Box(Modifier.padding(16.dp)) { WindGateSetting(com.ridecomm.app.audio.GateSettings(holdMs = 800)) {} }
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
        menu.centerIsClose = false
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
