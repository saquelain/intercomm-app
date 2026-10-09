package com.ridecomm.app.nearby

import com.ridecomm.app.group.GroupMath
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What to look for on the road. */
enum class PoiKind(val label: String, val one: String, val radiusM: Int, val googleTypes: List<String>, val osmFilter: String) {
    FUEL("Fuel", "Petrol pump", 20_000, listOf("gas_station"), """["amenity"="fuel"]"""),
    FOOD("Food", "Restaurant", 10_000, listOf("restaurant", "cafe"), """["amenity"~"^(restaurant|fast_food|cafe|food_court)$"]"""),
    MECHANIC("Mechanic", "Mechanic", 20_000, listOf("car_repair"), """["shop"~"^(car_repair|motorcycle|motorcycle_repair|tyres)$"]"""),
}

data class Poi(val id: String, val name: String, val lat: Double, val lon: Double)

/** A place on the road ahead: how far, how far off my direction ([deltaDeg], + is right) and which side. */
data class AheadPoi(val poi: Poi, val distanceM: Double, val deltaDeg: Double, val side: String?)

/**
 * "On the road ahead": places in the direction I'm riding, not behind me. Without a direction
 * (standing still, no destination) it's simply the nearest. Same rules as the web page (PROTOCOL.md).
 */
object AheadLogic {
    const val CONE_DEG = 40.0
    const val NEAR_M = 400.0
    const val NEAR_CONE_DEG = 90.0
    const val BEHIND_DEG = 100.0
    const val SIDE_MIN_M = 30.0
    const val SIDE_MAX_M = 2_000.0

    /** -180..180: how far [to] is clockwise from [from]. */
    fun angleDiff(from: Double, to: Double): Double = ((to - from) % 360 + 540) % 360 - 180

    fun ahead(lat: Double, lon: Double, bearing: Double?, pois: List<Poi>, maxM: Double = 20_000.0): List<AheadPoi> =
        pois.mapNotNull { p ->
            val d = GroupMath.distanceM(lat, lon, p.lat, p.lon)
            if (d > maxM) return@mapNotNull null
            if (bearing == null) return@mapNotNull AheadPoi(p, d, 0.0, null)
            val delta = angleDiff(bearing, GroupMath.bearingDeg(lat, lon, p.lat, p.lon))
            val inCone = abs(delta) <= CONE_DEG || (d <= NEAR_M && abs(delta) <= NEAR_CONE_DEG)
            if (!inCone) return@mapNotNull null
            val across = d * abs(sin(Math.toRadians(delta)))
            val side = when {
                across < SIDE_MIN_M || d > SIDE_MAX_M -> null
                delta > 0 -> "right"
                else -> "left"
            }
            AheadPoi(p, d, delta, side)
        }.sortedBy { score(it) }

    /** Distance along the road plus twice the distance off it: a pump just off the highway beats one far to the side. */
    fun score(a: AheadPoi): Double {
        val r = Math.toRadians(a.deltaDeg)
        return a.distanceM * abs(cos(r)) + 2 * a.distanceM * abs(sin(r))
    }

    /** I've ridden past it. */
    fun isBehind(a: AheadPoi): Boolean = abs(a.deltaDeg) > BEHIND_DEG && a.distanceM > 150

    /** The point [distanceM] away along [bearing] (for searching and for the weather ahead). */
    fun pointAhead(lat: Double, lon: Double, bearing: Double, distanceM: Double): Pair<Double, Double> {
        val r = 6_371_000.0
        val d = distanceM / r
        val b = Math.toRadians(bearing)
        val la = Math.toRadians(lat)
        val lo = Math.toRadians(lon)
        val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
        return Math.toDegrees(la2) to ((Math.toDegrees(lo2) + 540) % 360 - 180)
    }

    /** "6.2 km ahead · on your left", or "nearest" wording when there's no direction. */
    fun describe(a: AheadPoi, haveDirection: Boolean): String =
        GroupMath.shortDistance(a.distanceM) + (if (haveDirection) " ahead" else " away") + (a.side?.let { " · on your $it" } ?: "")

    // ---- Search results ----

    fun parseOverpass(json: String, kind: PoiKind): List<Poi> = runCatching {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: return@mapNotNull null
            val lon = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: return@mapNotNull null
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            val tags = e.optJSONObject("tags")
            val name = listOf("name", "brand", "operator").firstNotNullOfOrNull { tags?.optString(it)?.takeIf { s -> s.isNotBlank() } } ?: kind.one
            Poi("osm:${e.optString("type")}/${e.optLong("id")}", name.take(60), lat, lon)
        }
    }.getOrDefault(emptyList())

    fun parseGoogle(json: String): List<Poi> = runCatching {
        val places = JSONObject(json).optJSONArray("places") ?: JSONArray()
        (0 until places.length()).mapNotNull { i ->
            val p = places.optJSONObject(i) ?: return@mapNotNull null
            val loc = p.optJSONObject("location") ?: return@mapNotNull null
            val name = p.optJSONObject("displayName")?.optString("text").orEmpty().ifBlank { return@mapNotNull null }
            Poi("g:" + p.optString("id"), name.take(60), loc.optDouble("latitude"), loc.optDouble("longitude"))
        }
    }.getOrDefault(emptyList())

    fun overpassQuery(kind: PoiKind, lat: Double, lon: Double): String {
        val around = "(around:${kind.radiusM},$lat,$lon)"
        return "[out:json][timeout:20];(node${kind.osmFilter}$around;way${kind.osmFilter}$around;);out center tags 300;"
    }
}
