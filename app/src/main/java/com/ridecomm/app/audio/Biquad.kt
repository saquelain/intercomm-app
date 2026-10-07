package com.ridecomm.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Second-order IIR filter (RBJ audio-EQ cookbook), processed one sample at a time. */
class Biquad private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun filter(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    companion object {
        private val Q = (1 / sqrt(2.0)).toFloat() // Butterworth

        fun lowPass(cutoffHz: Float, sampleRate: Int): Biquad {
            val w = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad(
                ((1 - c) / 2 / a0).toFloat(),
                ((1 - c) / a0).toFloat(),
                ((1 - c) / 2 / a0).toFloat(),
                (-2 * c / a0).toFloat(),
                ((1 - alpha) / a0).toFloat(),
            )
        }

        fun highPass(cutoffHz: Float, sampleRate: Int): Biquad {
            val w = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad(
                ((1 + c) / 2 / a0).toFloat(),
                (-(1 + c) / a0).toFloat(),
                ((1 + c) / 2 / a0).toFloat(),
                (-2 * c / a0).toFloat(),
                ((1 - alpha) / a0).toFloat(),
            )
        }
    }
}
