package com.ridecomm.app.sos

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Backup channel for SOS when there's no internet: opens the phone's Messages app with the SOS
 * text and the emergency numbers filled in, so the rider only taps Send.
 *
 * Sending silently would need the SEND_SMS permission, which Play Protect blocks for apps
 * installed outside the Play Store.
 */
object SmsSender {

    /** Returns false if there are no numbers or no messaging app. */
    fun compose(context: Context, numbers: List<String>, text: String): Boolean {
        if (numbers.isEmpty()) return false
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + numbers.joinToString(";")))
            .putExtra("sms_body", text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Splits the settings text ("98xxx, +91 99xxx") into dialable numbers. */
    fun parseNumbers(text: String): List<String> =
        text.split(',', ';', '\n')
            .map { it.filter { c -> c.isDigit() || c == '+' } }
            .filter { it.count(Char::isDigit) >= 6 }
}
