package com.ridecomm.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** One choice in the slide menu. [spoken] is announced after it runs. */
class SlideOption(
    val emoji: String,
    val label: String,
    val color: Int,
    val spoken: String,
    val run: () -> Unit,
)

/**
 * Full-screen, non-touchable overlay that draws the options in two fans around the floating button
 * ([inner] for a short slide, [outer] for a long one). The button's window keeps receiving the
 * finger's movement; [pick] maps the finger position to an option by direction and distance
 * ([RingLayout]), so it only has to point roughly at an option — easy with gloves.
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
    /** Text on the button in the middle ("Slide" or "Close"). */
    var hint = "Slide"

    var selected = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        color = Color.WHITE
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28 * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15 * density
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4 * density, 0f, 0f, Color.BLACK)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15 * density
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
    }
    private val centerRadius = 32 * density
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
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        getLocationOnScreen(location)
        val cx = centerX - location[0]
        val cy = centerY - location[1]
        // Redraw the button on top of the dim layer so the finger's starting point stays visible.
        circlePaint.color = Color.rgb(0xFF, 0x8A, 0x1F)
        canvas.drawCircle(cx, cy, centerRadius, circlePaint)
        canvas.drawCircle(cx, cy, centerRadius, ringPaint)
        canvas.drawText(hint, cx, cy + labelPaint.textSize / 3, hintPaint)
        val rings = rings
        options.forEachIndexed { i, option ->
            val a = Math.toRadians(rings.angleOf(i).toDouble())
            val x = cx + rings.radiusOf(i) * cos(a).toFloat()
            val y = cy + rings.radiusOf(i) * sin(a).toFloat()
            val isSelected = i == selected
            val r = if (isSelected) itemRadius * 1.25f else itemRadius
            circlePaint.color = if (isSelected) option.color else Color.argb(235, 38, 42, 47)
            canvas.drawCircle(x, y, r, circlePaint)
            if (isSelected) canvas.drawCircle(x, y, r, ringPaint)
            canvas.drawText(option.emoji, x, y + emojiPaint.textSize / 3, emojiPaint)
            canvas.drawText(option.label, x, y + r + labelPaint.textSize + 2 * density, labelPaint)
        }
    }

    companion object {
        const val INNER_RADIUS_DP = 125f
        const val OUTER_RADIUS_DP = 235f
        const val ITEM_RADIUS_DP = 38f
        /** Finger must move this far from the button before anything is selected. */
        private const val DEAD_ZONE_DP = 56f
    }
}
