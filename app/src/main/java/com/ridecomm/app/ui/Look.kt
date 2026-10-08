package com.ridecomm.app.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app looks. Same screens and features either way; picked in Settings → Look. */
enum class UiLook(val label: String) {
    CLASSIC("Classic"),
    GLASS("Glass"),
    /** Neumorphism: light, with soft raised cards and buttons. */
    NEU("Soft"),
}

/**
 * The chosen look, for screens drawn outside Settings (the background, the home screen). Screens
 * opt in one at a time as their Glass design is done; the others stay Classic meanwhile.
 */
object LookSetting {
    private val _current = MutableStateFlow(UiLook.CLASSIC)
    val current: StateFlow<UiLook> = _current.asStateFlow()

    fun load(context: Context) {
        _current.value = Prefs.look(context)
    }

    fun set(context: Context, look: UiLook) {
        Prefs.setLook(context, look)
        _current.value = look
    }
}

/** The look the components below should draw in (Classic unless a screen provides Glass). */
val LocalLook = staticCompositionLocalOf { UiLook.CLASSIC }

/** Glass design tokens (from the Glass mock-up). */
object GlassTokens {
    val Lavender = Color(0xFFD3C9FA)
    val Lilac = Color(0xFFD8D0FB)
    val TileText = Color(0xFFEEE5FF)
    val AvatarRing = Color(0xFFB0D7FF)
    val AvatarGlow = Color(0x9048B8FF)
    val CardGlow = Color(0x25D761FF)
    val ButtonGlow = Color(0x8AFF64CB)
    val LogoGlow = Color(0x55FF43C6)
    val ButtonRim = Color(0xFFFFBDF0)

    val Action = Brush.linearGradient(listOf(Color(0xFFFFB72D), Color(0xFFFF665A), Color(0xFFF326A9)))
    val Logo = Brush.linearGradient(listOf(Color(0xFFFFBC3D), Color(0xFFFF5D5F), Color(0xFFF22EBC)))
    /** Translucent fill of cards, lit from the top-left. */
    val Card = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.245f),
        0.55f to Color.White.copy(alpha = 0.07f),
        1f to Color.White.copy(alpha = 0.133f),
    )
    /** Rim of cards: brighter where the light hits. */
    val Rim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.36f)))
    val Round = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f)))
    val Input = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.08f)))
    /** Secondary buttons: pink-to-blue glass. */
    val Secondary = Brush.linearGradient(listOf(Color(0x4DE8A6E3), Color(0x504B8FFB)))
    val Recent = Brush.linearGradient(listOf(Color(0x4AF4A2E3), Color(0x544B92FF)))
    val Tile = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f)))
    val Divider = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.27f), Color.Transparent))
    // Ride screen (from the ride mock-up)
    val Sos = Brush.linearGradient(listOf(Color(0xFFFF797C), Color(0xFFFC2488)))
    val SosGlow = Color(0x66FF4E9D)
    val MicOn = Brush.linearGradient(listOf(Color(0xFF17F4BC), Color(0xFF0CB5C3)))
    val MicOff = Brush.linearGradient(listOf(Color(0xFF9C99AE), Color(0xFF56526C)))
    val MicGlow = Color(0x8816E6CC)
    val MicLabel = Color(0xFF20F0B4)
    val Leave = Brush.linearGradient(listOf(Color(0x88FF689A), Color(0x77AE2E8A)))
    val Share = Brush.linearGradient(listOf(Color(0x995B9CF9), Color(0x88303DBB)))
    val StatIcon = Color(0xFFB9FAFF)
    val Muted = Color(0xFFD0C7E8)
    val TileBlue = Color(0x3827B8FF)
    val TileBlueIcon = Color(0xFF36EAFF)
    val TileOrange = Color(0x50FF9D4D)
    val ConnectedDot = Color(0xFF0EE7A8)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private val glassBase = TextStyle(fontFamily = Inter, color = Color(0xF5FFFFFF))

private val glassTypography = Typography(
    displayMedium = glassBase.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp),
    headlineMedium = glassBase.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    titleLarge = glassBase.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    titleMedium = glassBase.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    bodyLarge = glassBase.copy(fontSize = 16.sp),
    bodyMedium = glassBase.copy(fontSize = 14.sp, color = GlassTokens.Lilac),
    labelLarge = glassBase.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
    labelMedium = glassBase.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = GlassTokens.Lavender),
    labelSmall = glassBase.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)

/** Draws [content] in [look]: Glass also switches to its own type (Inter). */
@Composable
fun LookScope(look: UiLook, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = when (look) {
                UiLook.GLASS -> glassTypography
                UiLook.NEU -> neuTypography
                UiLook.CLASSIC -> ClassicTypography
            },
            content = content,
        )
    }
}

/**
 * A soft coloured glow behind a shape (the mock-up's coloured box-shadows). Needs Android 9+ to
 * blur; older phones simply go without it.
 */
fun Modifier.glow(color: Color, radius: Dp, shape: Shape, offsetY: Dp = 0.dp): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        this
    } else {
        drawBehind {
            val r = radius.toPx()
            if (r <= 0f) return@drawBehind
            val paint = Paint().apply {
                this.color = color
                asFrameworkPaint().maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            }
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(0f, offsetY.toPx())
                canvas.drawOutline(shape.createOutline(size, layoutDirection, this), paint)
                canvas.restore()
            }
        }
    }

/**
 * Frosted glass in the Glass look: a translucent white sheet lit from the top-left, a bright rim and
 * a faint violet glow. [tint] colours it (green for "home safe", red for errors…). The scene behind
 * is already soft, so a see-through fill reads as frosted without a real backdrop blur, on every phone.
 */
fun Modifier.frost(shape: Shape, tint: Color? = null, glow: Boolean = true): Modifier = this
    .then(if (glow) Modifier.glow(GlassTokens.CardGlow, 20.dp, shape) else Modifier)
    .clip(shape)
    .frostedBackdrop()
    .background(GlassTokens.Card)
    .then(
        if (tint != null) {
            Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.26f), tint.copy(alpha = 0.08f))))
        } else {
            Modifier
        },
    )
    .border(
        1.4.dp,
        if (tint != null) Brush.linearGradient(listOf(tint.copy(alpha = 0.8f), tint.copy(alpha = 0.35f))) else GlassTokens.Rim,
        shape,
    )

/** Where the Glass scene is on screen, so frosted cards can show a softened copy of what's behind them. */
class GlassSceneInfo(val style: GlassSceneStyle) {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(Size.Zero)
}

/** The home screen and the ride screen have slightly different light (from their mock-ups). */
enum class GlassSceneStyle { HOME, RIDE }

val LocalGlassScene = staticCompositionLocalOf<GlassSceneInfo?> { null }

private val SceneBase = Brush.linearGradient(
    0f to Color(0xFF2C257F),
    0.55f to Color(0xFF20165F),
    1f to Color(0xFF151353),
)
private val RideSceneBase = Brush.linearGradient(
    0f to Color(0xFF192B8E),
    0.62f to Color(0xFF251253),
    1f to Color(0xFF080D4B),
)

/**
 * Paints the Glass scene: deep indigo with blue, magenta and purple light, and four glowing spheres
 * at the edges (positions from the mock-up, which is 400 wide). [soft] blurs the spheres' edges, as
 * seen through frosted glass.
 */
private fun DrawScope.drawGlassScene(sceneSize: Size, soft: Boolean, style: GlassSceneStyle) {
    if (style == GlassSceneStyle.RIDE) {
        drawRideScene(sceneSize, soft)
        return
    }
    drawRect(SceneBase, size = sceneSize)
    val u = sceneSize.width / 400f
    fun light(color: Color, x: Float, y: Float, r: Float) {
        val center = Offset(sceneSize.width * x, sceneSize.height * y)
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, r * u), r * u, center)
    }
    light(Color(0xFF5278FF), 0.02f, 0.13f, 330f)
    light(Color(0xFFBB3CDA), 0.95f, 0.27f, 300f)
    light(Color(0xFF9D27CB), 0.15f, 0.84f, 340f)
    light(Color(0xFF1266E7), 1.0f, 0.88f, 340f)

    /** A glowing sphere lit at ([hx], [hy]) of its box, centred at ([left] + d/2, [centerY]). */
    fun orb(left: Float, centerY: Float, d: Float, lit: Color, mid: Color, hx: Float, hy: Float, glow: Color?) {
        val c = Offset((left + d / 2) * u, centerY)
        val r = d / 2 * u
        if (glow != null) drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), c, r * 1.35f), r * 1.35f, c)
        val shader = Brush.radialGradient(
            0f to lit,
            0.58f to mid,
            0.73f to mid.copy(alpha = 0f),
            center = Offset(c.x - r + 2 * r * hx, c.y - r + 2 * r * hy),
            // CSS "circle at x y" reaches to the farthest corner of the sphere's box.
            radius = 2 * r * kotlin.math.hypot(maxOf(hx, 1 - hx), maxOf(hy, 1 - hy)),
        )
        if (soft && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val paint = Paint().apply { asFrameworkPaint().maskFilter = BlurMaskFilter(24.dp.toPx(), BlurMaskFilter.Blur.NORMAL) }
            shader.applyTo(size, paint, 1f)
            drawIntoCanvas { it.drawCircle(c, r, paint) }
        } else {
            drawCircle(shader, r, c)
        }
    }
    orb(-130f, -25f * u, 390f, Color(0xFF9B9EFF), Color(0xFF6A61FF), 0.65f, 0.70f, Color(0x55A278FF))
    orb(400f + 230f - 340f, (270f + 170f) * u, 340f, Color(0xFFFF7FF0), Color(0xFFD936D7), 0.30f, 0.45f, Color(0x44EF61EE))
    orb(-170f, sceneSize.height - (90f + 140f) * u, 280f, Color(0xFFF983E3), Color(0xFFA927C6), 0.70f, 0.35f, null)
    orb(400f + 170f - 240f, sceneSize.height - (220f + 120f) * u, 240f, Color(0xFF7DE9FF), Color(0xFF228AFF), 0.30f, 0.40f, null)
}

/** The ride screen's scene: blue-violet with magenta light, a pink sphere at the top right and one at the bottom left. */
private fun DrawScope.drawRideScene(sceneSize: Size, soft: Boolean) {
    drawRect(RideSceneBase, size = sceneSize)
    val u = sceneSize.width / 400f
    fun light(color: Color, x: Float, y: Float, r: Float) {
        val center = Offset(sceneSize.width * x, sceneSize.height * y)
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, r * u), r * u, center)
    }
    light(Color(0xFF454BFA), 0.0f, 0.12f, 380f)
    light(Color(0xFFB72AB9), 1.1f, 0.24f, 380f)
    light(Color(0xFFB51CA8), 0.05f, 0.85f, 340f)
    light(Color(0xFF0875F5), 1.05f, 0.90f, 380f)
    fun orb(c: Offset, r: Float, color: Color, hx: Float, hy: Float, fade: Float, glow: Color?) {
        if (glow != null) drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), c, r * 1.5f), r * 1.5f, c)
        val shader = Brush.radialGradient(
            0f to color,
            fade to color.copy(alpha = 0f),
            center = Offset(c.x - r + 2 * r * hx, c.y - r + 2 * r * hy),
            radius = 2 * r * kotlin.math.hypot(maxOf(hx, 1 - hx), maxOf(hy, 1 - hy)),
        )
        if (soft && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val paint = Paint().apply { asFrameworkPaint().maskFilter = BlurMaskFilter(24.dp.toPx(), BlurMaskFilter.Blur.NORMAL) }
            shader.applyTo(size, paint, 1f)
            drawIntoCanvas { it.drawCircle(c, r, paint) }
        } else {
            drawCircle(shader, r, c)
        }
    }
    // 370 wide, top -230, right -160; 320 wide, bottom -180, left -190 (mock-up px at 400 wide).
    orb(Offset((400f + 160f - 185f) * u, (-230f + 185f) * u), 185f * u, Color(0xFFFF66E7), 0.40f, 0.65f, 0.65f, Color(0x66E336E4))
    orb(Offset((-190f + 160f) * u, sceneSize.height + (180f - 160f) * u), 160f * u, Color(0xFFF83AD9), 0.5f, 0.5f, 0.72f, null)
}

/** The Glass background, behind everything drawn in [content]. */
@Composable
fun GlassScene(modifier: Modifier = Modifier, style: GlassSceneStyle = GlassSceneStyle.HOME, content: @Composable BoxScope.() -> Unit) {
    val info = remember(style) { GlassSceneInfo(style) }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                info.origin = it.positionInRoot()
                info.size = it.size.toSize()
            }
            .drawBehind { drawGlassScene(size, soft = false, style) },
    ) {
        CompositionLocalProvider(LocalGlassScene provides info) { content() }
    }
}

/**
 * Shows the scene softened behind a frosted surface: the stand-in for a real backdrop blur, which
 * Android only offers from version 12 and not for what's behind a view.
 */
internal fun Modifier.frostedBackdrop(): Modifier = composed {
    val scene = LocalGlassScene.current ?: return@composed Modifier
    var at by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { at = it.positionInRoot() }
        .drawBehind {
            val shift = scene.origin - at
            translate(shift.x, shift.y) { drawGlassScene(scene.size, soft = true, scene.style) }
        }
}

/** The app background for [look]. */
@Composable
fun LookBackground(
    look: UiLook,
    modifier: Modifier = Modifier,
    style: GlassSceneStyle = GlassSceneStyle.HOME,
    content: @Composable BoxScope.() -> Unit,
) {
    when (look) {
        UiLook.GLASS -> GlassScene(modifier, style, content)
        UiLook.NEU -> NeuScene(modifier, content)
        UiLook.CLASSIC -> GlassBackground(modifier, content)
    }
}

/**
 * Any glass surface in the Glass look (chips, tiles, pills…): frosted white, tinted by [tint] in
 * proportion to [fillAlpha] (selected chips are tinted more), with a light rim.
 */
internal fun Modifier.glassLookSurface(shape: Shape, tint: Color, fillAlpha: Float): Modifier {
    val tinted = tint != Color.White
    return this
        .clip(shape)
        .frostedBackdrop()
        .background(GlassTokens.Card)
        .then(
            if (tinted) {
                Modifier.background(
                    Brush.linearGradient(listOf(tint.copy(alpha = (fillAlpha * 1.5f).coerceAtMost(0.55f)), tint.copy(alpha = fillAlpha * 0.5f))),
                )
            } else {
                Modifier
            },
        )
        .border(
            1.dp,
            if (tinted && fillAlpha >= 0.2f) {
                Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.4f)))
            } else {
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.3f)))
            },
            shape,
        )
}

/** Pop-up panel in the Glass look (from the ride mock-up's dialog): violet glass, nearly opaque. */
val GlassDialogPanel = Brush.linearGradient(listOf(Color(0xF0463FA6), Color(0xF0622B86)))

/** Panels over the map in the Glass look: violet, opaque enough to read over streets. */
val GlassMapPanel = Color(0xE8302873)
