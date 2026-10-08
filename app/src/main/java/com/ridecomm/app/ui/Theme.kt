package com.ridecomm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ridecomm.app.R

/** RideComm palette: deep night background, vivid glows behind frosted glass. */
object Palette {
    val Night = Color(0xFF070A14)
    val NightDeep = Color(0xFF0E0B22)

    val Orange = Color(0xFFFF8A1F)
    val Pink = Color(0xFFFF3D81)
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)

    val Go = Color(0xFF34E89E)
    val Stop = Color(0xFFFF4D6D)
    val Amber = Color(0xFFFFC93C)

    val TextPrimary = Color(0xF5FFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextTertiary = Color(0x73FFFFFF)

    val Brand = Brush.linearGradient(listOf(Orange, Pink))
    val GoGradient = Brush.linearGradient(listOf(Color(0xFF34E89E), Color(0xFF0FB5A8)))
    val StopGradient = Brush.linearGradient(listOf(Color(0xFFFF5E62), Color(0xFFE5245E)))
}

// Kept for existing call sites.
val Orange = Palette.Orange
val Talking = Palette.Go
val Danger = Palette.Stop
val Muted = Palette.TextSecondary

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

private val base = TextStyle(fontFamily = Outfit, color = Palette.TextPrimary)

/** Classic look type (Outfit). */
internal val ClassicTypography = Typography(
    displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp),
    headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = 14.sp, color = Palette.TextSecondary),
    labelLarge = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    labelSmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val colors = darkColorScheme(
    primary = Palette.Orange,
    onPrimary = Color.White,
    secondary = Palette.Go,
    background = Palette.Night,
    surface = Color(0x1AFFFFFF),
    surfaceVariant = Color(0x26FFFFFF),
    onBackground = Palette.TextPrimary,
    onSurface = Palette.TextPrimary,
    onSurfaceVariant = Palette.TextSecondary,
    error = Palette.Stop,
    outline = Color(0x40FFFFFF),
)

@Composable
fun RideCommTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = ClassicTypography, content = content)
}
