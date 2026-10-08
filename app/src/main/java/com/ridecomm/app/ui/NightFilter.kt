package com.ridecomm.app.ui

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

private val NightRed = Color(0xFFFF2A1A)

/**
 * Night mode: everything drawn in shades of red, a little dimmer. Red light keeps the eyes
 * adjusted to the dark road. Brightness is kept, so buttons and text stay as easy to tell
 * apart as by day.
 */
fun Modifier.nightFilter(on: Boolean): Modifier = if (!on) this else this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        // First to grey, keeping how bright each thing is (Android 10+), so a red button and white
        // text don't end up the same red.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) drawRect(Color.Gray, blendMode = BlendMode.Saturation)
        // Then tint: white becomes bright red, grey dim red.
        drawRect(NightRed, blendMode = BlendMode.Multiply)
        drawRect(Color.Black.copy(alpha = 0.22f))
    }
