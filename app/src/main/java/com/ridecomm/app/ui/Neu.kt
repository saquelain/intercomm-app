package com.ridecomm.app.ui

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Soft look (neumorphism): a pale lavender page where cards and buttons are the same colour
 * as the page and stand out only by a soft shadow below-right and a white light above-left, as if
 * pressed out of the surface. Text is dark navy; blue is the main colour (from the owner's
 * screenshot and the NeuKit sample).
 */
object NeuTokens {
    val Page = Color(0xFFEEF0F8)
    val Ink = Color(0xFF161A35)
    val InkMuted = Color(0xFF636887)
    val InkFaint = Color(0xFF9398B3)
    val ShadowDark = Color(0x8CA9AECB)
    val ShadowLight = Color(0xF2FFFFFF)
    /** Raised surfaces: a touch lighter at the top-left, where the light comes from. */
    val Surface = Brush.linearGradient(listOf(Color(0xFFF8F9FE), Color(0xFFE9ECF6)))
    val Pressed = Color(0xFFE6E9F4)
    val Blue = Color(0xFF2F6BFF)
    val BlueAction = Brush.linearGradient(listOf(Color(0xFF5B8CFF), Color(0xFF2563EB)))
    val BlueGlow = Color(0x665B8CFF)
    val Sos = Brush.linearGradient(listOf(Color(0xFFFF8484), Color(0xFFF43F5E)))
    val SosGlow = Color(0x66F43F5E)
    // Tinted stat tiles and their icons (speed blue, distance violet, time orange).
    val TileBlue = Color(0xFFE8EFFF)
    val TileViolet = Color(0xFFF1EBFF)
    val TilePeach = Color(0xFFFFF0E3)
    val IconBlue = Color(0xFF2F6BFF)
    val IconViolet = Color(0xFF8B5CF6)
    val IconOrange = Color(0xFFF59E0B)
    val LeaveTint = Color(0xFFFFE1E8)
    val ShareTint = Color(0xFFEDE9FA)
    val Track = Color(0xFFD6D9EA)
    val Dot = Color(0xFF10D9A0)

    // Darker accents: the bright ones made for the dark looks don't read on a light page.
    val Go = Color(0xFF0E9F73)
    val Stop = Color(0xFFE23552)
    val Amber = Color(0xFFB7791F)
    val Cyan = Color(0xFF0B8DB0)
    val Orange = Color(0xFFE5670C)
    val Pink = Color(0xFFDB2777)
    val Violet = Color(0xFF6D4AFF)
}

private val neuBase = TextStyle(fontFamily = Inter, color = NeuTokens.Ink)

internal val neuTypography = Typography(
    displayMedium = neuBase.copy(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp),
    headlineMedium = neuBase.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp),
    titleLarge = neuBase.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleMedium = neuBase.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    bodyLarge = neuBase.copy(fontSize = 16.sp),
    bodyMedium = neuBase.copy(fontSize = 14.sp, color = NeuTokens.InkMuted),
    labelLarge = neuBase.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    labelMedium = neuBase.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = NeuTokens.InkMuted),
    labelSmall = neuBase.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)

/** Soft look type on a strongly coloured card (an SOS): white. */
internal val neuOnColorTypography = Typography(
    displayMedium = neuTypography.displayMedium.copy(color = Color.White),
    headlineMedium = neuTypography.headlineMedium.copy(color = Color.White),
    titleLarge = neuTypography.titleLarge.copy(color = Color.White),
    titleMedium = neuTypography.titleMedium.copy(color = Color.White),
    bodyLarge = neuTypography.bodyLarge.copy(color = Color.White),
    bodyMedium = neuTypography.bodyMedium.copy(color = Color(0xE6FFFFFF)),
    labelLarge = neuTypography.labelLarge.copy(color = Color.White),
    labelMedium = neuTypography.labelMedium.copy(color = Color(0xCCFFFFFF)),
    labelSmall = neuTypography.labelSmall.copy(color = Color.White),
)

private val softShadows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/** Draws [shape]'s outline moved by ([dx], [dy]) and blurred by [blur] px: one half of the soft shadow. */
private fun DrawScope.blurredOutline(shape: Shape, color: Color, dx: Float, dy: Float, blur: Float) {
    val paint = Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(dx, dy)
        canvas.drawOutline(shape.createOutline(size, layoutDirection, this), paint)
        canvas.restore()
    }
}

/**
 * Raised out of the page: a dark soft shadow below-right, a white one above-left, filled with
 * [fill]. [depth] sets how far it seems to stand out. Without blur (Android 8) only a faint
 * shadow line shows, so it still reads as a card.
 */
fun Modifier.neuRaised(shape: Shape, depth: Dp = 6.dp, fill: Brush = NeuTokens.Surface): Modifier = this
    .drawBehind {
        val d = depth.toPx()
        if (softShadows) {
            blurredOutline(shape, NeuTokens.ShadowDark, d, d, d * 2.2f)
            blurredOutline(shape, NeuTokens.ShadowLight, -d, -d, d * 2.2f)
        } else {
            translateOutline(shape, NeuTokens.ShadowDark.copy(alpha = 0.35f), d / 3)
        }
    }
    .clip(shape)
    .background(fill)

private fun DrawScope.translateOutline(shape: Shape, color: Color, by: Float) {
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(by, by)
        canvas.drawOutline(shape.createOutline(size, layoutDirection, this), Paint().apply { this.color = color })
        canvas.restore()
    }
}

/** Pressed into the page (text fields, the track of a switch): shadow inside, at the top-left. */
fun Modifier.neuInset(shape: Shape, depth: Dp = 3.dp, fill: Color = NeuTokens.Pressed): Modifier = this
    .clip(shape)
    .background(fill)
    .drawWithContent {
        val d = depth.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        clipPath(path) {
            fun ring(color: Color, dx: Float, dy: Float) {
                val paint = Paint().apply {
                    this.color = color
                    style = PaintingStyle.Stroke
                    strokeWidth = d * 4
                    if (softShadows) asFrameworkPaint().maskFilter = BlurMaskFilter(d * 2, BlurMaskFilter.Blur.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(dx, dy)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                }
            }
            ring(NeuTokens.ShadowDark, d, d)
            ring(NeuTokens.ShadowLight, -d, -d)
        }
        drawContent()
    }

/** The Soft look's page: pale lavender with pastel light in three corners (from the screenshot). */
@Composable
fun NeuScene(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(NeuTokens.Page)
            .drawBehind {
                val u = size.width / 400f
                fun light(color: Color, x: Float, y: Float, r: Float) {
                    val center = Offset(size.width * x, size.height * y)
                    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, r * u), r * u, center)
                }
                light(Color(0xFFD9CBFF), 1.0f, 0.0f, 260f)
                light(Color(0xFFF2CBF1), -0.05f, 0.62f, 190f)
                light(Color(0xFFC6D4FF), 1.05f, 0.9f, 230f)
                light(Color(0x99DCD3FF), 0.0f, 0.0f, 160f)
            },
        content = content,
    )
}

/** Slider colours for the current look (orange on the dark looks, blue on Soft). */
@Composable
fun lookSliderColors(): SliderColors = if (LocalLook.current == UiLook.NEU) {
    SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = NeuTokens.Blue, inactiveTrackColor = NeuTokens.Track)
} else {
    SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Palette.Orange, inactiveTrackColor = Color.White.copy(alpha = 0.15f))
}

/** Switch colours for the current look; [on] is the colour when switched on (blue on Soft unless given). */
@Composable
fun lookSwitchColors(on: Color? = null): SwitchColors = if (LocalLook.current == UiLook.NEU) {
    SwitchDefaults.colors(
        checkedTrackColor = on ?: NeuTokens.Blue,
        uncheckedTrackColor = NeuTokens.Track,
        uncheckedThumbColor = Color.White,
        uncheckedBorderColor = Color.Transparent,
    )
} else {
    SwitchDefaults.colors(checkedTrackColor = on ?: Palette.Orange, uncheckedTrackColor = Color.White.copy(alpha = 0.1f))
}
