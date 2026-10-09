package com.ridecomm.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import com.ridecomm.app.trip.RideLog
import com.ridecomm.app.trip.RideSummary
import com.ridecomm.app.trip.RoutePoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * The ride as a picture to share (WhatsApp, Instagram): the route as a glowing line (no map, so
 * nothing else of where you live shows), the date and the numbers.
 */
object RidePicture {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    /** The route squeezed into [box], north up, keeping its shape (longitude shrinks away from the equator). */
    fun routePath(route: List<RoutePoint>, box: RectF): Path {
        val path = Path()
        if (route.isEmpty()) return path
        val midLat = (route.maxOf { it.lat } + route.minOf { it.lat }) / 2
        val kx = cos(Math.toRadians(midLat))
        val xs = route.map { it.lon * kx }
        val ys = route.map { it.lat }
        val spanX = max(xs.max() - xs.min(), 1e-6)
        val spanY = max(ys.max() - ys.min(), 1e-6)
        val scale = min(box.width() / spanX, box.height() / spanY)
        val offX = box.left + (box.width() - spanX * scale) / 2
        val offY = box.top + (box.height() - spanY * scale) / 2
        route.indices.forEach { i ->
            val x = (offX + (xs[i] - xs.min()) * scale).toFloat()
            val y = (offY + (ys.max() - ys[i]) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }

    fun render(context: Context, ride: RideSummary): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val bold = ResourcesCompat.getFont(context, R.font.outfit_bold) ?: Typeface.DEFAULT_BOLD
        val regular = ResourcesCompat.getFont(context, R.font.outfit_regular) ?: Typeface.DEFAULT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Night sky with a violet and a pink glow, like the app.
        paint.shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), 0xFF0E0B22.toInt(), 0xFF1B1340.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        fun glow(x: Float, y: Float, r: Float, color: Int) {
            paint.shader = RadialGradient(x, y, r, color, 0x00000000, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, paint)
        }
        glow(150f, 120f, 650f, 0x667C5CFF)
        glow(1000f, 900f, 600f, 0x55FF3D81)
        paint.shader = null

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) {
            paint.typeface = face
            paint.textSize = size
            paint.color = color
            paint.textAlign = align
            paint.letterSpacing = spacing
            c.drawText(s, x, y, paint)
            paint.letterSpacing = 0f
        }
        text("RIDECOMM", 72f, 120f, 34f, 0xB3FFFFFF.toInt(), bold, spacing = 0.18f)
        text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(ride.startedAtMs)), 72f, 200f, 64f, 0xFFFFFFFF.toInt(), bold)
        val with = ride.riders.take(4).joinToString(", ").let { if (it.isBlank()) "" else " · with $it" }
        text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ride.startedAtMs)) + with, 72f, 258f, 36f, 0xB3FFFFFF.toInt(), regular)

        // The route.
        val box = RectF(110f, 320f, WIDTH - 110f, 860f)
        val path = routePath(ride.route, box)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.shader = LinearGradient(box.left, box.top, box.right, box.bottom, 0xFFFF8A1F.toInt(), 0xFFFF3D81.toInt(), Shader.TileMode.CLAMP)
        paint.strokeWidth = 34f
        paint.maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        paint.alpha = 150
        c.drawPath(path, paint)
        paint.maskFilter = null
        paint.alpha = 255
        paint.strokeWidth = 12f
        c.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        if (ride.route.isNotEmpty()) {
            val ends = FloatArray(2)
            val measure = android.graphics.PathMeasure(path, false)
            measure.getPosTan(0f, ends, null)
            paint.color = 0xFF34E89E.toInt()
            c.drawCircle(ends[0], ends[1], 18f, paint)
            measure.getPosTan(measure.length, ends, null)
            paint.color = 0xFFFFFFFF.toInt()
            c.drawCircle(ends[0], ends[1], 18f, paint)
        } else {
            text("No route: location was off", WIDTH / 2f, 600f, 40f, 0x99FFFFFF.toInt(), regular, Paint.Align.CENTER)
        }

        // Six numbers in two rows.
        val tiles = listOf(
            String.format(Locale.US, "%.1f km", ride.distanceM / 1000) to "Distance",
            RideLog.duration(ride.movingMs) to "Riding time",
            "${ride.averageKmh.toInt()} km/h" to "Average",
            "${ride.topKmh.toInt()} km/h" to "Top speed",
            RideLog.duration(ride.totalMs) to "Total time",
            "${ride.stops.size}" to if (ride.stops.size == 1) "Stop" else "Stops",
        )
        val tileW = (WIDTH - 72f * 2 - 24f * 2) / 3
        tiles.forEachIndexed { i, (value, label) ->
            val x = 72f + (i % 3) * (tileW + 24f)
            val y = 920f + (i / 3) * 170f
            paint.color = 0x1FFFFFFF
            c.drawRoundRect(RectF(x, y, x + tileW, y + 146f), 32f, 32f, paint)
            text(value, x + 28f, y + 70f, 50f, 0xFFFFFFFF.toInt(), bold)
            text(label, x + 28f, y + 116f, 30f, 0xB3FFFFFF.toInt(), regular)
        }
        text("Recorded with RideComm · ride ${ride.code}", WIDTH / 2f, HEIGHT - 50f, 30f, 0x80FFFFFF.toInt(), regular, Paint.Align.CENTER)
        return bitmap
    }

    /** Saves the picture where the share sheet can read it, and opens the share sheet. */
    fun share(context: Context, ride: RideSummary) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ride-${ride.id}.png")
        file.outputStream().use { render(context, ride).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, "%.1f km ride with RideComm", ride.distanceM / 1000))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share ride"))
    }
}
