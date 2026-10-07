package com.ridecomm.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
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
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.vote.QuickMessage
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteManager
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating ride button shown over other apps (e.g. Maps) during a ride.
 *
 * - Tap it to open the options, then tap an option (or roughly toward it). Tap empty space to close.
 * - Or touch it and slide toward an option, then lift to choose it.
 * - Long-press it to drag it somewhere else; it snaps to the nearest edge.
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
        view.setOnClickListener { showMenu(tapMode = true) }
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

    /** Thick yellow ring while a vote is waiting for this rider's answer. */
    fun setVotePending(pending: Boolean) {
        bubble?.votePending = pending
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moving = false
                hideMenu()
                handler.postDelayed(enterMoveMode, MOVE_HOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (moving) {
                    dragTo(event.rawX, event.rawY)
                } else if (hypot(event.rawX - downX, event.rawY - downY) > HOLD_TOLERANCE_DP * density) {
                    // Sliding straight off the button: show the fan and follow the finger.
                    handler.removeCallbacks(enterMoveMode)
                    if (menu == null) showMenu(tapMode = false)
                    updateSelection(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(enterMoveMode)
                when {
                    moving -> snapToEdge(event.rawX)
                    menu != null -> chooseAndClose()
                    else -> view.performClick()
                }
                moving = false
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(enterMoveMode)
                if (!menuTapMode) hideMenu()
                moving = false
            }
        }
        return true
    }

    /** Touches on the open (tapped) menu: highlight by direction, choose on lift, close on empty space. */
    @SuppressLint("ClickableViewAccessibility")
    private fun onMenuTouch(event: MotionEvent): Boolean {
        handler.removeCallbacks(closeMenu)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> updateSelection(event)
            MotionEvent.ACTION_UP -> chooseAndClose()
            MotionEvent.ACTION_CANCEL -> hideMenu()
        }
        return true
    }

    private fun updateSelection(event: MotionEvent) {
        val m = menu ?: return
        val pick = m.pick(event.rawX, event.rawY)
        if (pick != m.selected) {
            m.selected = pick
            if (pick >= 0) haptics.tick()
        }
    }

    private fun chooseAndClose() {
        val chosen = menu?.selected ?: -1
        val options = currentOptions
        hideMenu()
        if (chosen >= 0) run(options[chosen])
    }

    private fun run(option: SlideOption) {
        haptics.confirm()
        // Confirm first: the action may queue its own announcement (e.g. a vote result).
        if (option.spoken.isNotEmpty()) Announcer.speak(context, option.spoken)
        option.run()
    }

    // ---- Menu ----

    private var currentOptions: List<SlideOption> = emptyList()
    private var menuTapMode = false
    private val closeMenu = Runnable { hideMenu() }

    /**
     * [tapMode]: the menu stays open and takes touches itself (closes after a few idle seconds).
     * Otherwise it only draws, while the finger that slid off the button keeps driving it.
     */
    private fun showMenu(tapMode: Boolean) {
        val b = bubble ?: return
        hideMenu()
        val view = SlideMenuView(context, innerOptions(), outerOptions())
        currentOptions = view.options
        val location = IntArray(2)
        b.getLocationOnScreen(location)
        view.anchorX = location[0] + sizePx / 2f
        view.anchorY = location[1] + sizePx / 2f
        view.centerX = view.anchorX
        view.centerY = fanCenterY(view.anchorY)
        view.onLeftEdge = onLeftEdge
        view.centerIsClose = tapMode
        if (tapMode) view.setOnTouchListener { _, event -> onMenuTouch(event) }
        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            touchable = tapMode,
        )
        // In slide mode the menu window must not take touches: the gesture keeps going to the
        // button's window, which received the touch-down.
        runCatching { windowManager.addView(view, params) }.onSuccess {
            menu = view
            menuTapMode = tapMode
            if (tapMode) handler.postDelayed(closeMenu, MENU_IDLE_CLOSE_MS)
        }
    }

    private fun hideMenu() {
        handler.removeCallbacks(closeMenu)
        menu?.let { runCatching { windowManager.removeView(it) } }
        menu = null
        menuTapMode = false
    }

    /**
     * Short-slide ring: the things used most. While a vote waits for my answer, Yes/No take the
     * place of the music controls so they're the easiest to reach.
     */
    private fun innerOptions(): List<SlideOption> = buildList {
        if (VoteManager.state.value.needsMyVote) {
            add(SlideOption(R.drawable.ms_thumb_up, "Yes", GREEN, "Voted yes") { VoteManager.cast(yes = true) })
            add(SlideOption(R.drawable.ms_thumb_down, "No", RED, "Voted no") { VoteManager.cast(yes = false) })
        }
        val muted = RideManager.state.value.micMuted
        add(
            if (muted) {
                SlideOption(R.drawable.ms_mic, "Unmute", GREEN, "Mic on") { RideManager.toggleMute() }
            } else {
                SlideOption(R.drawable.ms_mic_off, "Mute", RED, "Mic off") { RideManager.toggleMute() }
            },
        )
        val music = MusicManager.state.value
        if (music.title != null && !VoteManager.state.value.needsMyVote) {
            if (music.iAmDj) {
                add(
                    SlideOption(
                        if (music.playing) R.drawable.ms_pause else R.drawable.ms_play_arrow,
                        if (music.playing) "Pause" else "Play",
                        ORANGE,
                        if (music.playing) "Music paused" else "Music playing",
                    ) { MusicManager.playPause() },
                )
                add(SlideOption(R.drawable.ms_skip_next, "Next song", ORANGE, "Next song") { MusicManager.next() })
            } else {
                add(
                    SlideOption(
                        if (music.offForMe) R.drawable.ms_volume_up else R.drawable.ms_volume_off,
                        if (music.offForMe) "Music on" else "Music off",
                        ORANGE,
                        if (music.offForMe) "Music on" else "Music off",
                    ) { MusicManager.toggleOffForMe() },
                )
            }
        }
        add(SlideOption(R.drawable.ms_smartphone, "Open app", BLUE, "Opening RideComm") { openApp() })
    }

    /** Long-slide ring: SOS, start a vote (one at a time) and quick messages to the group. */
    private fun outerOptions(): List<SlideOption> = buildList {
        if (SosManager.state.value.mySosActive) {
            add(SlideOption(R.drawable.ms_check_circle, "I'm OK", GREEN, "") { SosManager.imOk() })
        } else {
            // Starts a cancellable countdown, which announces itself.
            add(SlideOption(R.drawable.ms_sos, "SOS", RED, "") { SosManager.startCountdown() })
        }
        if (VoteManager.state.value.active == null) {
            VoteKind.entries.forEach { kind ->
                add(SlideOption(kind.icon, "${kind.label}?", YELLOW, "${kind.label} vote sent") { VoteManager.startVote(kind) })
            }
        }
        QuickMessage.entries.forEach { message ->
            add(SlideOption(message.icon, message.label, CYAN, "Sent: ${message.label}") { VoteManager.sendQuick(message) })
        }
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

    /** The button can go almost anywhere along the edge, just clear of the status and navigation bars. */
    private fun clampY(y: Int): Int {
        val top = (EDGE_MARGIN_DP * density).roundToInt()
        val bottom = (screenHeight() - sizePx - top).coerceAtLeast(top)
        return y.coerceIn(top, bottom)
    }

    /**
     * Keeps the fan on screen. If it fits around the button it opens there; near the top or bottom
     * it opens further down or up, far enough that no option sits on top of the button.
     */
    private fun fanCenterY(buttonCenterY: Float): Float {
        val margin = SlideMenuView.FIT_MARGIN_DP * density
        val max = (screenHeight() - margin).coerceAtLeast(margin)
        val gap = SlideMenuView.MOVED_FAN_GAP_DP * density
        return when {
            buttonCenterY < margin -> (buttonCenterY + gap).coerceIn(margin, max)
            buttonCenterY > max -> (buttonCenterY - gap).coerceIn(margin, max)
            else -> buttonCenterY
        }
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

    /**
     * Frosted dark-glass button with a brand-gradient rim and the RideComm bike icon. The rim turns
     * red with a crossed-out mic while muted, and thick amber while a vote waits for an answer.
     */
    @SuppressLint("ViewConstructor")
    private class BubbleView(context: Context) : View(context) {
        var muted = false
            set(value) {
                field = value
                invalidate()
            }
        var votePending = false
            set(value) {
                field = value
                invalidate()
            }
        private val density = context.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val bike = ContextCompat.getDrawable(context, R.drawable.ms_two_wheeler)!!.mutate()
        private val micOff = ContextCompat.getDrawable(context, R.drawable.ms_mic_off)!!.mutate()

        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            val stroke = (if (votePending) 6f else 3.5f) * density
            val r = c - stroke / 2 - density
            fill.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.argb(235, 34, 38, 58), Color.argb(235, 16, 14, 36), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(c, c, r, fill)
            rim.strokeWidth = stroke
            rim.shader = when {
                votePending -> null
                muted -> null
                else -> LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), ORANGE, PINK, Shader.TileMode.CLAMP)
            }
            rim.color = when {
                votePending -> YELLOW
                muted -> RED
                else -> Color.WHITE
            }
            canvas.drawCircle(c, c, r, rim)
            val icon = if (muted) micOff else bike
            val inset = (width * 0.27f).roundToInt()
            icon.setBounds(inset, inset, width - inset, height - inset)
            icon.setTint(if (muted) RED else Color.WHITE)
            icon.draw(canvas)
        }
    }

    companion object {
        private const val BUBBLE_DP = 68f
        private const val MOVE_HOLD_MS = 600L
        private const val MENU_IDLE_CLOSE_MS = 8_000L
        private const val HOLD_TOLERANCE_DP = 14f
        private const val EDGE_MARGIN_DP = 28f

        private val ORANGE = Color.rgb(0xFF, 0x8A, 0x1F)
        private val RED = Color.rgb(0xFF, 0x4D, 0x6D)
        private val GREEN = Color.rgb(0x34, 0xE8, 0x9E)
        private val BLUE = Color.rgb(0x7C, 0x5C, 0xFF)
        private val YELLOW = Color.rgb(0xFF, 0xC9, 0x3C)
        private val CYAN = Color.rgb(0x22, 0xD3, 0xEE)
        private val PINK = Color.rgb(0xFF, 0x3D, 0x81)
    }
}
