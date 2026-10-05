package com.ridecomm.app.overlay

import kotlin.math.hypot

/**
 * Two fans of options around the floating button: a short slide reaches the [inner] ring, a long
 * slide the [outer] ring. Options are numbered inner first, then outer.
 */
class RingLayout(
    private val inner: Int,
    private val outer: Int,
    onLeftEdge: Boolean,
    private val innerRadius: Float,
    private val outerRadius: Float,
    private val deadZone: Float,
) {
    private val innerFan = FanLayout(inner, onLeftEdge)
    private val outerFan = FanLayout(outer, onLeftEdge)

    fun angleOf(i: Int): Float = if (i < inner) innerFan.angleOf(i) else outerFan.angleOf(i - inner)

    fun radiusOf(i: Int): Float = if (i < inner) innerRadius else outerRadius

    /** The option the finger at offset ([dx], [dy]) from the button points to, or -1. */
    fun pick(dx: Float, dy: Float): Int {
        val distance = hypot(dx, dy)
        if (distance < deadZone) return -1
        val useOuter = outer > 0 && (inner == 0 || distance > (innerRadius + outerRadius) / 2)
        return if (useOuter) {
            outerFan.pick(dx, dy, 0f).let { if (it < 0) -1 else inner + it }
        } else {
            innerFan.pick(dx, dy, 0f)
        }
    }
}
