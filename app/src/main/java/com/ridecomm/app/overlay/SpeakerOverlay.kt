package com.ridecomm.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import kotlin.math.roundToInt

/** Someone talking, as shown in the corner. */
data class Speaker(val id: String, val name: String, val photo: Bitmap?)

/**
 * Small photos of whoever is talking, at the top-left corner while another app (e.g. Maps) is on
 * screen. The window ignores touches and is partly transparent, so it never blocks the app below
 * (Android only lets touches pass through overlays that are at most 80% opaque).
 */
class SpeakerOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var view: SpeakersView? = null
    private val hideLater = Runnable { remove() }

    /** Shows [speakers]; an empty list hides the strip after a short pause, so gaps between words don't flicker. */
    fun update(speakers: List<Speaker>, allowed: Boolean) {
        if (!allowed || !Settings.canDrawOverlays(context)) {
            handler.removeCallbacks(hideLater)
            remove()
            return
        }
        if (speakers.isEmpty()) {
            if (view != null) {
                handler.removeCallbacks(hideLater)
                handler.postDelayed(hideLater, HIDE_DELAY_MS)
            }
            return
        }
        handler.removeCallbacks(hideLater)
        val v = view ?: add() ?: return
        v.speakers = speakers.take(MAX_SHOWN)
        v.requestLayout()
        v.invalidate()
    }

    fun hide() {
        handler.removeCallbacks(hideLater)
        remove()
    }

    private fun add(): SpeakersView? {
        val v = SpeakersView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).roundToInt()
            y = (TOP_OFFSET_DP * density).roundToInt()
            alpha = WINDOW_ALPHA
        }
        return runCatching { windowManager.addView(v, params) }.map { v }.getOrNull()?.also { view = it }
    }

    private fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    /** The strip itself (internal so screenshot tests can render it). */
    @SuppressLint("ViewConstructor")
    internal class SpeakersView(context: Context) : View(context) {
        var speakers: List<Speaker> = emptyList()

        private val d = context.resources.displayMetrics.density
        private val size = AVATAR_DP * d
        private val gap = 8 * d
        private val labelHeight = 18 * d
        private val pad = 6 * d
        private val font = ResourcesCompat.getFont(context, R.font.outfit_semibold)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * d
            color = Color.rgb(0x34, 0xE8, 0x9E)
        }
        private val initial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 18 * d
            typeface = font
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 11 * d
            typeface = font
        }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 10, 12, 24) }
        private val rect = RectF()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val n = speakers.size.coerceAtLeast(1)
            val w = pad * 2 + n * size + (n - 1) * gap
            val h = pad * 2 + size + labelHeight
            setMeasuredDimension(w.roundToInt(), h.roundToInt())
        }

        override fun onDraw(canvas: Canvas) {
            speakers.forEachIndexed { i, speaker ->
                val left = pad + i * (size + gap)
                val cx = left + size / 2
                val cy = pad + size / 2
                val r = size / 2 - ring.strokeWidth
                val photo = speaker.photo
                if (photo != null) {
                    val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val scale = (r * 2) / minOf(photo.width, photo.height)
                    shader.setLocalMatrix(
                        Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
                        },
                    )
                    fill.shader = shader
                } else {
                    fill.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, VIOLET, CYAN, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, ring)
                if (photo == null) {
                    canvas.drawText(speaker.name.take(1).uppercase(), cx, cy + initial.textSize / 3, initial)
                }
                val name = speaker.name.substringBefore(' ').take(10)
                val w = label.measureText(name) + 10 * d
                val top = pad + size + 2 * d
                rect.set(cx - w / 2, top, cx + w / 2, top + labelHeight - 2 * d)
                canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, labelBg)
                canvas.drawText(name, cx, rect.centerY() + label.textSize / 3, label)
            }
        }
    }

    private companion object {
        const val MAX_SHOWN = 4
        const val AVATAR_DP = 42f
        const val TOP_OFFSET_DP = 40f
        const val HIDE_DELAY_MS = 1_200L
        /** At most 0.8 so touches pass through to the app underneath. */
        const val WINDOW_ALPHA = 0.78f
        val VIOLET = Color.rgb(0x7C, 0x5C, 0xFF)
        val CYAN = Color.rgb(0x22, 0xD3, 0xEE)
    }
}
