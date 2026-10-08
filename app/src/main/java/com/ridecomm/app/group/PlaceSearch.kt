package com.ridecomm.app.group

import com.ridecomm.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds a place by name with OpenStreetMap's free search (Nominatim). Only runs when a rider taps
 * Search, which keeps well within its one-request-a-second rule. Typed or pasted coordinates (or a
 * maps link with them) skip the search.
 */
object PlaceSearch {
    private const val URL_BASE = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q="
    private const val TIMEOUT_MS = 12_000

    /** Throws when there's no internet; an empty list means nothing was found. */
    suspend fun search(query: String): List<Place> {
        DestinationLogic.parseCoordinates(query)?.let { (lat, lon) ->
            return listOf(Place("Pinned place", String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), lat, lon))
        }
        return withContext(Dispatchers.IO) {
            val c = URL(URL_BASE + URLEncoder.encode(query.trim(), "UTF-8")).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                // Nominatim asks every app to say who it is.
                c.setRequestProperty("User-Agent", "RideComm/${BuildConfig.VERSION_NAME} (github.com/saquelain/intercomm-app)")
                c.setRequestProperty("Accept-Language", "en")
                if (c.responseCode != 200) throw IllegalStateException("search ${c.responseCode}")
                DestinationLogic.parseSearch(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        }
    }
}
