package com.ridecomm.app.overlay

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Geometry of the slide menu: [count] options fanned out away from the screen edge the button
 * sits on. Angles are in degrees on screen axes (0 = right, 90 = down); option 0 is at the top.
 */
class FanLayout(private val count: Int, private val onLeftEdge: Boolean) {

    private val step: Float = if (count <= 1) 0f else SPAN_DEG / (count - 1)

    fun angleOf(i: Int): Float {
        val center = if (onLeftEdge) 0f else 180f
        if (count <= 1) return center
        return if (onLeftEdge) center - SPAN_DEG / 2 + i * step else center + SPAN_DEG / 2 - i * step
    }

    /**
     * The option the finger points at, given its offset ([dx], [dy]) from the button centre,
     * or -1 while it's still within [deadZone] or pointing away from every option.
     */
    fun pick(dx: Float, dy: Float, deadZone: Float): Int {
        if (count == 0 || hypot(dx, dy) < deadZone) return -1
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val best = (0 until count).minBy { angleDiff(angle, angleOf(it)) }
        val tolerance = (if (count <= 1) SPAN_DEG / 2 else step / 2) + SLACK_DEG
        return if (angleDiff(angle, angleOf(best)) <= tolerance) best else -1
    }

    private fun angleDiff(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360 + 360) % 360)
        return if (d > 180) 360 - d else d
    }

    companion object {
        private const val SPAN_DEG = 150f
        /** Extra tolerance beyond halfway to the neighbouring option. */
        private const val SLACK_DEG = 25f
    }
}
