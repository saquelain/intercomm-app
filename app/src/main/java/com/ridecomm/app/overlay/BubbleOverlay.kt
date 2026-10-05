package com.ridecomm.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.ridecomm.app.Announcer
import com.ridecomm.app.MainActivity
import com.ridecomm.app.Prefs
import com.ridecomm.app.R
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.ride.RideManager
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating ride button shown over other apps (e.g. Maps) during a ride.
 *
 * - Touch it and slide toward an option, then lift to choose it.
 * - Tap it to open RideComm.
 * - Hold it still for a moment to drag it somewhere else; it snaps to the nearest edge.
 */
class BubbleOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val sizePx = (BUBBLE_DP * density).roundToInt()
    private val haptics = Haptics(context)
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menu: SlideMenuView? = null
    private var onLeftEdge = Prefs.bubbleOnLeft(context)

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moving = false
    private val enterMoveMode = Runnable {
        moving = true
        hideMenu()
        haptics.longPress()
    }

    val isShowing: Boolean get() = bubble != null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show() {
        if (bubble != null || !canShow()) return
        val view = BubbleView(context)
        val params = overlayParams(sizePx, sizePx, touchable = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = edgeX()
            y = clampY(Prefs.bubbleY(context) ?: (screenHeight() * 0.45f).roundToInt())
        }
        view.setOnClickListener { openApp() }
        view.setOnTouchListener { v, event -> onTouch(v, event) }
        runCatching { windowManager.addView(view, params) }.onSuccess {
            bubble = view
            bubbleParams = params
        }
    }

    fun hide() {
        handler.removeCallbacks(enterMoveMode)
        hideMenu()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        bubbleParams = null
    }

    /** Red when the mic is off, so the state is visible at a glance. */
    fun setMuted(muted: Boolean) {
        bubble?.muted = muted
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = event.eventTime
                moving = false
                handler.postDelayed(enterMoveMode, MOVE_HOLD_MS)
                showMenu()
            }
            MotionEvent.ACTION_MOVE -> {
                if (moving) {
                    dragTo(event.rawX, event.rawY)
                } else {
                    if (hypot(event.rawX - downX, event.rawY - downY) > HOLD_TOLERANCE_DP * density) {
                        handler.removeCallbacks(enterMoveMode)
                    }
                    menu?.let { m ->
                        val pick = m.pick(event.rawX, event.rawY)
                        if (pick != m.selected) {
                            m.selected = pick
                            if (pick >= 0) haptics.tick()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(enterMoveMode)
                if (moving) {
                    snapToEdge(event.rawX)
                } else {
                    val chosen = menu?.selected ?: -1
                    val options = currentOptions
                    hideMenu()
                    when {
                        chosen >= 0 -> run(options[chosen])
                        isTap(event) -> view.performClick()
                    }
                }
                moving = false
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(enterMoveMode)
                hideMenu()
                moving = false
            }
        }
        return true
    }

    private fun isTap(event: MotionEvent) =
        event.eventTime - downTime < TAP_MAX_MS &&
            hypot(event.rawX - downX, event.rawY - downY) < HOLD_TOLERANCE_DP * density

    private fun run(option: SlideOption) {
        haptics.confirm()
        option.run()
        Announcer.speak(context, option.spoken)
    }

    // ---- Menu ----

    private var currentOptions: List<SlideOption> = emptyList()

    private fun showMenu() {
        val b = bubble ?: return
        currentOptions = buildOptions()
        val view = SlideMenuView(context, currentOptions)
        val location = IntArray(2)
        b.getLocationOnScreen(location)
        view.centerX = location[0] + sizePx / 2f
        view.centerY = location[1] + sizePx / 2f
        view.onLeftEdge = onLeftEdge
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            touchable = false,
        )
        // The menu window doesn't take touches: the finger's movement keeps going to the button's
        // window, which received the touch-down. Re-adding the button above it would cancel the gesture.
        runCatching { windowManager.addView(view, params) }.onSuccess { menu = view }
    }

    private fun hideMenu() {
        menu?.let { runCatching { windowManager.removeView(it) } }
        menu = null
    }

    /** Options reflect the current state (e.g. "Unmute" while muted; DJ controls only for the DJ). */
    private fun buildOptions(): List<SlideOption> = buildList {
        val muted = RideManager.state.value.micMuted
        add(
            if (muted) {
                SlideOption("🎙", "Unmute", GREEN, "Mic on") { RideManager.toggleMute() }
            } else {
                SlideOption("🔇", "Mute", RED, "Mic off") { RideManager.toggleMute() }
            },
        )
        val music = MusicManager.state.value
        if (music.title != null) {
            if (music.iAmDj) {
                add(
                    SlideOption(
                        if (music.playing) "⏸" else "▶",
                        if (music.playing) "Pause" else "Play",
                        ORANGE,
                        if (music.playing) "Music paused" else "Music playing",
                    ) { MusicManager.playPause() },
                )
                add(SlideOption("⏭", "Next song", ORANGE, "Next song") { MusicManager.next() })
            } else {
                add(
                    SlideOption(
                        if (music.offForMe) "🔈" else "🔕",
                        if (music.offForMe) "Music on" else "Music off",
                        ORANGE,
                        if (music.offForMe) "Music on" else "Music off",
                    ) { MusicManager.toggleOffForMe() },
                )
            }
        }
        add(SlideOption("📱", "Open app", BLUE, "Opening RideComm") { openApp() })
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---- Position ----

    private fun dragTo(rawX: Float, rawY: Float) {
        val b = bubble ?: return
        val p = bubbleParams ?: return
        p.x = (rawX - sizePx / 2f).roundToInt().coerceIn(0, screenWidth() - sizePx)
        p.y = clampY((rawY - sizePx / 2f).roundToInt())
        runCatching { windowManager.updateViewLayout(b, p) }
    }

    private fun snapToEdge(rawX: Float) {
        val b = bubble ?: return
        val p = bubbleParams ?: return
        onLeftEdge = rawX < screenWidth() / 2f
        p.x = edgeX()
        runCatching { windowManager.updateViewLayout(b, p) }
        Prefs.setBubblePosition(context, p.y, onLeftEdge)
    }

    private fun edgeX() = if (onLeftEdge) 0 else screenWidth() - sizePx

    /** Keeps the button far enough from the top and bottom for the whole fan to fit on screen. */
    private fun clampY(y: Int): Int {
        val margin = ((SlideMenuView.RADIUS_DP + SlideMenuView.ITEM_RADIUS_DP * 1.6f) * density).roundToInt() - sizePx / 2
        val max = (screenHeight() - margin - sizePx).coerceAtLeast(margin)
        return y.coerceIn(margin, max)
    }

    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun screenHeight() = context.resources.displayMetrics.heightPixels

    private fun overlayParams(width: Int, height: Int, touchable: Boolean) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    )

    /** Round button with the RideComm headset icon. */
    @SuppressLint("ViewConstructor")
    private class BubbleView(context: Context) : View(context) {
        var muted = false
            set(value) {
                field = value
                invalidate()
            }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 3 * context.resources.displayMetrics.density
        }
        private val icon = ContextCompat.getDrawable(context, R.drawable.ic_notification)!!.mutate()

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            fill.color = if (muted) RED else ORANGE
            canvas.drawCircle(r, r, r - border.strokeWidth, fill)
            canvas.drawCircle(r, r, r - border.strokeWidth, border)
            val inset = (width * 0.25f).roundToInt()
            icon.setBounds(inset, inset, width - inset, height - inset)
            icon.setTint(if (muted) Color.WHITE else Color.BLACK)
            icon.draw(canvas)
        }
    }

    companion object {
        private const val BUBBLE_DP = 68f
        private const val MOVE_HOLD_MS = 600L
        private const val TAP_MAX_MS = 350L
        private const val HOLD_TOLERANCE_DP = 14f

        private val ORANGE = Color.rgb(0xFF, 0x8A, 0x1F)
        private val RED = Color.rgb(0xFF, 0x4D, 0x4D)
        private val GREEN = Color.rgb(0x3D, 0xDC, 0x84)
        private val BLUE = Color.rgb(0x4D, 0x9F, 0xFF)
    }
}
