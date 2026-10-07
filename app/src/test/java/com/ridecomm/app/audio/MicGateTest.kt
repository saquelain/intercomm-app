package com.ridecomm.app.audio

import android.media.AudioFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class MicGateTest {
    private val rate = 48_000
    private val frames = 480

    @After
    fun reset() {
        MicGate.setSettings(GateSettings.MEDIUM)
        MicGate.tap = null
    }

    /** Like WebRTC: a direct buffer left marked big-endian, filled with native-order PCM16. */
    private fun pcm(sample: (Int) -> Float): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frames * 2)
        val native = buf.duplicate().order(ByteOrder.nativeOrder())
        for (i in 0 until frames) native.putShort(i * 2, (sample(i) * 32767).toInt().toShort())
        return buf
    }

    private fun peak(buf: ByteBuffer): Int {
        val native = buf.duplicate().order(ByteOrder.nativeOrder())
        return (0 until frames).maxOf { abs(native.getShort(it * 2).toInt()) }
    }

    private fun feed(chunks: Int, sample: (Int) -> Float): ByteBuffer {
        var t = 0
        var last = pcm { 0f }
        repeat(chunks) {
            val start = t
            last = pcm { sample(start + it) }
            val time = MicGate.callback.onBuffer(last, AudioFormat.ENCODING_PCM_16BIT, 1, rate, frames * 2, 1234L)
            assertEquals("capture time is passed through", 1234L, time)
            t += frames
        }
        return last
    }

    private fun voice(t: Int) = (0.2 * sin(2 * PI * 660 * t / rate) + 0.15 * sin(2 * PI * 1500 * t / rate)).toFloat()

    @Test
    fun speechPassesThroughThePcmBuffer() {
        feed(50) { 0f } // settle any state from other tests
        val out = feed(30) { voice(it) }
        assertTrue("speech kept", peak(out) > 8_000)
    }

    @Test
    fun windIsSilenced() {
        feed(100) { 0f }
        val random = Random(3)
        val lp = Biquad.lowPass(150f, rate)
        val out = feed(150) { lp.filter(0.8f * (random.nextFloat() * 2 - 1)) * 3f }
        assertTrue("wind removed", peak(out) < 50)
    }

    @Test
    fun offLeavesTheMicUntouched() {
        MicGate.setSettings(null)
        val random = Random(5)
        val lp = Biquad.lowPass(150f, rate)
        val out = feed(20) { lp.filter(0.8f * (random.nextFloat() * 2 - 1)) * 3f }
        assertTrue(peak(out) > 1_000)
    }

    @Test
    fun tapGetsTheRawMicEvenWithTheGateOff() {
        MicGate.setSettings(null)
        var got = 0
        var rateSeen = 0
        MicGate.tap = { samples, count, sampleRate ->
            got += count
            rateSeen = sampleRate
            assertTrue(samples.take(count).any { it != 0f })
        }
        feed(3) { voice(it) }
        assertEquals(frames * 3, got)
        assertEquals(rate, rateSeen)
    }
}
