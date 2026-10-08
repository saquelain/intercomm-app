package com.ridecomm.app.trip

import android.content.Context
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.ride.safeMainScope
import com.ridecomm.app.vote.VoteKind
import com.ridecomm.app.vote.VoteManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A break suggestion on screen: riding time since the last break. */
data class BreakDue(val ridingMs: Long)

/**
 * "You've been riding for 2 hours. Time for a break?" Only on my phone; one tap (or "RideComm,
 * break") asks the whole group with a Break vote. Any Break vote that passes, or a 10-minute stop,
 * counts as a break for everyone's reminder.
 */
object BreakReminder {
    private const val TICK_MS = 15_000L

    private val scope = safeMainScope()
    private val _due = MutableStateFlow<BreakDue?>(null)
    val due: StateFlow<BreakDue?> = _due.asStateFlow()

    private lateinit var appContext: Context
    private var watch = BreakWatch(0)
    private var ticker: Job? = null
    private var voteWatch: Job? = null
    private var startedMs = 0L
    private var lastBreakVote: String? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        stop()
        watch = BreakWatch(Prefs.breakEvery(appContext).minutes)
        startedMs = System.currentTimeMillis()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
        voteWatch = scope.launch {
            VoteManager.state.collect { s ->
                val result = s.lastResult ?: return@collect
                if (result.kind == VoteKind.BREAK && result.approved == true && result.id != lastBreakVote) {
                    lastBreakVote = result.id
                    watch.tookBreak()
                    _due.value = null
                }
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        voteWatch?.cancel()
        voteWatch = null
        _due.value = null
    }

    /** The interval changed in Settings (mid-ride too). */
    fun applySettings(context: Context) {
        watch.everyMin = Prefs.breakEvery(context).minutes
        if (watch.everyMin == 0) _due.value = null
    }

    /** "Ask for a break": a Break vote for the group. */
    fun askGroup() {
        _due.value = null
        VoteManager.startVote(VoteKind.BREAK)
    }

    /** "Not now": ask again after another half hour of riding. */
    fun notNow() {
        _due.value = null
    }

    private fun tick() {
        val trip = TripTracker.state.value
        val now = System.currentTimeMillis()
        val riding = if (trip.gps) trip.movingMs else now - startedMs
        if (!watch.update(now, riding)) return
        _due.value = BreakDue(watch.sinceBreakMs)
        Announcer.speak(appContext, BreakWatch.spoken(watch.sinceBreakMs))
    }
}
