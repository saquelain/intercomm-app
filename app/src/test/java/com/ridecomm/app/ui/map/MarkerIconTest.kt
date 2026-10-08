package com.ridecomm.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.ridecomm.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Marker pictures for Google Maps: nothing cut off at the edges, and the point lands on the spot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class MarkerIconTest {
    private val painter = MarkerPainter(ApplicationProvider.getApplicationContext())

    private val markers = listOf(
        MapMarker.Rider(0f, 0f, "Amit", null, talking = true, headingDeg = 45f, stale = false, role = "Lead"),
        MapMarker.Rider(0f, 0f, "Bartholomew-Alexander Long", null, talking = false, headingDeg = null, stale = true),
        MapMarker.Me(0f, 0f, headingDeg = 200f, accuracyPx = 0f, name = "Sam"),
        MapMarker.Me(0f, 0f, headingDeg = null, accuracyPx = 0f),
        MapMarker.Regroup(0f, 0f, "Shell pump", "2 of 4 here"),
        MapMarker.Regroup(0f, 0f, "Lonavala hill station view point", "Destination", destination = true),
        MapMarker.Hazard(0f, 0f, R.drawable.ms_warning, "Pothole"),
    )

    @Test
    fun edgesAreEmpty() {
        markers.forEach { m ->
            val bitmap = painter.icon(m).bitmap
            for (x in 0 until bitmap.width) {
                assertEquals("top edge of $m", 0, Color.alpha(bitmap.getPixel(x, 0)))
                assertEquals("bottom edge of $m", 0, Color.alpha(bitmap.getPixel(x, bitmap.height - 1)))
            }
            for (y in 0 until bitmap.height) {
                assertEquals("left edge of $m", 0, Color.alpha(bitmap.getPixel(0, y)))
                assertEquals("right edge of $m", 0, Color.alpha(bitmap.getPixel(bitmap.width - 1, y)))
            }
        }
    }

    @Test
    fun anchorIsTheMarkersPoint() {
        markers.forEach { m ->
            val icon = painter.icon(m)
            val box = painter.extent(m)
            assertEquals(-box.left / box.width(), icon.anchorX, 0.02f)
            assertEquals(-box.top / box.height(), icon.anchorY, 0.02f)
            // Something is drawn right at the point (avatar, tile or the regroup pin's tip).
            val x = (icon.anchorX * icon.bitmap.width).toInt()
            val y = (icon.anchorY * icon.bitmap.height).toInt().coerceAtMost(icon.bitmap.height - 1)
            assertTrue("nothing at the point of $m", Color.alpha(icon.bitmap.getPixel(x, y)) > 0)
        }
        // A sheet of all of them, to look at.
        val icons = markers.map { painter.icon(it).bitmap }
        val sheet = Bitmap.createBitmap(icons.sumOf { it.width + 20 }, icons.maxOf { it.height }, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(Color.rgb(0xE8, 0xEA, 0xED))
            var x = 0f
            icons.forEach { drawBitmap(it, x, 0f, null); x += it.width + 20 }
        }
        File("screenshots").mkdirs()
        File("screenshots/marker_icons.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
