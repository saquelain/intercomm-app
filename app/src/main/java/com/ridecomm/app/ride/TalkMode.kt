package com.ridecomm.app.ride

/** How my mic decides when I'm talking. */
enum class TalkMode(val label: String) {
    /** Always on; the wind filter sends my voice and holds back wind. */
    OPEN_MIC("Open mic"),
    /** Silent until I hold the talk button (or tap it to keep talking). */
    PUSH_TO_TALK("Push to talk"),
}

/**
 * The push-to-talk button: hold to talk, or tap once to keep talking hands-free and tap again
 * to stop (easier with gloves). A forgotten open mic closes itself after [maxLatchedMs].
 */
class TalkButton(private val tapMs: Long = 350, private val maxLatchedMs: Long = 2 * 60_000L) {
    var talking = false
        private set
    /** Talking without holding: a tap opened it. */
    var latched = false
        private set
    private var downAtMs = 0L
    private var latchedAtMs = 0L

    fun press(nowMs: Long) {
        downAtMs = nowMs
        talking = true
    }

    fun release(nowMs: Long) {
        when {
            latched -> stop() // the tap that ends a latched talk
            nowMs - downAtMs < tapMs -> {
                latched = true
                latchedAtMs = nowMs
            }
            else -> talking = false
        }
    }

    /** One press on the headset or the floating button: start or stop talking. */
    fun toggle(nowMs: Long) {
        if (talking) {
            stop()
        } else {
            talking = true
            latched = true
            latchedAtMs = nowMs
        }
    }

    /** True when a latched talk has run too long and was closed now. */
    fun timedOut(nowMs: Long): Boolean {
        if (!latched || nowMs - latchedAtMs < maxLatchedMs) return false
        stop()
        return true
    }

    fun stop() {
        talking = false
        latched = false
    }
}
