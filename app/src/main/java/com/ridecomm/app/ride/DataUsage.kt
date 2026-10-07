package com.ridecomm.app.ride

import android.net.TrafficStats
import android.os.Process
import java.util.Locale

/** Mobile data RideComm has used since the ride started (voice, music, photos: everything the app sends or gets). */
object DataUsage {
    private var startBytes: Long? = null

    fun start() {
        startBytes = total()
    }

    /** Bytes used this ride, or null if the phone doesn't report per-app traffic. */
    fun usedBytes(): Long? {
        val start = startBytes ?: return null
        val now = total() ?: return null
        return (now - start).coerceAtLeast(0)
    }

    private fun total(): Long? {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        return rx + tx
    }

    /** "0.4 MB", "12 MB", "1.3 GB". */
    fun format(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return when {
            mb >= 1_000 -> String.format(Locale.US, "%.1f GB", mb / 1_000)
            mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
            else -> String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
