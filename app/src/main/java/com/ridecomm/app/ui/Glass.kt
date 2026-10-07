package com.ridecomm.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val CardShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(50)

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
            glow(Palette.Violet, 0.0f, 0.05f, 0.95f, 0.60f)
            glow(Palette.Pink, 1.0f, 0.30f, 0.75f, 0.42f)
            glow(Palette.Orange, 0.10f, 0.62f, 0.60f, 0.30f)
            glow(Palette.Cyan, 0.95f, 0.95f, 0.75f, 0.32f)
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
): Modifier = this
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
    Column(
        modifier
            .fillMaxWidth()
            .glass(tint = tint, fillAlpha = fillAlpha)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

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
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(height)
            .clip(PillShape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Ico(icon, 24.dp, contentColor)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, softWrap = false)
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
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(height)
            .glass(PillShape, tint = tint, fillAlpha = if (tint == Color.White) 0.10f else 0.22f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Ico(icon, 22.dp, contentColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, softWrap = false)
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
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(size)
            .then(
                if (brush != null) {
                    Modifier.clip(CircleShape).background(brush).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                } else {
                    Modifier.glass(CircleShape, tint = tint, fillAlpha = if (tint == Color.White) 0.10f else 0.25f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, iconSize, Color.White, description) }
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
        Ico(icon, 28.dp, accent)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, modifier = modifier)
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            cursorBrush = SolidColor(Palette.Orange),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(18.dp), fillAlpha = 0.06f, rimAlpha = 0.25f)
                        .padding(PaddingValues(horizontal = 18.dp, vertical = 16.dp)),
                    contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = style.copy(color = Palette.TextTertiary))
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(CardShape)
                .background(Brush.linearGradient(listOf(Color(0xF21B1F33), Color(0xF2120F26))))
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))), CardShape)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Round avatar with the rider's initial on a gradient. */
@Composable
fun Avatar(name: String, size: Dp, brush: Brush, ring: Color?) {
    Box(
        Modifier
            .size(size)
            .then(if (ring != null) Modifier.border(3.dp, ring, CircleShape).padding(5.dp) else Modifier)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
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
            .background(Color.White.copy(alpha = 0.12f)),
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
