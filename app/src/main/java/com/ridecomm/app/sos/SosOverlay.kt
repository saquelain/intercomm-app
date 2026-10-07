package com.ridecomm.app.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.ridecomm.app.R
import kotlin.math.roundToInt

/**
 * Full-screen red windows shown over other apps (e.g. Maps) while RideComm isn't on screen:
 * the SOS countdown (tap anywhere to cancel) and incoming SOS alerts.
 */
class SosOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var view: View? = null
    private var shownKey: String? = null
    /** Alerts the rider already closed here (by identity + time), so they don't pop up again. */
    private val seen = mutableSetOf<String>()

    fun update(state: SosState, appVisible: Boolean) {
        if (appVisible || !Settings.canDrawOverlays(context)) return hide()
        val countdown = state.countdown
        val alert = state.alerts.lastOrNull()?.takeIf { key(it) !in seen }
        when {
            countdown != null -> show("countdown") { countdownView(countdown, state.countdownFromCrash) }
            alert != null -> show(key(alert)) { alertView(alert) }
            else -> hide()
        }
        // The countdown number changes every second.
        if (countdown != null) (view?.tag as? TextView)?.text = countdown.toString()
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        shownKey = null
    }

    private fun key(alert: SosAlert) = "${alert.identity}:${alert.atMs}"

    private fun show(key: String, build: () -> View) {
        if (shownKey == key) return
        hide()
        val v = build()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(v, params) }.onSuccess {
            view = v
            shownKey = key
        }
    }

    @SuppressLint("SetTextI18n")
    private fun countdownView(seconds: Int, crash: Boolean): View = column().apply {
        addView(icon(if (crash) R.drawable.ms_car_crash else R.drawable.ms_sos, 72))
        if (crash) addView(text("Crash detected", 30f, bold = true))
        val number = text(seconds.toString(), 96f, bold = true)
        tag = number
        addView(
            FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(36, 255, 255, 255))
                    setStroke(dp(2), Color.argb(130, 255, 255, 255))
                }
                addView(number, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            },
            LinearLayout.LayoutParams(dp(190), dp(190)).apply { setMargins(0, dp(16), 0, dp(16)) },
        )
        addView(text("Sending SOS to your group", 22f, bold = true))
        addView(pill(R.drawable.ms_close, "Tap anywhere to cancel"))
        setOnClickListener { SosManager.cancelCountdown() }
    }

    @SuppressLint("SetTextI18n")
    private fun alertView(alert: SosAlert): View = column().apply {
        addView(icon(R.drawable.ms_sos, 80))
        addView(text(if (alert.crash) "${alert.name} may have crashed" else "${alert.name} needs help", 30f, bold = true).apply { setPadding(0, dp(12), 0, 0) })
        addView(
            text(
                alert.distanceM?.let { "${SosManager.formatDistance(it)} away" } ?: "Location not available yet",
                20f,
            ).apply {
                alpha = 0.85f
                setPadding(0, dp(6), 0, dp(28))
            },
        )
        SosManager.mapsUri(alert)?.let { uri ->
            addView(
                button(R.drawable.ms_map, "Open map", solid = true) {
                    close(alert)
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
        addView(button(R.drawable.ms_check_circle, "Seen, stop alarm", solid = false) { close(alert) })
    }

    private fun close(alert: SosAlert) {
        seen += key(alert)
        SosManager.acknowledge()
        hide()
    }

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(RED_TOP, RED_BOTTOM))
        setPadding(dp(28), dp(28), dp(28), dp(28))
    }

    private fun icon(@DrawableRes res: Int, sizeDp: Int) = ImageView(context).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = if (bold) boldFont else regularFont
    }

    /** Frosted capsule with an icon and a short instruction. */
    private fun pill(@DrawableRes res: Int, label: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = glassDrawable(dp(40).toFloat())
        setPadding(dp(22), dp(14), dp(22), dp(14))
        addView(icon(res, 26))
        addView(text(label, 18f, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(36) }
    }

    /** Big glove-friendly button: white ([solid]) or frosted glass. */
    private fun button(@DrawableRes res: Int, label: String, solid: Boolean, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = if (solid) {
            GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(40).toFloat()
            }
        } else {
            glassDrawable(dp(40).toFloat())
        }
        val color = if (solid) RED_BOTTOM else Color.WHITE
        addView(icon(res, 28).apply { setColorFilter(color) })
        addView(
            text(label, 21f, bold = true).apply {
                setTextColor(color)
                setPadding(dp(12), 0, 0, 0)
            },
        )
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78)).apply {
            topMargin = dp(14)
        }
        setOnClickListener { onClick() }
    }

    private fun glassDrawable(radius: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(28, 255, 255, 255)),
    ).apply {
        cornerRadius = radius
        setStroke(dp(1), Color.argb(120, 255, 255, 255))
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private val regularFont = ResourcesCompat.getFont(context, R.font.outfit_medium)
    private val boldFont = ResourcesCompat.getFont(context, R.font.outfit_bold)

    private companion object {
        val RED_TOP = Color.rgb(0xE5, 0x24, 0x5E)
        val RED_BOTTOM = Color.rgb(0x7A, 0x0F, 0x2E)
    }
}
