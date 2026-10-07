package com.ridecomm.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpeechSegmenterTest {
    private val rate = 48_000
    private val chunk = 480

    private fun voice(t: Int) = (0.2 * sin(2 * PI * 660 * t / rate) + 0.15 * sin(2 * PI * 1500 * t / rate)).toFloat()

    private fun run(seg: SpeechSegmenter, plan: List<Pair<Int, Boolean>>): List<SpeechSegmenter.Event> {
        val events = mutableListOf<SpeechSegmenter.Event>()
        var t = 0
        plan.forEach { (ms, speaking) ->
            repeat(ms / 10) {
                val start = t
                val buf = FloatArray(chunk) { if (speaking) voice(start + it) else 0f }
                t += chunk
                seg.feed(buf, chunk)?.let { events += it }
            }
        }
        return events
    }

    @Test
    fun shortSpeechIsOneSegmentAt16k() {
        val seg = SpeechSegmenter(rate)
        assertEquals(16_000, seg.outputRate)
        val events = run(seg, listOf(500 to false, 1_500 to true, 1_000 to false))
        val start = events.first() as SpeechSegmenter.Event.Start
        // Includes ~300 ms from before the speech: 16 kHz * 2 bytes * 0.3 s.
        assertTrue(start.pcm.size >= 9_000)
        assertEquals(SpeechSegmenter.Event.End, events.last())
        val audioBytes = start.pcm.size + events.filterIsInstance<SpeechSegmenter.Event.Audio>().sumOf { it.pcm.size }
        // 1.5 s of speech + preroll + the gate's hold, at 32 bytes per ms.
        assertTrue("got $audioBytes", audioBytes in 50_000..80_000)
    }

    @Test
    fun longTalkIsAbandonedThenListensAgainAfterAPause() {
        val seg = SpeechSegmenter(rate)
        val events = run(seg, listOf(200 to false, 7_000 to true, 1_000 to false, 1_000 to true, 1_000 to false))
        val kinds = events.filter { it !is SpeechSegmenter.Event.Audio }.map { it::class.simpleName }
        assertEquals(listOf("Start", "Abort", "Start", "End"), kinds)
    }

    @Test
    fun silenceProducesNothing() {
        assertTrue(run(SpeechSegmenter(rate), listOf(3_000 to false)).isEmpty())
    }
}
