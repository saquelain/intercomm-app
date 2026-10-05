package com.ridecomm.app.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Backup channel for SOS when there's no internet: plain SMS to the rider's emergency numbers. */
object SmsSender {

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Returns how many numbers the message was handed to. */
    fun send(context: Context, numbers: List<String>, text: String): Int {
        if (numbers.isEmpty() || !canSend(context)) return 0
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sms.divideMessage(text)
        return numbers.count { number ->
            runCatching { sms.sendMultipartTextMessage(number, null, parts, null, null) }.isSuccess
        }
    }

    /** Splits the settings text ("98xxx, +91 99xxx") into dialable numbers. */
    fun parseNumbers(text: String): List<String> =
        text.split(',', ';', '\n')
            .map { it.filter { c -> c.isDigit() || c == '+' } }
            .filter { it.count(Char::isDigit) >= 6 }
}
