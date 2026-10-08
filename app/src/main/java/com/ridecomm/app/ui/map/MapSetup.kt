package com.ridecomm.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.view.MotionEvent
import com.ridecomm.app.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File

/**
 * One-time map library setup and the map style. (CARTO's ready-made dark tiles now need an API
 * key, so the free OpenStreetMap tiles are darkened on the phone instead.)
 */
object MapSetup {
    private var configured = false

    /** Tiles are cached in the app's own cache folder (about 60 MB at most), so routes ridden before load offline. */
    fun configure(context: Context) {
        if (configured) return
        configured = true
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            val base = File(context.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileFileSystemCacheMaxBytes = 60L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 45L * 1024 * 1024
        }
    }

    /** The standard OpenStreetMap map: free, no API key, sent with RideComm's own user agent as OSM asks. */
    val tiles: OnlineTileSourceBase get() = TileSourceFactory.MAPNIK

    /**
     * Turns the light OpenStreetMap style dark to match the app: invert, then rotate the hues back
     * (so water stays blue and parks green), softened and slightly dimmed. Roads end up light on dark.
     */
    val darkFilter: ColorMatrixColorFilter by lazy {
        val invert = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // Hue rotation by 180° (the CSS hue-rotate matrix with cos = -1, sin = 0).
        val hue = ColorMatrix(
            floatArrayOf(
                -0.574f, 1.430f, 0.144f, 0f, 0f,
                0.426f, 0.430f, 0.144f, 0f, 0f,
                0.426f, 1.430f, -0.856f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val calm = ColorMatrix().apply { setSaturation(0.55f) }
        val dim = ColorMatrix().apply { setScale(0.82f, 0.85f, 0.95f, 1f) }
        invert.postConcat(hue)
        invert.postConcat(calm)
        invert.postConcat(dim)
        ColorMatrixColorFilter(invert)
    }

    const val ATTRIBUTION = "© OpenStreetMap contributors"
}

/**
 * Draws [markers] (riders, me, regroup point) over the map, and turns a long-press into a
 * regroup point request.
 */
class GroupOverlay(context: Context, private val onLongPress: (Spot) -> Unit) : Overlay() {
    private val painter = MarkerPainter(context)
    private val point = Point()

    /** Map positions to draw; set from the UI, then the map is invalidated. */
    var places: List<Place> = emptyList()

    /**
     * A marker at a map position (converted to screen pixels when drawn). [key] stays the same while
     * it moves (a rider's id), so a map that keeps its own markers (Google) can move it.
     */
    data class Place(val lat: Double, val lon: Double, val key: String, val make: (x: Float, y: Float, metresPerPixel: Float) -> MapMarker)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        // Ground distance across 100 px in the middle of the screen.
        val y = mapView.height / 2
        val a = projection.fromPixels(0, y) as GeoPoint
        val b = projection.fromPixels(100, y) as GeoPoint
        val metresPerPixel = (a.distanceToAsDouble(b) / 100).toFloat().coerceAtLeast(0.01f)
        val markers = places.map { p ->
            projection.toPixels(GeoPoint(p.lat, p.lon), point)
            p.make(point.x.toFloat(), point.y.toFloat(), metresPerPixel)
        }
        painter.draw(canvas, markers)
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val geo = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        onLongPress(Spot(geo.latitude, geo.longitude))
        return true
    }
}
