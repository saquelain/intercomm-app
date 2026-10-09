package com.ridecomm.app.ui

import org.robolectric.annotation.Config

/**
 * Every screenshot again on a small phone with large text (320 dp wide, text at 130%, as with a big
 * "Display size" and "Font size" in Android settings), where layouts break first. PNGs go to
 * app/screenshots/small/.
 */
@Config(sdk = [34], qualifiers = "w320dp-h700dp-xhdpi", fontScale = 1.3f)
class SmallScreenTest : ScreenshotTest() {
    override val dir = "screenshots/small"
}
