package com.ridecomm.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One choice in the slide menu. [spoken] is announced after it runs (skipped if empty). */
class SlideOption(
    @DrawableRes val icon: Int,
    val label: String,
    val color: Int,
    val spoken: String,
    val run: () -> Unit,
)

/**
 * Full-screen overlay that draws the options in two fans of frosted-glass circles around the
 * floating button ([inner] for a short reach, [outer] for a long one). [pick] maps a finger
 * position to an option by direction and distance ([RingLayout]), so it only has to point roughly
 * at an option — easy with gloves.
 */
@SuppressLint("ViewConstructor")
class SlideMenuView(
    context: Context,
    private val inner: List<SlideOption>,
    private val outer: List<SlideOption>,
) : View(context) {

    /** All options, numbered as [pick] and [selected] use them: inner first, then outer. */
    val options: List<SlideOption> = inner + outer

    private val density = resources.displayMetrics.density
    private val itemRadius = ITEM_RADIUS_DP * density
    private val deadZone = DEAD_ZONE_DP * density

    /** Centre of the floating button, in screen coordinates. */
    var centerX = 0f
    var centerY = 0f
    /** The fan opens away from the screen edge the button sits on. */
    var onLeftEdge = false
    /** Icon in the middle: a close mark when the menu was opened with a tap. */
    var centerIsClose = false

    var selected = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val font = ResourcesCompat.getFont(context, R.font.outfit_semibold)
    private val icons: List<Drawable> = options.map { ContextCompat.getDrawable(context, it.icon)!!.mutate() }
    private val closeIcon = ContextCompat.getDrawable(context, R.drawable.ms_close)!!.mutate()
    private val brandIcon = ContextCompat.getDrawable(context, R.drawable.ms_two_wheeler)!!.mutate()

    private val scrim = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12 * density
        color = Color.WHITE
        typeface = font
    }
    private val centerRadius = 34 * density
    private val location = IntArray(2)

    private val rings
        get() = RingLayout(
            inner = inner.size,
            outer = outer.size,
            onLeftEdge = onLeftEdge,
            innerRadius = INNER_RADIUS_DP * density,
            outerRadius = OUTER_RADIUS_DP * density,
            deadZone = deadZone,
        )

    /** Which option the finger at screen position ([rawX], [rawY]) points to, or -1. */
    fun pick(rawX: Float, rawY: Float): Int = rings.pick(rawX - centerX, rawY - centerY)

    override fun onDraw(canvas: Canvas) {
        scrim.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.argb(215, 7, 10, 20), Color.argb(230, 14, 11, 34), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        getLocationOnScreen(location)
        val cx = centerX - location[0]
        val cy = centerY - location[1]

        // The floating button, redrawn above the scrim so the starting point stays visible.
        drawGlassCircle(canvas, cx, cy, centerRadius, highlight = null)
        drawIcon(canvas, if (centerIsClose) closeIcon else brandIcon, cx, cy, centerRadius * 0.95f)

        val rings = rings
        options.forEachIndexed { i, option ->
            val a = Math.toRadians(rings.angleOf(i).toDouble())
            val x = cx + rings.radiusOf(i) * cos(a).toFloat()
            val y = cy + rings.radiusOf(i) * sin(a).toFloat()
            val isSelected = i == selected
            val r = if (isSelected) itemRadius * 1.15f else itemRadius
            drawGlassCircle(canvas, x, y, r, highlight = if (isSelected) option.color else null)
            // Icon and caption both inside the circle, so neighbouring options never overlap.
            drawIcon(canvas, icons[i], x, y - r * 0.18f, r * 0.72f, tint = if (isSelected) Color.WHITE else option.color)
            canvas.drawText(option.label, x, y + r * 0.58f, labelPaint)
        }
    }

    /** Frosted circle; [highlight] fills it with a colour and a soft glow when selected. */
    private fun drawGlassCircle(canvas: Canvas, x: Float, y: Float, r: Float, highlight: Int?) {
        if (highlight != null) {
            glow.shader = RadialGradient(x, y, r * 1.9f, withAlpha(highlight, 140), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r * 1.9f, glow)
            fill.shader = LinearGradient(x - r, y - r, x + r, y + r, highlight, withAlpha(highlight, 200), Shader.TileMode.CLAMP)
        } else {
            fill.shader = LinearGradient(
                x - r, y - r, x + r, y + r,
                Color.argb(60, 255, 255, 255), Color.argb(22, 255, 255, 255), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(x, y, r, fill)
        rim.shader = LinearGradient(
            x - r, y - r, x + r, y + r,
            Color.argb(if (highlight != null) 230 else 110, 255, 255, 255), Color.argb(20, 255, 255, 255), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, r, rim)
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable, x: Float, y: Float, size: Float, tint: Int = Color.WHITE) {
        val half = (size / 2).roundToInt()
        icon.setTint(tint)
        icon.setBounds(x.roundToInt() - half, y.roundToInt() - half, x.roundToInt() + half, y.roundToInt() + half)
        icon.draw(canvas)
    }

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        const val INNER_RADIUS_DP = 135f
        const val OUTER_RADIUS_DP = 252f
        const val ITEM_RADIUS_DP = 44f
        /** Finger must move this far from the button before anything is selected. */
        private const val DEAD_ZONE_DP = 56f
    }
}
