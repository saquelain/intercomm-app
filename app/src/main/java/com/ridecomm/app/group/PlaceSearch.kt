package com.ridecomm.app.group

import android.content.Context
import com.ridecomm.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Places found for a search, and where they came from ("Google Maps" or "OpenStreetMap"). */
data class PlaceResults(val places: List<Place>, val source: String)

/**
 * Finds places by name, nearest to me first. Google Maps' own place search when this build's key allows it
 * (it knows the shops, dhabas and pumps Google Maps shows), otherwise OpenStreetMap: Photon, which is quick
 * enough to search as you type, then Nominatim when you tap Search. Typed or pasted coordinates and Google
 * Maps links (short maps.app.goo.gl ones too) skip the search.
 */
object PlaceSearch {
    private const val GOOGLE_URL = "https://places.googleapis.com/v1/places:searchText"
    private const val PHOTON_URL = "https://photon.komoot.io/api/?limit=8&lang=en&q="
    private const val PHOTON_REVERSE = "https://photon.komoot.io/reverse?lang=en"
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q="
    private const val TIMEOUT_MS = 12_000
    /** Search near me within this (Google) or prefer places around this box (Nominatim). */
    private const val BIAS_RADIUS_M = 50_000.0
    private val SHORT_LINK = Regex("""https?://(maps\.app\.goo\.gl|goo\.gl/maps)/\S+""")
    private val USER_AGENT = "RideComm/${BuildConfig.VERSION_NAME} (github.com/saquelain/intercomm-app)"

    /** Google said no (no Places API on the key, quota…): don't ask again until the app restarts. */
    @Volatile private var googleRefused = false

    /**
     * [typing]: searching as the rider types (Nominatim isn't asked then: its rules allow one search a
     * second and no search-as-you-type). Throws when nothing could be reached.
     */
    suspend fun search(context: Context, query: String, near: Pair<Double, Double>?, typing: Boolean = false): PlaceResults = withContext(Dispatchers.IO) {
        // A short share link hides where it points: follow it to the full link first.
        val text = SHORT_LINK.find(query.trim())?.value?.let { link -> runCatching { expand(link) }.getOrNull() } ?: query.trim()
        DestinationLogic.parseCoordinates(text)?.let { (lat, lon) ->
            val name = DestinationLogic.placeNameInLink(text) ?: "Pinned place"
            return@withContext PlaceResults(listOf(Place(name, String.format(Locale.US, "%.5f, %.5f", lat, lon), lat, lon)), "")
        }
        // A link without coordinates still names the place: search for that.
        val q = DestinationLogic.placeNameInLink(text) ?: query.trim()
        var lastError: Exception? = null
        GoogleApi.key(context)?.takeIf { !googleRefused }?.let { key ->
            try {
                val found = DestinationLogic.parseGoogleText(post(GOOGLE_URL, googleBody(q, near), GoogleApi.headers(context, key, "places.id,places.displayName,places.formattedAddress,places.location")))
                if (found.isNotEmpty()) return@withContext PlaceResults(found, "Google Maps")
            } catch (e: HttpError) {
                if (e.code in 400..403) googleRefused = true
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        try {
            val bias = near?.let { (lat, lon) -> String.format(Locale.US, "&lat=%.4f&lon=%.4f", lat, lon) }.orEmpty()
            val found = DestinationLogic.parsePhoton(get(PHOTON_URL + URLEncoder.encode(q, "UTF-8") + bias))
            if (found.isNotEmpty() || typing) return@withContext PlaceResults(found, "OpenStreetMap")
        } catch (e: Exception) {
            lastError = e
            if (typing) throw e
        }
        try {
            val box = near?.let { (lat, lon) -> String.format(Locale.US, "&viewbox=%.3f,%.3f,%.3f,%.3f", lon - 0.5, lat + 0.5, lon + 0.5, lat - 0.5) }.orEmpty()
            return@withContext PlaceResults(DestinationLogic.parseSearch(get(NOMINATIM_URL + URLEncoder.encode(q, "UTF-8") + box)), "OpenStreetMap")
        } catch (e: Exception) {
            throw lastError ?: e
        }
    }

    /** A name for a spot picked on the map ("Shivaji Nagar, Pune"), or null. */
    suspend fun nameOf(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            DestinationLogic.parsePhoton(get(PHOTON_REVERSE + String.format(Locale.US, "&lat=%.6f&lon=%.6f", lat, lon))).firstOrNull()
                ?.let { p -> listOf(p.name, p.detail.substringBefore(",")).filter { it.isNotBlank() }.distinct().joinToString(", ") }
        }.getOrNull()
    }

    private fun googleBody(q: String, near: Pair<Double, Double>?): String {
        val o = JSONObject().put("textQuery", q).put("maxResultCount", 8).put("languageCode", "en")
        near?.let { (lat, lon) ->
            o.put("locationBias", JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", lat).put("longitude", lon)).put("radius", BIAS_RADIUS_M)))
        }
        return o.toString()
    }

    /** Follows a short Google Maps link to the full one, which has the coordinates. */
    private fun expand(link: String): String {
        var url = link
        repeat(4) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.requestMethod = "HEAD"
                val next = c.getHeaderField("Location") ?: return url
                url = URL(URL(url), next).toString()
            } finally {
                c.disconnect()
            }
        }
        return url
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            // OpenStreetMap's services ask every app to say who it is.
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Language", "en")
            if (c.responseCode != 200) throw HttpError(c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) throw HttpError(c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private class HttpError(val code: Int) : Exception("search $code")
}
