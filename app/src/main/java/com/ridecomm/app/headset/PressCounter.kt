package com.ridecomm.app.headset

/**
 * Counts quick presses of one button: presses closer together than [gapMs] belong to the same
 * gesture. Plain Kotlin with injected time so it can be unit-tested.
 */
class PressCounter(private val gapMs: Long = 550) {
    private var count = 0
    private var lastAt = Long.MIN_VALUE / 2

    /** Registers a press; returns how many presses the current gesture has so far. */
    fun press(tMs: Long): Int {
        count = if (tMs - lastAt <= gapMs) count + 1 else 1
        lastAt = tMs
        return count
    }

    /** When the gesture is finished (no press for [gapMs]), the final count; resets. */
    fun settle(tMs: Long): Int? {
        if (count == 0 || tMs - lastAt < gapMs) return null
        return count.also { count = 0 }
    }
}
