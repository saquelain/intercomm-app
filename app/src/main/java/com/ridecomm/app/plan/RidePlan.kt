package com.ridecomm.app.plan

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** A place in a plan: the meeting point, a stop or where the ride ends. */
data class PlanPlace(val name: String, val lat: Double, val lon: Double)

/**
 * A ride planned ahead: when, where to meet, the stops and where it ends. It travels inside the
 * invite link (see PROTOCOL.md "Ride plans"), so it needs no server.
 */
data class RidePlan(
    val code: String,
    val title: String,
    val atMs: Long,
    val meet: PlanPlace,
    val stops: List<PlanPlace> = emptyList(),
    val dest: PlanPlace? = null,
    val by: String = "",
) {
    /** "Sunday Lonavala ride", or just "Ride" without a title. */
    val label: String get() = title.ifBlank { "Ride" }
}

object PlanCodec {
    const val MAX_TEXT = 60
    const val MAX_STOPS = 5
    /** Plans are kept until this long after they start. */
    const val KEEP_AFTER_MS = 12 * 60 * 60 * 1000L

    private fun place(p: PlanPlace) = JSONObject().put("name", p.name.take(MAX_TEXT)).put("lat", p.lat).put("lon", p.lon)

    private fun placeFrom(o: JSONObject?): PlanPlace? {
        o ?: return null
        if (!o.has("lat") || !o.has("lon")) return null
        val lat = o.optDouble("lat")
        val lon = o.optDouble("lon")
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return PlanPlace(o.optString("name").take(MAX_TEXT).ifBlank { "Pinned place" }, lat, lon)
    }

    /** The plan as JSON; with [withCode] for keeping it on this phone (links carry the code outside). */
    fun toJson(plan: RidePlan, withCode: Boolean = false): JSONObject = JSONObject().apply {
        put("v", 1)
        if (withCode) put("code", plan.code)
        if (plan.title.isNotBlank()) put("title", plan.title.take(MAX_TEXT))
        put("at", plan.atMs)
        put("meet", place(plan.meet))
        put("stops", JSONArray().apply { plan.stops.take(MAX_STOPS).forEach { put(place(it)) } })
        plan.dest?.let { put("dest", place(it)) }
        if (plan.by.isNotBlank()) put("by", plan.by.take(MAX_TEXT))
    }

    fun fromJson(o: JSONObject, code: String? = null): RidePlan? = runCatching {
        val meet = placeFrom(o.optJSONObject("meet")) ?: return null
        val at = o.optLong("at", 0)
        if (at <= 0) return null
        val stops = o.optJSONArray("stops")?.let { a -> (0 until a.length()).mapNotNull { placeFrom(a.optJSONObject(it)) } }.orEmpty()
        RidePlan(
            code = code ?: o.optString("code"),
            title = o.optString("title").take(MAX_TEXT),
            atMs = at,
            meet = meet,
            stops = stops.take(MAX_STOPS),
            dest = placeFrom(o.optJSONObject("dest")),
            by = o.optString("by").take(MAX_TEXT),
        )
    }.getOrNull()

    /** For the link: base64url of the JSON, no padding. */
    fun encode(plan: RidePlan): String =
        Base64.encodeToString(toJson(plan).toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun decode(text: String, code: String): RidePlan? = runCatching {
        val json = String(Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        fromJson(JSONObject(json), code)
    }.getOrNull()

    fun listToJson(plans: List<RidePlan>): String = JSONArray().apply { plans.forEach { put(toJson(it, withCode = true)) } }.toString()

    fun listFrom(json: String): List<RidePlan> = runCatching {
        val a = JSONArray(json.ifBlank { "[]" })
        (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }.filter { it.code.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Adds or replaces [plan] (by ride code), drops old plans, soonest first. */
    fun merge(plans: List<RidePlan>, plan: RidePlan?, nowMs: Long): List<RidePlan> =
        (plans.filter { it.code != plan?.code } + listOfNotNull(plan))
            .filter { nowMs - it.atMs < KEEP_AFTER_MS }
            .sortedBy { it.atMs }

    /** Reminder times: an hour before, and at the start. Past ones are skipped. */
    fun reminderTimes(plan: RidePlan, nowMs: Long): List<Long> =
        listOf(plan.atMs - 60 * 60_000L, plan.atMs).filter { it > nowMs }
}
