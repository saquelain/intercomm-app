package com.ridecomm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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

    // Accents and text follow the look: the Soft look is light, so it uses darker versions.
    private val soft: Boolean @Composable @ReadOnlyComposable get() = LocalLook.current == UiLook.NEU

    val Orange: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Orange else OrangeBright
    val Pink: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Pink else PinkBright
    val Violet: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Violet else VioletBright
    val Cyan: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Cyan else CyanBright

    val Go: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Go else GoBright
    val Stop: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Stop else StopBright
    val Amber: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Amber else AmberBright

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Ink else Color(0xF5FFFFFF)
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.InkMuted else Color(0xB3FFFFFF)
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.InkFaint else Color(0x73FFFFFF)
    /** The colour of a selected choice: orange, or blue on the Soft look. */
    val Accent: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Blue else OrangeBright
    /** Icons and text drawn straight on a card or the page (white on the dark looks). */
    val OnSurface: Color @Composable @ReadOnlyComposable get() = if (soft) NeuTokens.Ink else Color.White

    // The bright accents as plain values, for places that aren't drawn in a look (overlays, map markers).
    val OrangeBright = Color(0xFFFF8A1F)
    val PinkBright = Color(0xFFFF3D81)
    val VioletBright = Color(0xFF7C5CFF)
    val CyanBright = Color(0xFF22D3EE)
    val GoBright = Color(0xFF34E89E)
    val StopBright = Color(0xFFFF4D6D)
    val AmberBright = Color(0xFFFFC93C)

    val Brand = Brush.linearGradient(listOf(OrangeBright, PinkBright))
    val GoGradient = Brush.linearGradient(listOf(Color(0xFF34E89E), Color(0xFF0FB5A8)))
    val StopGradient = Brush.linearGradient(listOf(Color(0xFFFF5E62), Color(0xFFE5245E)))
}

// Kept for existing call sites.
val Orange = Palette.OrangeBright
val Talking = Palette.GoBright
val Danger = Palette.StopBright

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

private val base = TextStyle(fontFamily = Outfit, color = Color(0xF5FFFFFF))

/** Classic look type (Outfit). */
internal val ClassicTypography = Typography(
    displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp),
    headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = 14.sp, color = Color(0xB3FFFFFF)),
    labelLarge = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    labelSmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val colors = darkColorScheme(
    primary = Palette.OrangeBright,
    onPrimary = Color.White,
    secondary = Palette.GoBright,
    background = Palette.Night,
    surface = Color(0x1AFFFFFF),
    surfaceVariant = Color(0x26FFFFFF),
    onBackground = Color(0xF5FFFFFF),
    onSurface = Color(0xF5FFFFFF),
    onSurfaceVariant = Color(0xB3FFFFFF),
    error = Palette.StopBright,
    outline = Color(0x40FFFFFF),
)

@Composable
fun RideCommTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = ClassicTypography, content = content)
}
