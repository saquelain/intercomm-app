package com.ridecomm.app.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
            countdown != null -> show("countdown") { countdownView(countdown) }
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
    private fun countdownView(seconds: Int): View = column(RED).apply {
        addView(text("🚨 SOS", 44f, bold = true))
        val number = text(seconds.toString(), 120f, bold = true)
        tag = number
        addView(number)
        addView(text("Sending to your group…", 22f))
        addView(text("TAP ANYWHERE TO CANCEL", 26f, bold = true).apply { setPadding(0, dp(40), 0, 0) })
        setOnClickListener { SosManager.cancelCountdown() }
    }

    @SuppressLint("SetTextI18n")
    private fun alertView(alert: SosAlert): View = column(RED).apply {
        addView(text("🚨 SOS", 44f, bold = true))
        addView(text("${alert.name} needs help", 30f, bold = true))
        addView(
            text(
                alert.distanceM?.let { "${SosManager.formatDistance(it)} away" } ?: "Location not available yet",
                22f,
            ).apply { setPadding(0, dp(8), 0, dp(32)) },
        )
        SosManager.mapsUri(alert)?.let { uri ->
            addView(
                button("🗺  OPEN MAP", Color.WHITE, RED) {
                    close(alert)
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
        addView(button("✓  SEEN — STOP ALARM", DARK, Color.WHITE) { close(alert) })
    }

    private fun close(alert: SosAlert) {
        seen += key(alert)
        SosManager.acknowledge()
        hide()
    }

    private fun column(background: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(background)
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, bgColor: Int, textColor: Int, onClick: () -> Unit) = Button(context).apply {
        text = label
        textSize = 22f
        setTextColor(textColor)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(20).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(80)).apply {
            topMargin = dp(16)
        }
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private companion object {
        val RED = Color.rgb(0xC6, 0x28, 0x28)
        val DARK = Color.rgb(0x40, 0x10, 0x10)
    }
}
