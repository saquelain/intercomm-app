package com.ridecomm.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import com.ridecomm.app.Announcer
import com.ridecomm.app.Prefs
import com.ridecomm.app.alerts.RiderAlerts
import com.ridecomm.app.audio.MicGate
import com.ridecomm.app.music.MusicManager
import com.ridecomm.app.ride.RideManager
import com.ridecomm.app.ride.RideStatus
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.vote.VoteManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.IOException

/**
 * Hands-free control: "RideComm, vote break", "RideComm, slow down", "RideComm, SOS"…
 *
 * The call already owns the microphone, so instead of letting the speech recogniser open it, each
 * burst of my speech is cut out of the call's own mic stream ([SpeechSegmenter]) and piped to the
 * phone's speech recogniser (on-device when available). Only bursts that start with "RideComm"
 * do anything. Needs Android 13+, which lets apps hand their own audio to the recogniser.
 */
object VoiceCommands {
    private const val TAG = "RideComm"
    /** A recogniser that never answers is given up on after this long. */
    private const val SESSION_TIMEOUT_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private var io: Handler? = null
    private var ioThread: HandlerThread? = null
    private lateinit var appContext: Context

    // Audio thread only.
    private var segmenter: SpeechSegmenter? = null
    private var segmenterRate = 0

    // Io thread only. Non-blocking, so a recogniser that stops reading can never stall us.
    private var sink: ParcelFileDescriptor? = null

    // Main thread only.
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = true
    private var modelDownloadAsked = false
    @Volatile private var busy = false

    /** Android 13+ with a speech recogniser on the phone. */
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isRecognitionAvailable(context)

    /** Called when the ride connects, and when the setting changes. */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !Prefs.voiceCommands(appContext) || !available(appContext)) {
            stop()
            return
        }
        if (ioThread != null) return
        val thread = HandlerThread("rc-voice").apply { start() }
        ioThread = thread
        io = Handler(thread.looper)
        MicGate.tap = ::onMicAudio
    }

    fun stop() {
        MicGate.tap = null
        val h = io
        io = null
        h?.post { closeSink() }
        ioThread?.quitSafely()
        ioThread = null
        main.post {
            recognizer?.destroy()
            recognizer = null
            busy = false
        }
    }

    fun applySettings(context: Context) {
        if (RideManager.state.value.status == RideStatus.IDLE) return
        if (Prefs.voiceCommands(context)) start(context) else stop()
    }

    // ---- Audio thread ----

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onMicAudio(samples: FloatArray, count: Int, sampleRate: Int) {
        val seg = segmenter?.takeIf { segmenterRate == sampleRate }
            ?: SpeechSegmenter(sampleRate).also { segmenter = it; segmenterRate = sampleRate }
        val event = seg.feed(samples, count) ?: return
        val h = io ?: return
        when (event) {
            is SpeechSegmenter.Event.Start -> {
                // One command at a time; speech while the last one is still being recognised is ignored.
                if (busy) return
                busy = true
                val rate = seg.outputRate
                h.post { openSession(event.pcm, rate) }
            }
            is SpeechSegmenter.Event.Audio -> h.post { write(event.pcm) }
            SpeechSegmenter.Event.End -> h.post { closeSink() }
            SpeechSegmenter.Event.Abort -> h.post {
                if (sink != null) {
                    closeSink()
                    main.post { cancelSession() }
                }
            }
        }
    }

    // ---- Io thread ----

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun openSession(pcm: ByteArray, rate: Int) {
        val (read, write) = try {
            ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
        } catch (e: IOException) {
            busy = false
            return
        }
        try {
            Os.fcntlInt(write.fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
        } catch (e: ErrnoException) {
            Log.w(TAG, "Couldn't make the voice pipe non-blocking", e)
        }
        sink = write
        main.post { startRecognizer(read, rate) }
        write(pcm)
    }

    private fun write(pcm: ByteArray) {
        val out = sink ?: return
        var at = 0
        try {
            while (at < pcm.size) at += Os.write(out.fileDescriptor, pcm, at, pcm.size - at)
        } catch (e: Exception) {
            // Pipe full (the recogniser fell behind) or closed (it finished early): end this command here.
            closeSink()
        }
    }

    private fun closeSink() {
        runCatching { sink?.close() }
        sink = null
    }

    // ---- Main thread ----

    private val timeout = Runnable { cancelSession() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startRecognizer(audio: ParcelFileDescriptor, rate: Int) {
        if (ioThread == null) {
            audio.close()
            busy = false
            return
        }
        try {
            val rec = recognizer ?: createRecognizer().also { recognizer = it }
            rec.startListening(intent(audio, rate))
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, SESSION_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Voice command recogniser failed", e)
            busy = false
        } finally {
            // The recogniser received its own copy of the pipe.
            audio.close()
        }
    }

    private fun cancelSession() {
        main.removeCallbacks(timeout)
        io?.post { closeSink() }
        runCatching { recognizer?.cancel() }
        busy = false
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun intent(audio: ParcelFileDescriptor, rate: Int) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
        putExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(CommandParser.BIASING))
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRecognizer(): SpeechRecognizer {
        val rec = if (onDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            onDevice = false
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
        rec.setRecognitionListener(listener)
        return rec
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            main.removeCallbacks(timeout)
            io?.post { closeSink() }
            busy = false
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val command = heard.firstNotNullOfOrNull { CommandParser.parse(it) } ?: return
            Log.i(TAG, "Voice command: $heard -> $command")
            run(command)
        }

        override fun onError(error: Int) {
            main.removeCallbacks(timeout)
            io?.post { closeSink() }
            busy = false
            if (onDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                // No offline model for this language yet: ask for it, and use the regular recogniser meanwhile.
                if (!modelDownloadAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    modelDownloadAsked = true
                    runCatching {
                        recognizer?.triggerModelDownload(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                        )
                    }
                }
                onDevice = false
                recognizer?.destroy()
                recognizer = null
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    // ---- Doing what was asked ----

    private fun run(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.StartVote -> when {
                VoteManager.state.value.active != null -> say("A vote is already running")
                else -> {
                    VoteManager.startVote(command.kind)
                    say("Asked the group: ${command.kind.label.lowercase()}")
                }
            }
            is VoiceCommand.Cast -> {
                val vote = VoteManager.state.value
                when {
                    vote.active == null -> say("No vote to answer")
                    vote.myVote != null -> say("You already voted")
                    else -> {
                        VoteManager.cast(command.yes)
                        say(if (command.yes) "Voted yes" else "Voted no")
                    }
                }
            }
            is VoiceCommand.Quick -> VoteManager.sendQuick(command.message)
            is VoiceCommand.Mute -> {
                if (RideManager.state.value.micMuted != command.muted) RideManager.toggleMute()
                say(if (command.muted) "Mic off" else "Mic on")
            }
            VoiceCommand.NextSong -> {
                val music = MusicManager.state.value
                when {
                    music.title == null -> say("No music playing")
                    !music.iAmDj -> say("Only ${music.djName ?: "the DJ"} can skip songs")
                    else -> {
                        MusicManager.next()
                        say("Next song")
                    }
                }
            }
            is VoiceCommand.Music -> {
                val music = MusicManager.state.value
                when {
                    music.title == null -> say("No music playing")
                    else -> {
                        if (music.offForMe == command.on) MusicManager.toggleOffForMe()
                        say(if (command.on) "Music on" else "Music off")
                    }
                }
            }
            VoiceCommand.Sos -> SosManager.startCountdown()
            VoiceCommand.Cancel ->
                if (SosManager.state.value.countdown != null) SosManager.cancelCountdown() else say("Nothing to cancel")
            VoiceCommand.WhoIsHere -> say(whoIsHere())
            VoiceCommand.Battery -> say(batteries())
            VoiceCommand.Unknown -> say("Say RideComm, then a command, like break or slow down")
        }
    }

    private fun whoIsHere(): String {
        val others = RideManager.state.value.riders.filter { !it.isMe }.map { it.name }
        return when (others.size) {
            0 -> "Nobody else is on the ride yet"
            1 -> "${others[0]} and you"
            else -> "${others.size + 1} riders: ${others.joinToString(", ")} and you"
        }
    }

    private fun batteries(): String {
        val levels = RiderAlerts.batteries.value
        return RideManager.state.value.riders.mapNotNull { rider ->
            val b = levels[rider.id] ?: return@mapNotNull null
            val who = if (rider.isMe) "You" else rider.name
            "$who ${b.level} percent" + if (b.charging) ", charging" else ""
        }.joinToString(". ").ifEmpty { "No battery levels yet" }
    }

    private fun say(text: String) {
        if (::appContext.isInitialized) Announcer.speak(appContext, text)
    }
}
