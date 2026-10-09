package com.ridecomm.app.ui

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
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
open class ScreenshotTest {
    /** Where the PNGs go (the small-screen run uses its own folder). */
    protected open val dir = "screenshots"


    private val riders = listOf(
        Rider("me", "Saquelain", isMe = true, isSpeaking = false, isMuted = false, signal = Signal.GOOD),
        Rider("a", "Amit", isMe = false, isSpeaking = true, isMuted = false, signal = Signal.GOOD),
        Rider("r", "Rahul", isMe = false, isSpeaking = false, isMuted = true, signal = Signal.WEAK),
        Rider("v", "Vikram", isMe = false, isSpeaking = false, isMuted = false, signal = Signal.GOOD),
    )
    private val ride = RideState(status = RideStatus.CONNECTED, code = "XCQGCW", riders = riders)

    private fun shot(name: String, night: Boolean = false, content: @Composable () -> Unit) = captureRoboImage("$dir/$name.png") {
        RideCommTheme {
            GlassBackground(Modifier.nightFilter(night)) { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { content() } }
        }
    }

    private val now = System.currentTimeMillis()
    private val roles = com.ridecomm.app.group.RideRolesState(leadId = "a", sweepId = "r", atMs = 1, byId = "me")
    private fun hz(id: String, kind: com.ridecomm.app.hazard.HazardKind, lat: Double, lon: Double, by: String, minAgo: Int, d: Double?, ahead: Boolean?) =
        com.ridecomm.app.hazard.HazardView(com.ridecomm.app.hazard.Hazard(id, kind, lat, lon, by, by, now - minAgo * 60_000L), d, ahead)
    private val hazards = com.ridecomm.app.hazard.HazardsState(
        enabled = true,
        hazards = listOf(
            hz("h1", com.ridecomm.app.hazard.HazardKind.POTHOLE, 12.7480, 77.3330, "Amit", 3, 1_800.0, true),
            hz("h2", com.ridecomm.app.hazard.HazardKind.POLICE, 12.7700, 77.3060, "Amit", 9, 4_200.0, true),
        ),
    )
    private val volumes = mapOf("v" to com.ridecomm.app.ride.RiderVolume(1f, mutedForMe = true), "a" to com.ridecomm.app.ride.RiderVolume(1.5f))

    @Test
    fun ridePushToTalkRolesHazards() = shot("20_ride_ptt_roles_hazards") {
        RideContent(ride.copy(pushToTalk = true), MusicState(), VoteState(), SosState(), roles = roles, hazards = hazards, volumes = volumes)
    }

    @Test
    fun rideTalking() = shot("20b_ride_talking") {
        RideContent(ride.copy(pushToTalk = true, talking = true, talkLatched = true), MusicState(), VoteState(), SosState(), roles = roles, hazards = hazards.copy(hazards = emptyList()))
    }

    @Test
    fun riderSheet() = shot("21_rider_sheet") {
        RiderSheet(riders[1], null, com.ridecomm.app.ride.RiderVolume(1.5f), roles, {}, {}, {}, {})
    }

    // ---- Talk to one rider, destination, home safe, break reminder ----

    private val dest = com.ridecomm.app.group.DestinationState(
        enabled = true,
        destination = com.ridecomm.app.group.Destination("d", 18.7546, 73.4062, "Lonavala", "Amit", "a", 1),
        distanceM = 42_300.0,
    )

    @Test
    fun rideNewFeatures() = shot("28_ride_whisper_destination_break") {
        RideContent(
            ride, MusicState(), VoteState(), SosState(), roles = roles,
            whisper = com.ridecomm.app.whisper.WhisperState(
                fromMe = com.ridecomm.app.whisper.WhisperView("a", "Amit", "me", "Saquelain"),
                fromMeNow = true,
            ),
            destination = dest,
            breakDue = com.ridecomm.app.trip.BreakDue(2 * 60 * 60_000L + 5 * 60_000L),
        )
    }

    @Test
    fun rideTalkingToOne() = shot("29_ride_talking_to_one") {
        RideContent(
            ride, MusicState(), VoteState(), SosState(), roles = roles,
            whisper = com.ridecomm.app.whisper.WhisperState(talkingTo = "r", talkingToName = "Rahul", live = true, others = mapOf("v" to "Amit")),
            destination = com.ridecomm.app.group.DestinationState(enabled = true),
            homeSafe = com.ridecomm.app.home.HomeSafeState(enabled = true),
        )
    }

    @Test
    fun newCards() = shot("30_new_cards") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeSafeCard(
                com.ridecomm.app.home.HomeSafeState(enabled = true, home = mapOf("a" to "Amit"), leftNotHome = mapOf("x" to "Imran")),
                riders, {}, {},
            )
            HomeSafeCard(com.ridecomm.app.home.HomeSafeState(enabled = true, meHome = true, home = mapOf("a" to "Amit")), riders.take(1), {}, {})
            DestinationCard(dest.copy(distanceM = 120.0, arrived = true), {}, {})
            HomeCheckInCard(com.ridecomm.app.ride.RecentRide("XCQGCW", now - 3_600_000L), "Saquelain") {}
        }
    }

    @Test
    fun riderSheetTalkToOne() = shot("31_rider_sheet_talk_to_one") {
        RiderSheet(riders[1], null, com.ridecomm.app.ride.RiderVolume(), roles, {}, {}, {}, {}, onWhisperStart = {})
    }

    @Test
    fun destinationDialog() = shot("32_destination_dialog") { DestinationDialog({}, {}) }

    @Test
    fun newSettings2() = shot("33_settings_home_break") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeSafeSetting(true, 18.5 to 73.8, {}, {})
            OptionChips("Break reminder", "After 2 h of riding: \"Time for a break?\"", com.ridecomm.app.trip.BreakEvery.entries, com.ridecomm.app.trip.BreakEvery.H2, { it.label }) {}
        }
    }

    @Test
    fun groupMapDestination() = captureRoboImage("$dir/34_map_destination.png") {
        RideCommTheme {
            com.ridecomm.app.ui.map.GroupMapScreen(
                sampleGroup, riders, emptyMap(), onClose = {},
                destination = com.ridecomm.app.group.Destination("d", 12.7600, 77.3300, "Lonavala", "Amit", "a", 1),
                mapContent = mapStandIn(dark = true),
            )
        }
    }

    @Test
    fun hazardPicker() = shot("22_hazard_picker") { HazardPicker({}, {}) }

    @Test
    fun newSettings() = shot("23_new_settings") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OptionChips("Talk mode", "Silent until you hold the mic button.", com.ridecomm.app.ride.TalkMode.entries, com.ridecomm.app.ride.TalkMode.PUSH_TO_TALK, { it.label }) {}
            OptionChips("Night mode", "Dim red screen and quieter alerts from sunset to sunrise.", com.ridecomm.app.night.NightModeSetting.entries, com.ridecomm.app.night.NightModeSetting.AUTO, { it.label }) {}
            EmergencyInfoSetting(com.ridecomm.app.sos.EmergencyInfo("O+", "Allergic to penicillin", "Ammi", "+91 98450 12345"), true, {}, {})
        }
    }

    @Test
    fun nightRide() = shot("24_night_ride", night = true) {
        RideContent(ride, MusicState(), VoteState(), SosState(), roles = roles, hazards = hazards)
    }

    @Test
    fun mySosHelperCard() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ridecomm.app.Prefs.setRiderName(context, "Saquelain")
        com.ridecomm.app.Prefs.setEmergencyInfo(context, com.ridecomm.app.sos.EmergencyInfo("O+", "Allergic to penicillin. Asthma inhaler in jacket.", "Ammi", "+91 98450 12345"))
        shot("25_my_sos_helper") {
            RideContent(ride, MusicState(), VoteState(), SosState(mySosActive = true, mySosStatus = "Sent to the group"))
        }
    }

    @Test
    fun sosAlertWithInfo() = shot("26_sos_alert_info") {
        val alert = SosAlert("r", "Rahul", 12.97, 77.59, distanceM = 1240f, atMs = 0, crash = true, info = com.ridecomm.app.sos.EmergencyInfo("B+", "Diabetic", "Priya", "+91 99000 11111"))
        RideContent(ride, MusicState(), VoteState(), SosState(alerts = listOf(alert)))
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

    // ---- Glass look ----

    private fun glassShot(name: String, style: GlassSceneStyle = GlassSceneStyle.HOME, content: @Composable () -> Unit) = captureRoboImage("$dir/$name.png") {
        RideCommTheme {
            GlassScene(style = style) { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { LookScope(UiLook.GLASS) { content() } } }
        }
    }

    private val trip0 = com.ridecomm.app.trip.TripState(active = true, startedAtMs = System.currentTimeMillis(), gps = true, speedKmh = 0f)

    // ---- Soft look (neumorphism) ----

    private fun softShot(name: String, content: @Composable () -> Unit) = captureRoboImage("$dir/$name.png") {
        RideCommTheme {
            NeuScene { Box(Modifier.fillMaxSize().padding(top = 24.dp)) { LookScope(UiLook.NEU) { content() } } }
        }
    }

    @Test
    fun rideSoftLikeMockup() = softShot("60_ride_soft") {
        RideContent(
            ride.copy(riders = riders.take(1)), MusicState(), VoteState(), SosState(),
            group = GroupState(enabled = true, sharing = true, me = com.ridecomm.app.group.MyFix(12.9, 77.6, null, 8f)),
            trip = trip0,
            hazards = com.ridecomm.app.hazard.HazardsState(enabled = true),
            destination = com.ridecomm.app.group.DestinationState(enabled = true),
            homeSafe = com.ridecomm.app.home.HomeSafeState(enabled = true),
        )
    }

    @Test
    fun rideSoftBusy() = softShot("61_ride_soft_busy") {
        val vote = Vote("v", VoteKind.BREAK, "Amit", System.currentTimeMillis(), 4, mapOf("a" to Ballot("Amit", true), "r" to Ballot("Rahul", true)))
        RideContent(
            ride, MusicState(), VoteState(active = vote, activeSinceMs = System.currentTimeMillis()), SosState(), roles = roles, hazards = hazards,
            trip = trip0.copy(speedKmh = 42f, distanceM = 18_400.0),
            batteries = mapOf("r" to com.ridecomm.app.alerts.BatteryInfo(18, false)),
            whisper = com.ridecomm.app.whisper.WhisperState(others = mapOf("v" to "Rahul")),
            destination = dest,
            breakDue = com.ridecomm.app.trip.BreakDue(2 * 60 * 60_000L + 5 * 60_000L),
        )
    }

    @Test
    fun rideSoftPttSos() = softShot("62_ride_soft_ptt_sos") {
        RideContent(
            ride.copy(pushToTalk = true), MusicState(),
            VoteState(), SosState(alerts = listOf(SosAlert("r", "Rahul", 12.97, 77.59, 1_200f, now, crash = false, info = com.ridecomm.app.sos.EmergencyInfo("O+", "Allergic to penicillin", "Ammi", "+91 98450 12345")))),
            roles = roles, volumes = volumes,
        )
    }

    @Test
    fun homeSoft() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ridecomm.app.Prefs.setRiderName(context, "Saquelain")
        com.ridecomm.app.Prefs.setTokenServerId(context, "ridecomm-test")
        com.ridecomm.app.Prefs.rideStarted(context, "GMMCBR", System.currentTimeMillis() - 11 * 60_000)
        com.ridecomm.app.Prefs.clearUnfinishedRide(context)
        softShot("63_home_soft") { HomeScreen(RideState()) }
    }

    @Test
    fun settingsSoft() = softShot("64_settings_soft") { SettingsDialog(onClose = {}) }

    @Test
    fun riderSheetSoft() = softShot("65_rider_sheet_soft") {
        RiderSheet(riders[1], null, com.ridecomm.app.ride.RiderVolume(1.5f), roles, {}, {}, {}, {}, onWhisperStart = {})
    }

    @Test
    fun cardsSoft() = softShot("66_cards_soft") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeSafeCard(com.ridecomm.app.home.HomeSafeState(enabled = true, home = mapOf("a" to "Amit"), leftNotHome = mapOf("x" to "Imran")), riders, {}, {})
            com.ridecomm.app.ui.map.GroupMapCard(sampleGroup, 4) {}
            HazardPicker({}, {})
        }
    }

    @Test
    fun groupMapSoft() = captureRoboImage("$dir/67_map_soft.png") {
        RideCommTheme {
            LookScope(UiLook.NEU) {
                com.ridecomm.app.ui.map.GroupMapScreen(sampleGroup, riders, emptyMap(), onClose = {}, hazards = hazards.hazards, roles = roles, mapContent = mapStandIn(dark = false))
            }
        }
    }

    @Test
    fun rideGlassLikeMockup() = glassShot("43_ride_glass", GlassSceneStyle.RIDE) {
        RideContent(
            ride.copy(riders = riders.take(1)), MusicState(), VoteState(), SosState(),
            group = GroupState(enabled = true, sharing = true, me = com.ridecomm.app.group.MyFix(12.9, 77.6, null, 8f)),
            trip = trip0,
            hazards = com.ridecomm.app.hazard.HazardsState(enabled = true),
            destination = com.ridecomm.app.group.DestinationState(enabled = true),
            homeSafe = com.ridecomm.app.home.HomeSafeState(enabled = true),
        )
    }

    @Test
    fun rideGlassBusy() = glassShot("44_ride_glass_busy", GlassSceneStyle.RIDE) {
        val vote = Vote("v", VoteKind.BREAK, "Amit", System.currentTimeMillis(), 4, mapOf("a" to Ballot("Amit", true), "r" to Ballot("Rahul", true)))
        RideContent(
            ride, MusicState(), VoteState(active = vote, activeSinceMs = System.currentTimeMillis()), SosState(), roles = roles, hazards = hazards,
            trip = trip0.copy(speedKmh = 42f, distanceM = 18_400.0),
            batteries = mapOf("r" to com.ridecomm.app.alerts.BatteryInfo(18, false)),
            whisper = com.ridecomm.app.whisper.WhisperState(fromMe = com.ridecomm.app.whisper.WhisperView("a", "Amit", "me", "Saquelain"), fromMeNow = true, others = mapOf("v" to "Rahul")),
            destination = dest,
            breakDue = com.ridecomm.app.trip.BreakDue(2 * 60 * 60_000L + 5 * 60_000L),
        )
    }

    @Test
    fun rideGlassPushToTalkMuted() = glassShot("45_ride_glass_ptt", GlassSceneStyle.RIDE) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            RideContent(ride.copy(pushToTalk = true, micMuted = false), MusicState(), VoteState(), SosState(alerts = listOf(SosAlert("r", "Rahul", 12.97, 77.59, 1_200f, now, crash = false, info = com.ridecomm.app.sos.EmergencyInfo("O+", "Allergic to penicillin", "Ammi", "+91 98450 12345")))), roles = roles)
        }
    }

    @Test
    fun settingsGlass() = glassShot("46_settings_glass") { SettingsDialog(onClose = {}) }

    @Test
    fun riderSheetGlass() = glassShot("47_rider_sheet_glass", GlassSceneStyle.RIDE) {
        RiderSheet(riders[1], null, com.ridecomm.app.ride.RiderVolume(1.5f), roles, {}, {}, {}, {}, onWhisperStart = {})
    }

    @Test
    fun hazardPickerGlass() = glassShot("48_hazard_picker_glass", GlassSceneStyle.RIDE) { HazardPicker({}, {}) }

    @Test
    fun destinationDialogGlass() = glassShot("49_destination_glass", GlassSceneStyle.RIDE) { DestinationDialog({}, {}) }

    @Test
    fun groupMapGlass() = captureRoboImage("$dir/50_map_glass.png") {
        RideCommTheme {
            LookScope(UiLook.GLASS) {
                com.ridecomm.app.ui.map.GroupMapScreen(
                    sampleGroup, riders, emptyMap(), onClose = {}, hazards = hazards.hazards, roles = roles,
                    destination = com.ridecomm.app.group.Destination("d", 12.7600, 77.3300, "Lonavala", "Amit", "a", 1),
                    mapContent = mapStandIn(dark = true),
                )
            }
        }
    }

    @Test
    fun cardsGlass() = glassShot("51_cards_glass", GlassSceneStyle.RIDE) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeSafeCard(com.ridecomm.app.home.HomeSafeState(enabled = true, home = mapOf("a" to "Amit"), leftNotHome = mapOf("x" to "Imran")), riders, {}, {})
            com.ridecomm.app.ui.map.GroupMapCard(sampleGroup, 4) {}
            MusicCard(MusicState())
        }
    }

    @Test
    fun homeGlass() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ridecomm.app.Prefs.setRiderName(context, "Saquelain")
        com.ridecomm.app.Prefs.setTokenServerId(context, "ridecomm-test")
        com.ridecomm.app.Prefs.rideStarted(context, "GMMCBR", System.currentTimeMillis() - 11 * 60_000)
        com.ridecomm.app.Prefs.clearUnfinishedRide(context)
        glassShot("40_home_glass") { HomeScreen(RideState()) }
    }

    @Test
    fun homeGlassFirstRun() = glassShot("41_home_glass_first_run") { HomeScreen(RideState(error = "Could not reach the ride server. Check internet and the token server ID.")) }

    @Test
    fun homeGlassCards() = glassShot("42_home_glass_cards") {
        val now = 100L * 60 * 60 * 1000
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeCheckInCard(com.ridecomm.app.ride.RecentRide("XCQGCW", now), "Saquelain") {}
            RejoinCard(com.ridecomm.app.ride.RecentRide("XCQGCW", now - 25 * 60_000), now, {}) {}
            GlassCard(tint = Palette.Amber) {
                androidx.compose.material3.Text("Finish setup", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Text("Add your ride server details once to start riding.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                GlassButton("Open settings", com.ridecomm.app.R.drawable.ms_settings) {}
            }
        }
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
        bitmap.captureRoboImage("$dir/10_speakers_strip.png")
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
        bitmap.captureRoboImage("$dir/14_dismiss_target.png")
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

    /** Sample group spread along a highway, with a regroup point ahead. */
    private val sampleGroup = com.ridecomm.app.group.GroupState(
        positions = mapOf(
            "a" to com.ridecomm.app.group.RiderPosition(12.7605, 77.3215, System.currentTimeMillis(), 3_600.0, com.ridecomm.app.group.Relation.AHEAD, "Amit", 62f, 40f),
            "r" to com.ridecomm.app.group.RiderPosition(12.7160, 77.2870, System.currentTimeMillis(), 2_500.0, com.ridecomm.app.group.Relation.BEHIND, "Rahul", 55f, 35f),
            "v" to com.ridecomm.app.group.RiderPosition(12.7372, 77.3035, System.currentTimeMillis(), 120.0, com.ridecomm.app.group.Relation.NEARBY, "Vikram", 58f, 38f),
        ),
        sharing = true,
        enabled = true,
        me = com.ridecomm.app.group.MyFix(12.7350, 77.3000, 38.0, 8f),
        regroup = com.ridecomm.app.group.RegroupPoint("g1", 12.7745, 77.3345, "Petrol pump", "Amit", "a", 0, setOf("a")),
        regroupDistanceM = 5_400.0,
        regroupRelation = com.ridecomm.app.group.Relation.AHEAD,
    )

    /** Real OpenStreetMap tiles (test resources) standing in for the live map, with the markers on top. */
    private fun mapStandIn(dark: Boolean): @Composable (List<com.ridecomm.app.ui.map.GroupOverlay.Place>) -> Unit = { places ->
        val context = androidx.compose.ui.platform.LocalContext.current
        val tiles = androidx.compose.runtime.remember {
            val (z, x0, y0) = javaClass.classLoader!!.getResource("maptiles/origin.txt")!!.readText().trim().split(" ").map { it.toInt() }
            val bmp = android.graphics.Bitmap.createBitmap(3 * 256, 5 * 256, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            for (dx in 0 until 3) for (dy in 0 until 5) {
                val tile = android.graphics.BitmapFactory.decodeStream(javaClass.classLoader!!.getResourceAsStream("maptiles/t_${dx}_$dy.png"))
                c.drawBitmap(tile, dx * 256f, dy * 256f, null)
            }
            Triple(bmp, z, x0 to y0)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val (bmp, z, origin) = tiles
            val native = drawContext.canvas.nativeCanvas
            val scale = size.height / bmp.height
            val offX = (size.width - bmp.width * scale) / 2
            native.save()
            native.translate(offX, 0f)
            native.scale(scale, scale)
            native.drawBitmap(bmp, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                if (dark) colorFilter = com.ridecomm.app.ui.map.MapSetup.darkFilter
            })
            native.restore()
            val world = 256.0 * (1 shl z)
            fun px(lat: Double, lon: Double): Pair<Float, Float> {
                val x = (lon + 180) / 360 * world - origin.first * 256
                val r = Math.toRadians(lat)
                val y = (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * world - origin.second * 256
                return (offX + x * scale).toFloat() to (y * scale).toFloat()
            }
            val mpp = (156543.03 * Math.cos(Math.toRadians(12.73)) / (1 shl z) / scale).toFloat()
            val painter = com.ridecomm.app.ui.map.MarkerPainter(context)
            painter.draw(native, places.map { p -> val (x, y) = px(p.lat, p.lon); p.make(x, y, mpp) })
        }
    }

    @Test
    fun groupMapDark() = captureRoboImage("$dir/17_group_map_dark.png") {
        RideCommTheme { com.ridecomm.app.ui.map.GroupMapScreen(sampleGroup, riders, emptyMap(), onClose = {}, mapContent = mapStandIn(dark = true)) }
    }

    @Test
    fun groupMapHazardsRoles() = captureRoboImage("$dir/27_map_hazards_roles.png") {
        RideCommTheme {
            // My own photo shows on my marker too.
            val me = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888).apply {
                val c = android.graphics.Canvas(this)
                c.drawColor(android.graphics.Color.rgb(0x2E, 0x7D, 0x32))
                c.drawCircle(48f, 40f, 22f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(0xF5, 0xC6, 0x9A) })
            }
            com.ridecomm.app.ui.map.GroupMapScreen(sampleGroup, riders, mapOf("me" to me), onClose = {}, hazards = hazards.hazards, roles = roles, mapContent = mapStandIn(dark = true))
        }
    }

    @Test
    fun groupMapLight() = captureRoboImage("$dir/18_group_map_light.png") {
        RideCommTheme {
            com.ridecomm.app.ui.map.GroupMapScreen(sampleGroup.copy(regroup = null), riders, emptyMap(), onClose = {}, mapContent = mapStandIn(dark = false))
        }
    }

    @Test
    fun groupMapCard() = shot("19_group_map_card") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            com.ridecomm.app.ui.map.GroupMapCard(sampleGroup, 4) {}
            com.ridecomm.app.ui.map.GroupMapCard(sampleGroup.copy(regroup = null), 4) {}
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
        bitmap.captureRoboImage("$dir/$name.png")
    }

    // ---- Ride history, planner, family ----

    private val sampleRoute = (0..120).map { i ->
        val t = i / 120.0
        com.ridecomm.app.trip.RoutePoint(18.52 + 0.23 * t + 0.02 * kotlin.math.sin(t * 9), 73.85 - 0.45 * t + 0.03 * kotlin.math.cos(t * 7))
    }
    private val sampleRide = com.ridecomm.app.trip.RideSummary(
        id = 7, code = "XCQGCW", startedAtMs = 1_791_000_000_000, endedAtMs = 1_791_000_000_000 + 3 * 3_600_000L,
        distanceM = 84_600.0, movingMs = 2 * 3_600_000L + 10 * 60_000L, topKmh = 96f, averageKmh = 39f,
        riders = listOf("Amit", "Rahul", "Vikram"), route = sampleRoute,
        stops = listOf(com.ridecomm.app.trip.RideStop(18.64, 73.62, 1_791_000_000_000 + 3_600_000L, 25 * 60_000L)),
    )
    private val samplePlan = com.ridecomm.app.plan.RidePlan(
        code = "XCQGCW", title = "Sunday Lonavala ride", atMs = 1_791_000_000_000,
        meet = com.ridecomm.app.plan.PlanPlace("Shell pump, Hinjewadi", 18.59, 73.74),
        stops = listOf(com.ridecomm.app.plan.PlanPlace("Food Mall, Expressway", 18.75, 73.4)),
        dest = com.ridecomm.app.plan.PlanPlace("Tiger Point, Lonavala", 18.73, 73.39), by = "Amit",
    )
    private val routeStandIn: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(androidx.compose.ui.graphics.Color(0xFF1B2333))) { RouteShape(sampleRoute, Modifier.fillMaxSize().padding(16.dp), width = 8f) }
    }

    @Composable
    private fun HomeExtras() {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            PlannerCard(listOf(samplePlan), joinEnabled = true, onPlan = {}) {}
            HistoryCard(listOf(sampleRide, sampleRide.copy(id = 8, distanceM = 12_400.0, movingMs = 40 * 60_000L)), onOpen = {}, onAll = {})
            PlannerCard(emptyList(), joinEnabled = true, onPlan = {}) {}
        }
    }

    @Test
    fun homePlannerHistory() = shot("70_home_plans_history") { HomeExtras() }

    @Test
    fun homePlannerHistoryGlass() = glassShot("71_home_plans_history_glass") { HomeExtras() }

    @Test
    fun homePlannerHistorySoft() = softShot("72_home_plans_history_soft") { HomeExtras() }

    @Test
    fun planDialog() = shot("73_plan_dialog") { PlanDialog({}, {}) }

    @Test
    fun rideSummaryGlass() = glassShot("74_ride_summary_glass", GlassSceneStyle.RIDE) { RideSummaryContent(sampleRide, {}, {}, {}, routeStandIn) }

    @Test
    fun rideSummarySoft() = softShot("75_ride_summary_soft") { RideSummaryContent(sampleRide, {}, {}, {}, routeStandIn) }

    @Test
    fun ridePlanAndFamily() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ridecomm.app.plan.RidePlans.save(context, samplePlan.copy(atMs = System.currentTimeMillis() + 3_600_000))
        softShot("76_ride_plan_family_soft") {
            RideContent(
                ride.copy(riders = riders.take(2), watchers = listOf("Ammi")), MusicState(), VoteState(), SosState(),
                destination = com.ridecomm.app.group.DestinationState(enabled = true),
            )
        }
        com.ridecomm.app.plan.RidePlans.delete(context, samplePlan.code)
    }

    @Test
    fun shareChoice() = shot("77_share_choice") { ShareChoiceDialog({}, {}, {}) }

    @Test
    fun sharePicture() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = RidePicture.render(context, sampleRide)
        java.io.File(dir).mkdirs()
        java.io.File("$dir/78_share_picture.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- On the road ahead ----

    private val pumps = listOf(
        com.ridecomm.app.nearby.AheadPoi(com.ridecomm.app.nearby.Poi("a", "Indian Oil, Hinjewadi Phase 2", 18.6, 73.7), 6200.0, 8.0, null),
        com.ridecomm.app.nearby.AheadPoi(com.ridecomm.app.nearby.Poi("b", "HP Petrol Pump", 18.61, 73.71), 1400.0, -25.0, "left"),
    )

    @Composable
    private fun RoadCards() {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            RoadAheadCard(com.ridecomm.app.nearby.LowFuelState(), {}, {}, {})
            RoadAheadCard(com.ridecomm.app.nearby.LowFuelState(active = true, target = pumps[1], haveDirection = true), {}, {}, {})
            RoadAheadCard(com.ridecomm.app.nearby.LowFuelState(active = true, searching = true, note = "No petrol pump found in the next 20 km. Still looking."), {}, {}, {})
        }
    }

    @Test
    fun roadAhead() = shot("80_road_ahead") { RoadCards() }

    @Test
    fun roadAheadGlass() = glassShot("81_road_ahead_glass", GlassSceneStyle.RIDE) { RoadCards() }

    @Test
    fun roadAheadSoft() = softShot("82_road_ahead_soft") { RoadCards() }

    @Test
    fun finderResults() = softShot("83_finder_soft") { FinderDialog(com.ridecomm.app.nearby.PoiKind.FUEL, groupMapOn = true, onClose = {}, preset = pumps) }

    // ---- Points & badges ----

    private fun dayAgo(d: Int, h: Int = 0) = now - d * 86_400_000L - h * 3_600_000L
    private val scoreBook = listOf(
        com.ridecomm.app.score.ScoreEntry("s1", dayAgo(1), km = 132.4, movingMin = 190, others = 3, names = listOf("Amit", "Rahul", "Vikram"), breaks = 2, hazards = 1, sweep = true, home = true),
        com.ridecomm.app.score.ScoreEntry("s2", dayAgo(8), km = 64.0, movingMin = 80, others = 2, names = listOf("Amit", "Rahul"), breaks = 1, lead = true),
        com.ridecomm.app.score.ScoreEntry("s3", dayAgo(15), km = 210.0, movingMin = 300, others = 4, names = listOf("Amit", "Rahul", "Vikram", "Asha"), breaks = 3, hazards = 3),
        com.ridecomm.app.score.ScoreEntry("s4", dayAgo(22), km = 38.0, movingMin = 50, others = 1, names = listOf("Amit")),
        com.ridecomm.app.score.ScoreEntry("s5", dayAgo(70), km = 420.0, movingMin = 520, others = 2, names = listOf("Vikram", "Asha"), breaks = 3, home = true),
        com.ridecomm.app.score.ScoreEntry("s6", dayAgo(120), km = 25.0, movingMin = 40),
    )
    private val board = com.ridecomm.app.score.RideScoreState(
        active = true,
        board = listOf(
            com.ridecomm.app.score.RiderScore("a", "Amit", 3, 96, 2_480),
            com.ridecomm.app.score.RiderScore("me", "Saquelain", 2, 74, 1_240, isMe = true),
            com.ridecomm.app.score.RiderScore("r", "Rahul", 1, 61, 520),
        ),
    )

    @Composable
    private fun PointsCards() {
        androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            PointsCard(scoreBook, now) {}
            RidePointsCard(board)
            RideScoreCard(scoreBook[0], listOf(com.ridecomm.app.score.Badge.CENTURY))
        }
    }

    @Test
    fun pointsCards() = shot("90_points_cards") { PointsCards() }

    @Test
    fun pointsCardsSoft() = softShot("91_points_cards_soft") { PointsCards() }

    @Test
    fun pointsCardsGlass() = glassShot("92_points_cards_glass", GlassSceneStyle.RIDE) { PointsCards() }

    @Test
    fun pointsPage() = shot("93_points_page") { PointsContent(scoreBook, now, {}, {}) }

    @Test
    fun pointsPageSoft() = softShot("94_points_page_soft") { PointsContent(scoreBook, now, {}, {}) }

    @Test
    fun pointsPageEmpty() = glassShot("95_points_page_empty_glass") { PointsContent(emptyList(), now, {}, {}) }

    @Test
    fun rideSummaryWithPoints() = softShot("96_ride_summary_points_soft") {
        RideSummaryContent(sampleRide, {}, {}, {}, routeStandIn, score = scoreBook[0], earned = listOf(com.ridecomm.app.score.Badge.CENTURY))
    }
}
