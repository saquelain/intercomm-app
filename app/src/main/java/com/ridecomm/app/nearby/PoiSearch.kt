package com.ridecomm.app.nearby

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ridecomm.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Finds petrol pumps, food or mechanics around a point. Google Maps' places when this build's key
 * allows it (best coverage in India), otherwise OpenStreetMap (free, no key), tried on a few
 * servers in turn because the public ones are sometimes busy.
 */
object PoiSearch {
    private const val GOOGLE_URL = "https://places.googleapis.com/v1/places:searchNearby"
    private val OVERPASS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private const val TIMEOUT_MS = 20_000

    /** Google said no (key without the Places API, quota…): don't ask again until the app restarts. */
    @Volatile private var googleRefused = false

    /** Throws when nothing could be reached; an empty list means nothing was found. */
    suspend fun search(context: Context, kind: PoiKind, lat: Double, lon: Double, bearing: Double?): List<Poi> = withContext(Dispatchers.IO) {
        if (BuildConfig.HAS_GOOGLE_MAPS && !googleRefused) {
            try {
                return@withContext google(context, kind, lat, lon, bearing)
            } catch (e: HttpError) {
                // Refused (Places not switched on for the key): use OpenStreetMap from now on.
                if (e.code in 400..403) googleRefused = true
            } catch (_: Exception) {
                // No answer this time: OpenStreetMap below, Google again next time.
            }
        }
        var lastError: Exception? = null
        for (url in OVERPASS) {
            try {
                val body = "data=" + URLEncoder.encode(AheadLogic.overpassQuery(kind, lat, lon), "UTF-8")
                return@withContext AheadLogic.parseOverpass(post(url, body, "application/x-www-form-urlencoded", emptyMap()), kind)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no search server")
    }

    /** Around me, and a second circle centred further along the road. */
    private fun google(context: Context, kind: PoiKind, lat: Double, lon: Double, bearing: Double?): List<Poi> {
        val key = mapsKey(context) ?: throw IllegalStateException("no key")
        val headers = mapOf(
            "X-Goog-Api-Key" to key,
            "X-Goog-FieldMask" to "places.id,places.displayName,places.location",
            // The key is limited to this app; Google checks these.
            "X-Android-Package" to context.packageName,
            "X-Android-Cert" to (signingSha1(context) ?: ""),
        )
        val circles = buildList {
            add(Triple(lat, lon, 3_000.0))
            if (bearing != null) {
                val (aLat, aLon) = AheadLogic.pointAhead(lat, lon, bearing, 9_000.0)
                add(Triple(aLat, aLon, 9_000.0))
            } else {
                add(Triple(lat, lon, kind.radiusM.toDouble()))
            }
        }
        return circles.flatMap { (cLat, cLon, r) ->
            val body = JSONObject()
                .put("includedTypes", JSONArray(kind.googleTypes))
                .put("maxResultCount", 20)
                .put("rankPreference", "DISTANCE")
                .put("locationRestriction", JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", cLat).put("longitude", cLon)).put("radius", r)))
            AheadLogic.parseGoogle(post(GOOGLE_URL, body.toString(), "application/json", headers))
        }.distinctBy { it.id }
    }

    private fun post(url: String, body: String, type: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.setRequestProperty("User-Agent", "RideComm/${BuildConfig.VERSION_NAME} (github.com/saquelain/intercomm-app)")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) throw HttpError(c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private class HttpError(val code: Int) : Exception("search $code")

    private fun mapsKey(context: Context): String? = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")?.takeIf { it.isNotBlank() }
    }.getOrNull()

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
