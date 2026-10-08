package com.ridecomm.app.whisper

/** One rider talking only to another, as seen from my phone. */
data class WhisperView(val fromId: String, val fromName: String, val toId: String, val toName: String)

/**
 * Who is talking privately to whom, from the "whisper" messages. Pure logic (no Android), so the
 * timing rules can be tested.
 *
 * A private talk repeats its message every couple of seconds; if the repeats stop (the sender's
 * phone dropped) it ends by itself after [timeoutMs]. After it ends the sender stays silent for
 * other riders a little longer ([tailMs]): their last words are still on the way.
 */
class WhisperBoard(private val timeoutMs: Long = TIMEOUT_MS, private val tailMs: Long = TAIL_MS) {
    private class Entry(val view: WhisperView, val lastMs: Long, val endedMs: Long?)

    private val bySender = mutableMapOf<String, Entry>()

    /** A "whisper" message from [fromId] arrived. */
    fun onMessage(fromId: String, fromName: String, toId: String, toName: String, on: Boolean, nowMs: Long) {
        if (on) {
            bySender[fromId] = Entry(WhisperView(fromId, fromName, toId, toName), nowMs, null)
        } else {
            val e = bySender[fromId] ?: return
            bySender[fromId] = Entry(e.view, e.lastMs, nowMs)
        }
    }

    /** Private talks going on now. */
    fun active(nowMs: Long): List<WhisperView> = bySender.values.filter { live(it, nowMs) }.map { it.view }

    /** The private talk to me right now, if any. */
    fun toMe(me: String, nowMs: Long): WhisperView? = active(nowMs).firstOrNull { it.toId == me }

    /** Riders I must not hear right now: they're talking only to someone else (or just stopped). */
    fun hushed(me: String, nowMs: Long): Set<String> = bySender.values
        .filter { it.view.toId != me && (live(it, nowMs) || tail(it, nowMs)) }
        .map { it.view.fromId }
        .toSet()

    /** Something is still going on (or ending), so it's worth checking again soon. */
    fun busy(nowMs: Long): Boolean {
        bySender.entries.removeAll { !live(it.value, nowMs) && !tail(it.value, nowMs) }
        return bySender.isNotEmpty()
    }

    fun forget(riderId: String) {
        bySender.remove(riderId)
    }

    fun clear() = bySender.clear()

    private fun live(e: Entry, nowMs: Long) = e.endedMs == null && nowMs - e.lastMs <= timeoutMs

    private fun tail(e: Entry, nowMs: Long): Boolean {
        val end = e.endedMs ?: (e.lastMs + timeoutMs)
        return nowMs - end in 0 until tailMs
    }

    companion object {
        /** The sender repeats "still talking" this often. */
        const val REPEAT_MS = 2_000L
        const val TIMEOUT_MS = 5_000L
        const val TAIL_MS = 700L
        /** The sender's mic stays closed this long after they start, until the others have silenced them. */
        const val WARM_UP_MS = 400L
    }
}
