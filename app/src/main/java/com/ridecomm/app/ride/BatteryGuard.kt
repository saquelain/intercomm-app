package com.ridecomm.app.ride

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery savers are the main reason an intercom goes silent in a pocket: many phones (Xiaomi, Oppo,
 * Vivo, Realme, even Samsung) stop apps running in the background to save power, mid-ride. This
 * spots when Android restricts RideComm and takes the rider to the right settings page, with the
 * steps for their phone's brand. No special permission: the rider changes it themselves.
 */
object BatteryGuard {
    /** True when Android may stop RideComm in the background. */
    fun restricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        val optimised = !pm.isIgnoringBatteryOptimizations(context.packageName)
        val am = context.getSystemService(ActivityManager::class.java)
        val limited = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am?.isBackgroundRestricted == true
        return optimised || limited
    }

    /** RideComm's App info page, where Battery is (every brand has it there). */
    fun openSettings(context: Context) {
        val info = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(info) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun tip(context: Context): String = BatteryTips.forBrand(Build.MANUFACTURER)
}

/** The steps on each brand's phones, in their own words (pure, so it can be tested). */
object BatteryTips {
    fun forBrand(manufacturer: String?): String = when (manufacturer.orEmpty().lowercase()) {
        "samsung" -> "Tap Battery → Unrestricted. Also check Settings → Battery → Background usage limits: RideComm must not be in Sleeping apps."
        "xiaomi", "redmi", "poco" -> "Tap Battery saver (or Battery) → No restrictions, then go back and turn on Autostart."
        "oppo", "realme", "oneplus" -> "Tap Battery usage → turn on Allow background activity and Allow auto launch."
        "vivo", "iqoo" -> "Tap Battery → turn on Allow high background power consumption, and allow Autostart."
        "huawei", "honor" -> "Tap App launch (or Battery → App launch) → Manage manually, with all three switches on."
        "motorola", "google", "nothing" -> "Tap App battery usage → Unrestricted."
        else -> "Tap Battery (or App battery usage) → Unrestricted, or Don't optimise."
    }
}
