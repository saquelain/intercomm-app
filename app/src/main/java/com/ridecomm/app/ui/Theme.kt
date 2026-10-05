package com.ridecomm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF8A1F)
val Talking = Color(0xFF3DDC84)
val Danger = Color(0xFFFF4D4D)
val Muted = Color(0xFF9AA0A6)

private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    secondary = Talking,
    background = Color(0xFF101214),
    surface = Color(0xFF1B1E22),
    surfaceVariant = Color(0xFF262A2F),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Danger,
)

/** Always dark: easier on the eyes at night and readable in sunlight with high-contrast accents. */
@Composable
fun RideCommTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
