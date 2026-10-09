package com.ridecomm.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.ridecomm.app.R
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

/**
 * Play Store pictures: screens at 1080 × 2160 (the 2:1 phone size Play accepts), the 512 × 512 icon
 * and the 1024 × 500 feature graphic, into app/screenshots/store/. Only when asked:
 * `STORE_SHOTS=1 ./gradlew recordRoborazziDebug --tests '*StoreShotTest*'`.
 */
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xxhdpi")
class StoreShotTest : ScreenshotTest() {
    override val dir = "screenshots/store"

    @Before
    fun onlyWhenAsked() = assumeTrue(System.getenv("STORE_SHOTS") != null)

    private fun save(name: String, bitmap: Bitmap) {
        File(dir).mkdirs()
        File("$dir/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun icon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The adaptive icon's two layers, full bleed (Play rounds the corners itself).
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
            ContextCompat.getDrawable(context, id)!!.apply { setBounds(-128, -128, 640, 640); draw(canvas) }
        }
        save("icon_512", bitmap)
    }

    @Test
    fun featureGraphic() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 1024f, 500f, 0xFF0E0B22.toInt(), 0xFF2A1458.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1024f, 500f, p)
        fun glow(x: Float, y: Float, r: Float, color: Int) {
            p.shader = RadialGradient(x, y, r, color, 0, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        glow(120f, 80f, 420f, 0x887C5CFF.toInt())
        glow(900f, 460f, 420f, 0x77FF3D81)
        glow(980f, 40f, 260f, 0x5522D3EE)
        p.shader = null
        val icon = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val ic = Canvas(icon)
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
            ContextCompat.getDrawable(context, id)!!.apply { setBounds(-55, -55, 275, 275); draw(ic) }
        }
        p.shader = android.graphics.BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(90f, 140f) })
        }
        c.drawRoundRect(90f, 140f, 310f, 360f, 56f, 56f, p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        p.typeface = ResourcesCompat.getFont(context, R.font.outfit_extrabold)
        p.textSize = 92f
        c.drawText("RideComm", 360f, 250f, p)
        p.typeface = ResourcesCompat.getFont(context, R.font.outfit_medium)
        p.textSize = 38f
        p.color = 0xD9FFFFFF.toInt()
        c.drawText("Group intercom for bike riders", 364f, 310f, p)
        p.textSize = 30f
        p.color = 0xA6FFFFFF.toInt()
        c.drawText("Talk · Group map · SOS · Ride history", 364f, 360f, p)
        save("feature_graphic_1024x500", bitmap)
    }
}
