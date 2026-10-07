package com.ridecomm.app.voice

import com.ridecomm.app.audio.Biquad
import com.ridecomm.app.audio.GateSettings
import com.ridecomm.app.audio.NoiseGate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cuts the mic stream into bursts of speech for the speech recogniser: 16 kHz, 16-bit mono PCM
 * (little-endian), starting a little before the speech so the first syllable isn't lost.
 * Bursts longer than [MAX_MS] are abandoned: commands are short, so that's just chatting.
 * Runs on the audio thread; keep it cheap.
 */
class SpeechSegmenter(inputRate: Int) {

    sealed interface Event {
        /** Speech began; [pcm] holds the audio just before it plus the current chunk. */
        class Start(val pcm: ByteArray) : Event
        class Audio(val pcm: ByteArray) : Event
        /** Speech ended normally: recognise what was sent. */
        data object End : Event
        /** Too long to be a command: drop it. */
        data object Abort : Event
    }

    val outputRate: Int = if (inputRate % TARGET_RATE == 0) TARGET_RATE else inputRate
    private val factor = inputRate / outputRate
    private val antiAlias = if (factor > 1) Biquad.lowPass(ANTI_ALIAS_HZ, inputRate) else null
    private val detector = NoiseGate(inputRate, GateSettings.MEDIUM)
    private var phase = 0
    private var scratch = FloatArray(0)

    private val preroll = ArrayDeque<ByteArray>()
    private var prerollBytes = 0
    private val prerollMax = outputRate * 2 * PREROLL_MS / 1000
    private val maxBytes = outputRate * 2 * MAX_MS / 1000

    private var active = false
    private var activeBytes = 0
    /** After an abort, wait for a pause before listening again. */
    private var waitForPause = false

    fun feed(samples: FloatArray, count: Int): Event? {
        val pcm = downsample(samples, count)
        if (scratch.size < count) scratch = FloatArray(count)
        samples.copyInto(scratch, 0, 0, count)
        detector.process(scratch, count)
        val speaking = detector.open

        if (waitForPause) {
            if (!speaking) waitForPause = false
            remember(pcm)
            return null
        }
        if (!active) {
            if (!speaking) {
                remember(pcm)
                return null
            }
            active = true
            val start = ByteArray(prerollBytes + pcm.size)
            var at = 0
            preroll.forEach { it.copyInto(start, at); at += it.size }
            pcm.copyInto(start, at)
            preroll.clear()
            prerollBytes = 0
            activeBytes = start.size
            return Event.Start(start)
        }
        if (!speaking) {
            active = false
            return Event.End
        }
        activeBytes += pcm.size
        if (activeBytes > maxBytes) {
            active = false
            waitForPause = true
            return Event.Abort
        }
        return Event.Audio(pcm)
    }

    private fun remember(pcm: ByteArray) {
        preroll.addLast(pcm)
        prerollBytes += pcm.size
        while (prerollBytes > prerollMax && preroll.isNotEmpty()) prerollBytes -= preroll.removeFirst().size
    }

    private fun downsample(samples: FloatArray, count: Int): ByteArray {
        val out = ByteBuffer.allocate((count / factor + 1) * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val s = antiAlias?.filter(samples[i]) ?: samples[i]
            if (phase == 0) out.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            phase = (phase + 1) % factor
        }
        return out.array().copyOf(out.position())
    }

    companion object {
        const val TARGET_RATE = 16_000
        const val ANTI_ALIAS_HZ = 7_000f
        const val PREROLL_MS = 300
        const val MAX_MS = 5_000
    }
}
