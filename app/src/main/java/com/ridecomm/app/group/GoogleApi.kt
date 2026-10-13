package com.ridecomm.app.group

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ridecomm.app.BuildConfig
import java.security.MessageDigest

/**
 * The Google Maps key built into the app, and the headers Google's web services want with it: the key
 * is limited to this app, so each request says which app (package and signing certificate) it is.
 */
object GoogleApi {
    /** Null when this build has no key. */
    fun key(context: Context): String? = if (!BuildConfig.HAS_GOOGLE_MAPS) null else runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun headers(context: Context, key: String, fieldMask: String): Map<String, String> = mapOf(
        "X-Goog-Api-Key" to key,
        "X-Goog-FieldMask" to fieldMask,
        "X-Android-Package" to context.packageName,
        "X-Android-Cert" to (signingSha1(context) ?: ""),
    )

    /** The app's signing certificate as Google wants it (SHA-1, hex, no colons). */
    @Suppress("DEPRECATION")
    private fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
