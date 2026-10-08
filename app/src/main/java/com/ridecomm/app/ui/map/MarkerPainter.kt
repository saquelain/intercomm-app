package com.ridecomm.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import kotlin.math.cos
import kotlin.math.sin

/** Something to draw on the map, already placed in screen pixels. */
sealed interface MapMarker {
    val x: Float
    val y: Float

    data class Rider(
        override val x: Float,
        override val y: Float,
        val name: String,
        val photo: Bitmap?,
        val talking: Boolean,
        /** Direction of travel while moving, degrees from north. */
        val headingDeg: Float?,
        /** No update for a while: drawn faded. */
        val stale: Boolean,
        /** "Lead" or "Sweep", shown as a badge. */
        val role: String? = null,
    ) : MapMarker

    data class Me(
        override val x: Float,
        override val y: Float,
        val headingDeg: Float?,
        val accuracyPx: Float,
        val name: String = "",
        val photo: Bitmap? = null,
    ) : MapMarker

    /** The regroup point (amber flag), or with [destination] where the group is heading (green finish flag). */
    data class Regroup(override val x: Float, override val y: Float, val label: String, val detail: String, val destination: Boolean = false) : MapMarker

    data class Hazard(override val x: Float, override val y: Float, val icon: Int, val label: String) : MapMarker
}

/**
 * Draws rider avatars (photo or initial, name tag, direction arrow), my position and the regroup
 * flag. Kept apart from the map library so it can be drawn and checked on its own.
 */
class MarkerPainter(private val context: Context) {
    private val d = context.resources.displayMetrics.density
    private val font = ResourcesCompat.getFont(context, R.font.outfit_semibold)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = font
    }
    private val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 16, 14, 36) }
    private val rect = RectF()
    private val path = Path()
    private val flag = ContextCompat.getDrawable(context, R.drawable.ms_flag)!!.mutate()
    private val finish = ContextCompat.getDrawable(context, R.drawable.ms_sports_score)!!.mutate()

    fun draw(canvas: Canvas, markers: List<MapMarker>) {
        // Regroup flag at the back, me on top of everyone else.
        markers.filterIsInstance<MapMarker.Hazard>().forEach { drawHazard(canvas, it) }
        markers.filterIsInstance<MapMarker.Regroup>().forEach { drawRegroup(canvas, it) }
        markers.filterIsInstance<MapMarker.Rider>().forEach { drawRider(canvas, it) }
        markers.filterIsInstance<MapMarker.Me>().forEach { drawMe(canvas, it) }
    }

    private fun drawRider(canvas: Canvas, m: MapMarker.Rider) {
        val r = AVATAR_DP / 2 * d
        val alpha = if (m.stale) 140 else 255
        canvas.drawCircle(m.x, m.y + 2 * d, r + 3 * d, shadow)
        // Direction of travel: a small arrow on the rim.
        m.headingDeg?.let { h ->
            val a = Math.toRadians(h.toDouble() - 90)
            val tipX = m.x + cos(a).toFloat() * (r + 11 * d)
            val tipY = m.y + sin(a).toFloat() * (r + 11 * d)
            val left = a + Math.toRadians(140.0)
            val right = a - Math.toRadians(140.0)
            path.reset()
            path.moveTo(tipX, tipY)
            path.lineTo(m.x + cos(left).toFloat() * (r - 1 * d) + cos(a).toFloat() * 4 * d, m.y + sin(left).toFloat() * (r - 1 * d) + sin(a).toFloat() * 4 * d)
            path.lineTo(m.x + cos(right).toFloat() * (r - 1 * d) + cos(a).toFloat() * 4 * d, m.y + sin(right).toFloat() * (r - 1 * d) + sin(a).toFloat() * 4 * d)
            path.close()
            fill.shader = null
            fill.color = Color.argb(alpha, 0xFF, 0xFF, 0xFF)
            canvas.drawPath(path, fill)
        }
        val photo = m.photo
        fill.shader = if (photo != null) {
            BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val scale = (r * 2) / minOf(photo.width, photo.height)
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(m.x - photo.width * scale / 2, m.y - photo.height * scale / 2)
                })
            }
        } else {
            LinearGradient(m.x - r, m.y - r, m.x + r, m.y + r, VIOLET, CYAN, Shader.TileMode.CLAMP)
        }
        fill.alpha = alpha
        canvas.drawCircle(m.x, m.y, r, fill)
        fill.shader = null
        stroke.strokeWidth = 3 * d
        stroke.color = if (m.talking) GREEN else Color.WHITE
        stroke.alpha = alpha
        canvas.drawCircle(m.x, m.y, r, stroke)
        if (photo == null) {
            text.textSize = 17 * d
            text.alpha = alpha
            canvas.drawText(m.name.take(1).uppercase(), m.x, m.y + 6 * d, text)
        }
        nameTag(canvas, m.x, m.y + r + 6 * d, m.name.substringBefore(' ').take(12), m.role, alpha)
        if (m.role != null) {
            // A small coloured dot on the rim, so lead and sweep stand out even when zoomed out.
            fill.shader = null
            fill.color = if (m.role == "Lead") ORANGE else CYAN
            canvas.drawCircle(m.x + r * 0.72f, m.y - r * 0.72f, 7 * d, fill)
            stroke.strokeWidth = 2 * d
            stroke.color = Color.WHITE
            canvas.drawCircle(m.x + r * 0.72f, m.y - r * 0.72f, 7 * d, stroke)
        }
    }

    private val hazardIcons = mutableMapOf<Int, android.graphics.drawable.Drawable>()

    private fun drawHazard(canvas: Canvas, m: MapMarker.Hazard) {
        // A rounded warning tile, red-orange, with the hazard's icon and name.
        val h = 15 * d
        rect.set(m.x - h, m.y - h, m.x + h, m.y + h)
        canvas.drawRoundRect(rect.apply { offset(0f, 2 * d) }, 9 * d, 9 * d, shadow)
        rect.set(m.x - h, m.y - h, m.x + h, m.y + h)
        fill.shader = LinearGradient(m.x - h, m.y - h, m.x + h, m.y + h, Color.rgb(0xFF, 0x5E, 0x3A), Color.rgb(0xE5, 0x24, 0x5E), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 9 * d, 9 * d, fill)
        fill.shader = null
        stroke.strokeWidth = 2 * d
        stroke.color = Color.WHITE
        canvas.drawRoundRect(rect, 9 * d, 9 * d, stroke)
        val icon = hazardIcons.getOrPut(m.icon) { ContextCompat.getDrawable(context, m.icon)!!.mutate() }
        val s = (10 * d).toInt()
        icon.setBounds((m.x - s).toInt(), (m.y - s).toInt(), (m.x + s).toInt(), (m.y + s).toInt())
        icon.setTint(Color.WHITE)
        icon.draw(canvas)
        nameTag(canvas, m.x, m.y + h + 4 * d, m.label, null, 235)
    }

    /** Me: my photo (or initial) like the other riders, with an orange ring and a "You" tag. */
    private fun drawMe(canvas: Canvas, m: MapMarker.Me) {
        if (m.accuracyPx > 24 * d) {
            fill.shader = null
            fill.color = Color.argb(40, 0xFF, 0x8A, 0x1F)
            canvas.drawCircle(m.x, m.y, m.accuracyPx, fill)
        }
        val r = AVATAR_DP / 2 * d
        m.headingDeg?.let { h ->
            // A soft cone showing which way I'm going.
            val a = Math.toRadians(h.toDouble() - 90)
            path.reset()
            path.moveTo(m.x, m.y)
            for (i in -30..30 step 10) {
                val b = a + Math.toRadians(i.toDouble())
                path.lineTo(m.x + cos(b).toFloat() * (r + 30 * d), m.y + sin(b).toFloat() * (r + 30 * d))
            }
            path.close()
            fill.shader = null
            fill.color = Color.argb(90, 0xFF, 0x8A, 0x1F)
            canvas.drawPath(path, fill)
        }
        canvas.drawCircle(m.x, m.y + 2 * d, r + 5 * d, shadow)
        // Orange-pink outer ring marks me apart from everyone else.
        fill.shader = LinearGradient(m.x - r, m.y - r, m.x + r, m.y + r, ORANGE, PINK, Shader.TileMode.CLAMP)
        fill.alpha = 255
        canvas.drawCircle(m.x, m.y, r + 4 * d, fill)
        val photo = m.photo
        fill.shader = if (photo != null) {
            BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val scale = (r * 2) / minOf(photo.width, photo.height)
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(m.x - photo.width * scale / 2, m.y - photo.height * scale / 2)
                })
            }
        } else {
            LinearGradient(m.x - r, m.y - r, m.x + r, m.y + r, ORANGE, PINK, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(m.x, m.y, r, fill)
        fill.shader = null
        stroke.strokeWidth = 2.5f * d
        stroke.color = Color.WHITE
        stroke.alpha = 255
        canvas.drawCircle(m.x, m.y, r, stroke)
        if (photo == null) {
            text.textSize = 17 * d
            text.alpha = 255
            canvas.drawText(m.name.take(1).uppercase().ifBlank { "•" }, m.x, m.y + 6 * d, text)
        }
        nameTag(canvas, m.x, m.y + r + 8 * d, "You", null, 255)
    }

    private fun drawRegroup(canvas: Canvas, m: MapMarker.Regroup) {
        // A pin: circle on a short stem, with the point at the exact spot.
        val r = 20 * d
        val cy = m.y - 30 * d
        val color = if (m.destination) GREEN else AMBER
        stroke.strokeWidth = 3 * d
        stroke.color = color
        canvas.drawLine(m.x, cy + r, m.x, m.y, stroke)
        fill.shader = null
        fill.color = color
        canvas.drawCircle(m.x, m.y, 4 * d, fill)
        canvas.drawCircle(m.x, cy + 2 * d, r + 3 * d, shadow)
        canvas.drawCircle(m.x, cy, r, fill)
        stroke.color = Color.WHITE
        canvas.drawCircle(m.x, cy, r, stroke)
        val s = (12 * d).toInt()
        val icon = if (m.destination) finish else flag
        icon.setBounds((m.x - s).toInt(), (cy - s).toInt(), (m.x + s).toInt(), (cy + s).toInt())
        icon.setTint(if (m.destination) Color.rgb(0x04, 0x2A, 0x1B) else Color.rgb(0x2A, 0x1E, 0x05))
        icon.draw(canvas)
        nameTag(canvas, m.x, cy - r - 30 * d, m.label, m.detail, 255)
    }

    /** A dark rounded tag with one or two lines, centred at [cx], top at [top]. */
    private fun nameTag(canvas: Canvas, cx: Float, top: Float, title: String, detail: String?, alpha: Int) {
        text.textSize = 13 * d
        val small = 11 * d
        val w1 = text.measureText(title)
        text.textSize = small
        val w2 = detail?.let { text.measureText(it) } ?: 0f
        val w = maxOf(w1, w2) + 16 * d
        val h = if (detail != null) 36 * d else 22 * d
        rect.set(cx - w / 2, top, cx + w / 2, top + h)
        tagBg.alpha = (225 * alpha / 255)
        canvas.drawRoundRect(rect, h / 2, h / 2, tagBg)
        text.textSize = 13 * d
        text.color = Color.WHITE
        text.alpha = alpha
        canvas.drawText(title, cx, top + 15.5f * d, text)
        if (detail != null) {
            text.textSize = small
            text.color = Color.argb(200, 255, 255, 255)
            canvas.drawText(detail, cx, top + 29 * d, text)
            text.color = Color.WHITE
        }
    }

    companion object {
        const val AVATAR_DP = 40f
        private val ORANGE = Color.rgb(0xFF, 0x8A, 0x1F)
        private val PINK = Color.rgb(0xFF, 0x3D, 0x81)
        private val VIOLET = Color.rgb(0x7C, 0x5C, 0xFF)
        private val CYAN = Color.rgb(0x22, 0xD3, 0xEE)
        private val GREEN = Color.rgb(0x34, 0xE8, 0x9E)
        private val AMBER = Color.rgb(0xFF, 0xC9, 0x3C)
    }
}
