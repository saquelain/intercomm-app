package com.ridecomm.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ridecomm.app.night.NightMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.composed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.heightIn
import com.ridecomm.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val CardShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)
/** Cards in the Glass look are rounder. */
val GlassCardShape = RoundedCornerShape(30.dp)
/** Cards in the Soft look. */
val NeuCardShape = RoundedCornerShape(26.dp)

/**
 * The scene behind the glass: a deep night gradient with soft colour glows. The glows are what
 * make translucent cards read as frosted glass.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Night, Palette.NightDeep))),
    ) {
        Canvas(Modifier.fillMaxSize().blur(60.dp)) {
            fun glow(color: Color, x: Float, y: Float, r: Float, alpha: Float) {
                val center = Offset(size.width * x, size.height * y)
                val radius = size.width * r
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
                    radius,
                    center,
                )
            }
            glow(Palette.VioletBright, 0.0f, 0.05f, 0.95f, 0.60f)
            glow(Palette.PinkBright, 1.0f, 0.30f, 0.75f, 0.42f)
            glow(Palette.OrangeBright, 0.10f, 0.62f, 0.60f, 0.30f)
            glow(Palette.CyanBright, 0.95f, 0.95f, 0.75f, 0.32f)
        }
        content()
    }
}

/**
 * Frosted glass surface: translucent fill with a light sheen from the top-left and a thin bright
 * rim, as if lit from above. [tint] colours the glass (e.g. red for SOS).
 */
fun Modifier.glass(
    shape: Shape = CardShape,
    tint: Color = Color.White,
    fillAlpha: Float = 0.09f,
    rimAlpha: Float = 0.32f,
): Modifier = composed {
    when (LocalLook.current) {
        UiLook.GLASS -> glassLookSurface(shape, tint, fillAlpha)
        UiLook.NEU -> neuSurface(shape, tint, fillAlpha)
        UiLook.CLASSIC -> classicGlass(shape, tint, fillAlpha, rimAlpha)
    }
}

/**
 * The Soft look's version of a glass surface: raised from the page. A strongly tinted one (a
 * selected chip) is filled with the colour, a lightly tinted one (a warning card) just takes a hint
 * of it.
 */
internal fun Modifier.neuSurface(shape: Shape, tint: Color, fillAlpha: Float, depth: Dp = 4.dp): Modifier = when {
    tint == Color.White -> neuRaised(shape, depth)
    fillAlpha >= 0.2f -> neuRaised(shape, depth, Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint)))
    else -> neuRaised(shape, depth, neuTinted(tint))
}

/** The page colour with a hint of [tint], lit from the top-left. */
internal fun neuTinted(tint: Color, amount: Float = 0.12f): Brush = Brush.linearGradient(
    listOf(lerp(Color(0xFFF8F9FE), tint, amount), lerp(Color(0xFFE9ECF6), tint, amount)),
)

private fun Modifier.classicGlass(shape: Shape, tint: Color, fillAlpha: Float, rimAlpha: Float): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(tint.copy(alpha = fillAlpha + 0.07f), tint.copy(alpha = fillAlpha * 0.5f)),
        ),
    )
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = rimAlpha), Color.White.copy(alpha = 0.04f))),
        shape,
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    fillAlpha: Float = 0.09f,
    padding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    // Soft: a strongly tinted card (an SOS) is filled with its colour and takes white text.
    val solid = look == UiLook.NEU && tint != Color.White && fillAlpha >= 0.25f
    Column(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    look == UiLook.GLASS -> Modifier.frost(GlassCardShape, tint = tint.takeIf { it != Color.White })
                    solid -> Modifier.neuRaised(NeuCardShape, 7.dp, Brush.linearGradient(listOf(tint.copy(alpha = 0.88f), tint)))
                    look == UiLook.NEU -> Modifier.neuRaised(NeuCardShape, 7.dp, if (tint == Color.White) NeuTokens.Surface else neuTinted(tint))
                    else -> Modifier.glass(tint = tint, fillAlpha = fillAlpha)
                },
            )
            .padding(if (look != UiLook.CLASSIC) maxOf(padding, 20.dp) else padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        val ink = if (look == UiLook.NEU && !solid) NeuTokens.Ink else Color.White
        CompositionLocalProvider(LocalCardInk provides ink) {
            if (solid) MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = neuOnColorTypography) { content() } else content()
        }
    }
}

/** Text and icon colour drawn straight on the current card: white, or dark on a pale Soft card. */
val LocalCardInk = compositionLocalOf { Color.White }

@Composable
fun Ico(@DrawableRes icon: Int, size: Dp = 24.dp, tint: Color = Palette.TextPrimary, description: String? = null) {
    Icon(painterResource(icon), description, Modifier.size(size), tint = tint)
}

/** Main call to action: bright gradient pill. */
@Composable
fun PrimaryButton(
    text: String,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    brush: Brush = Palette.Brand,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 62.dp,
    onClick: () -> Unit,
) {
    val look = LocalLook.current
    val glassLook = look == UiLook.GLASS
    // Glass: the brand action turns into the warm orange-to-pink pill with a pink glow. Soft: blue.
    val brand = brush == Palette.Brand
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(if (look != UiLook.CLASSIC && height == 62.dp) 64.dp else height)
            .then(
                if (look == UiLook.NEU) {
                    Modifier
                        .then(if (enabled && brand) Modifier.glow(NeuTokens.BlueGlow, 18.dp, PillShape, offsetY = 6.dp) else Modifier)
                        .neuRaised(PillShape, 5.dp, if (brand) NeuTokens.BlueAction else brush)
                } else {
                    Modifier
                        .then(if (glassLook && enabled) Modifier.glow(if (brand) GlassTokens.ButtonGlow else Color.White.copy(alpha = 0.18f), 20.dp, PillShape) else Modifier)
                        .clip(PillShape)
                        .background(if (glassLook && brand) GlassTokens.Action else brush)
                        .border(
                            if (glassLook) 1.5.dp else 1.dp,
                            if (glassLook && brand) GlassTokens.ButtonRim else Color.White.copy(alpha = if (glassLook) 0.5f else 0.35f),
                            PillShape,
                        )
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Ico(icon, if (glassLook) 25.dp else 24.dp, contentColor)
            Spacer(Modifier.width(if (glassLook) 12.dp else 10.dp))
        }
        // Long labels on narrow buttons shrink a little instead of being cut off.
        val style = MaterialTheme.typography.labelLarge
        Text(text, style = style, color = contentColor, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(12.sp, style.fontSize))
    }
}

/** Secondary action: glass pill. */
@Composable
fun GlassButton(
    text: String,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentColor: Color = Palette.TextPrimary,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val look = LocalLook.current
    val glassLook = look != UiLook.CLASSIC
    Row(
        modifier
            .alpha(if (enabled) 1f else if (glassLook) 0.58f else 0.4f)
            .height(if (glassLook && height == 56.dp) 58.dp else height)
            .then(
                when {
                    look == UiLook.NEU -> Modifier.neuRaised(PillShape, 5.dp, if (tint == Color.White) NeuTokens.Surface else neuTinted(tint, 0.16f))
                    !glassLook -> Modifier.glass(PillShape, tint = tint, fillAlpha = if (tint == Color.White) 0.10f else 0.22f)
                    tint == Color.White -> Modifier
                        .clip(PillShape)
                        .background(GlassTokens.Secondary)
                        .border(1.dp, Color.White.copy(alpha = 0.46f), PillShape)
                    else -> Modifier.frost(PillShape, tint = tint, glow = false)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Ico(icon, 22.dp, contentColor)
            Spacer(Modifier.width(if (glassLook) 10.dp else 8.dp))
        }
        val style = if (glassLook) MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge
        Text(text, style = style, color = contentColor, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(12.sp, style.fontSize))
    }
}

/** Round icon-only button; glass by default, or filled with [brush]. */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    brush: Brush? = null,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val soft = LocalLook.current == UiLook.NEU
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(size)
            .then(
                if (soft) {
                    Modifier.neuRaised(CircleShape, if (size >= 52.dp) 6.dp else 4.dp, brush ?: NeuTokens.Surface)
                } else if (brush != null) {
                    Modifier.clip(CircleShape).background(brush).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                } else if (LocalLook.current == UiLook.GLASS && tint == Color.White) {
                    Modifier.clip(CircleShape).background(GlassTokens.Round).border(1.dp, Color.White.copy(alpha = 0.44f), CircleShape)
                } else {
                    Modifier.glass(CircleShape, tint = tint, fillAlpha = if (tint == Color.White) 0.10f else 0.25f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Soft: the icon takes the colour on a plain raised button (dark, or red for Leave…).
        val iconColor = when {
            !soft || brush != null -> Color.White
            tint == Color.White -> NeuTokens.Ink
            else -> tint
        }
        Ico(icon, iconSize, iconColor, description)
    }
}

/** Icon tile with a caption underneath, for rows of quick actions. */
@Composable
fun GlassTile(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(84.dp)
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Ico(icon, 28.dp, if (accent == Color.White) Palette.OnSurface else accent)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val color = when (LocalLook.current) {
        UiLook.GLASS -> GlassTokens.Lavender
        UiLook.NEU -> NeuTokens.InkMuted
        UiLook.CLASSIC -> Palette.TextTertiary
    }
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Small status capsule with a coloured dot. */
@Composable
fun StatusPill(text: String, color: Color) {
    Row(
        Modifier
            .glass(PillShape, fillAlpha = 0.07f, rimAlpha = 0.22f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = Palette.TextPrimary)
    }
}

/** Text input on glass, with its label above. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    center: Boolean = false,
) {
    val look = LocalLook.current
    val glassLook = look == UiLook.GLASS
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (look != UiLook.CLASSIC) 12.dp else 8.dp)) {
        SectionLabel(label)
        val style = textStyle.copy(
            color = Palette.TextPrimary,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(if (look == UiLook.NEU) NeuTokens.Blue else Palette.Orange),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (look == UiLook.NEU) {
                                // Pressed into the page.
                                Modifier.neuInset(RoundedCornerShape(20.dp))
                            } else if (glassLook) {
                                Modifier
                                    .clip(RoundedCornerShape(23.dp))
                                    .background(GlassTokens.Input)
                                    .border(1.dp, Color.White.copy(alpha = 0.53f), RoundedCornerShape(23.dp))
                            } else {
                                Modifier.glass(RoundedCornerShape(18.dp), fillAlpha = 0.06f, rimAlpha = 0.25f)
                            },
                        )
                        .padding(PaddingValues(horizontal = 18.dp, vertical = if (look != UiLook.CLASSIC) 18.dp else 16.dp)),
                    contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = style.copy(color = if (glassLook) Color.White.copy(alpha = 0.63f) else Palette.TextTertiary))
                    }
                    inner()
                }
            },
        )
    }
}

/** Dialog panel in the same glass style (darker, since the dialog floats over a scrim). */
@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val night by NightMode.active.collectAsState()
    val look = LocalLook.current
    val glassLook = look == UiLook.GLASS
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A dialog is its own window: the scene behind can't line up with it, so no softened copy inside.
        CompositionLocalProvider(LocalGlassScene provides null) {
            Column(
                Modifier
                    .padding(20.dp)
                    .nightFilter(night)
                    .fillMaxWidth()
                    .then(
                        if (look == UiLook.NEU) {
                            Modifier.neuRaised(CardShape, 8.dp, SolidColor(NeuTokens.Page))
                        } else {
                            Modifier
                                .clip(CardShape)
                                .background(if (glassLook) GlassDialogPanel else Brush.linearGradient(listOf(Color(0xF21B1F33), Color(0xF2120F26))))
                                .border(
                                    1.dp,
                                    if (glassLook) {
                                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.3f)))
                                    } else {
                                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)))
                                    },
                                    CardShape,
                                )
                        },
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/**
 * Glass look: a whole-width row to open something ("Where are we heading?", "Mark a road hazard"),
 * with a coloured icon tile and an arrow, from the ride mock-up.
 */
@Composable
fun ActionRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    iconTint: Color = GlassTokens.TileBlueIcon,
    iconBackground: Color = GlassTokens.TileBlue,
    onClick: () -> Unit,
) {
    val soft = LocalLook.current == UiLook.NEU
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .then(if (soft) Modifier.neuRaised(shape, 6.dp) else Modifier.frost(shape, glow = false))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (soft) {
            // A raised round badge with the coloured icon, as in the Soft mock-up.
            Box(Modifier.size(50.dp).neuRaised(CircleShape, 4.dp, neuTinted(iconTint, 0.14f)), contentAlignment = Alignment.Center) {
                Ico(icon, 26.dp, iconTint)
            }
        } else {
            Box(Modifier.size(49.dp).clip(RoundedCornerShape(18.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
                Ico(icon, 24.dp, iconTint)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = if (soft) NeuTokens.InkMuted else GlassTokens.Muted), maxLines = 2)
            }
        }
        Spacer(Modifier.width(10.dp))
        if (soft) {
            Box(Modifier.size(36.dp).neuRaised(CircleShape, 3.dp), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ms_chevron_right, 22.dp, NeuTokens.InkMuted)
            }
        } else {
            Ico(R.drawable.ms_arrow_forward, 24.dp, Color.White)
        }
    }
}

/** Round avatar with the rider's initial on a gradient. */
@Composable
fun Avatar(name: String, size: Dp, brush: Brush, ring: Color?, photo: Bitmap? = null) {
    Box(
        Modifier
            .size(size)
            .then(if (ring != null) Modifier.border(3.dp, ring, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            val image = remember(photo) { photo.asImageBitmap() }
            Image(image, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
    }
}

/**
 * Thin bar that fills from left to right over [durationMs], counted from [startMs] (wall clock),
 * so you can see how long a card stays before it goes away.
 */
@Composable
fun TimeLine(startMs: Long, durationMs: Long, color: Color, modifier: Modifier = Modifier) {
    val progress = remember(startMs, durationMs) {
        val elapsed = (System.currentTimeMillis() - startMs).coerceIn(0, durationMs)
        Animatable(elapsed.toFloat() / durationMs)
    }
    LaunchedEffect(startMs, durationMs) {
        val remaining = ((1f - progress.value) * durationMs).toInt()
        progress.animateTo(1f, tween(remaining.coerceAtLeast(1), easing = LinearEasing))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(PillShape)
            .background(if (LocalLook.current == UiLook.NEU) NeuTokens.Track else Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .clip(PillShape)
                .background(color),
        )
    }
}
