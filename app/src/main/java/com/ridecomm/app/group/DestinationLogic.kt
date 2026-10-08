package com.ridecomm.app.group

import org.json.JSONArray

/** Where the group is heading, set by one rider for everyone ("Lonavala"). */
data class Destination(
    val id: String,
    val lat: Double,
    val lon: Double,
    val label: String,
    val byName: String,
    val byId: String,
    /** Wall clock when it was set; a newer destination replaces an older one. */
    val atMs: Long,
)

/** A search result: "Lonavala" / "Pune, Maharashtra, India". */
data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Destination rules and parsing, free of Android so they can be tested. */
object DestinationLogic {
    /** Closer than this counts as arrived. */
    const val ARRIVE_M = 300.0
    const val MAX_LABEL = 40

    fun newer(current: Destination?, incoming: Destination): Boolean =
        current == null || incoming.atMs > current.atMs || (incoming.atMs == current.atMs && incoming.id > current.id)

    private val googleData = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")
    private val pair = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")

    /**
     * Coordinates typed or pasted: "18.7546, 73.4062", a Google Maps link with them in it
     * (…/@18.75,73.40,15z, ?q=18.75,73.40, …!3d18.75!4d73.40) or geo:18.75,73.40. Null otherwise
     * (for example a short maps.app.goo.gl link, which hides them).
     */
    fun parseCoordinates(text: String): Pair<Double, Double>? {
        val m = googleData.find(text) ?: pair.find(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    /** OpenStreetMap (Nominatim) search results, best first. */
    fun parseSearch(json: String): List<Place> {
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val parts = o.optString("display_name").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val name = o.optString("name").ifBlank { parts.firstOrNull().orEmpty() }.ifBlank { return@mapNotNull null }
            val detail = parts.filter { it != name }.take(3).joinToString(", ")
            Place(name.take(MAX_LABEL), detail, lat, lon)
        }
    }
}
