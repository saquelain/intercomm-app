package com.ridecomm.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Problem reports from riders: a crash report or "Report a problem" in Settings, with the phone's
 * details filled in so a bug can be traced. With [EMAIL] set it opens an email to the developer;
 * empty, the rider picks how to send it (WhatsApp, Gmail…). Nothing is sent without the rider.
 */
object Support {
    /** The developer's address (also the Play Store contact). Empty: share with any app. */
    const val EMAIL = ""

    fun deviceInfo(): String =
        "RideComm ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

    fun send(context: Context, subject: String, body: String) {
        val intent = if (EMAIL.isNotBlank()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** "Report a problem": the rider writes what happened under the phone's details (and the last crash, if any). */
    fun reportProblem(context: Context) {
        val body = buildString {
            appendLine("What happened (and what you expected):")
            appendLine()
            appendLine()
            appendLine("—")
            appendLine(deviceInfo())
            CrashLog.read(context)?.let { appendLine().appendLine("Last crash:").append(it.take(6_000)) }
        }
        send(context, "RideComm problem (build ${BuildConfig.VERSION_CODE})", body)
    }
}
