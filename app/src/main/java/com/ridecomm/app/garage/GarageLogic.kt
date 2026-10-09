package com.ridecomm.app.garage

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Something to do every so many km and/or months: an oil change, a chain lube. 0 means "not by that measure". */
data class ServiceItem(
    val id: String,
    val name: String,
    val everyKm: Int,
    val everyMonths: Int,
    val lastKm: Double,
    val lastAtMs: Long,
)

data class Bike(
    val id: String,
    val name: String,
    val reg: String = "",
    /** The odometer as the rider typed it. */
    val odoKm: Double,
    /** Km ridden with RideComm since then. */
    val addedKm: Double = 0.0,
    val items: List<ServiceItem> = emptyList(),
    val insuranceUntilMs: Long = 0,
    val pucUntilMs: Long = 0,
) {
    val odometer: Double get() = odoKm + addedKm
}

/** My bikes, which one I'm riding, and my licence's end date. Kept only on this phone. */
data class Garage(val bikes: List<Bike> = emptyList(), val currentId: String? = null, val licenceUntilMs: Long = 0) {
    val current: Bike? get() = bikes.firstOrNull { it.id == currentId } ?: bikes.firstOrNull()

    fun replace(bike: Bike) = copy(bikes = bikes.map { if (it.id == bike.id) bike else it })
}

enum class Urgency { OK, SOON, DUE }

/** One line about a bike or a document: "Oil change in 240 km". */
data class GarageNote(val text: String, val urgency: Urgency, val itemId: String? = null)

/** The rules for My garage (PROTOCOL.md "My garage"); pure, for tests and both screens. */
object GarageLogic {
    const val DAY_MS = 86_400_000L
    const val DOC_SOON_DAYS = 30
    const val ITEM_SOON_DAYS = 14

    private fun km(n: Double) = NumberFormat.getIntegerInstance(Locale.US).format(n.roundToInt())

    /** A new bike with the usual service items, counted from now. */
    fun newBike(name: String, reg: String, odoKm: Double, nowMs: Long): Bike {
        fun item(n: String, k: Int, m: Int) = ServiceItem(UUID.randomUUID().toString(), n, k, m, odoKm, nowMs)
        return Bike(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(40),
            reg = reg.trim().uppercase().take(20),
            odoKm = odoKm,
            items = listOf(
                item("Oil change", 3_000, 6),
                item("Chain clean & lube", 600, 0),
                item("Air filter", 10_000, 0),
                item("General service", 5_000, 12),
            ),
        )
    }

    fun addMonths(ms: Long, months: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        add(Calendar.MONTH, months)
    }.timeInMillis

    /** Whole days from now to [untilMs]: 0 is today, 1 tomorrow, -3 three days ago (by the calendar). */
    fun daysLeft(untilMs: Long, nowMs: Long): Int {
        fun day(ms: Long) = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((day(untilMs) - day(nowMs)).toDouble() / DAY_MS).roundToInt()
    }

    /** Km left before the item is due (null when it isn't counted in km). */
    fun kmLeft(item: ServiceItem, odometer: Double): Double? = if (item.everyKm > 0) item.lastKm + item.everyKm - odometer else null

    fun daysLeft(item: ServiceItem, nowMs: Long): Int? = if (item.everyMonths > 0) daysLeft(addMonths(item.lastAtMs, item.everyMonths), nowMs) else null

    fun urgency(item: ServiceItem, odometer: Double, nowMs: Long): Urgency {
        val k = kmLeft(item, odometer)
        val d = daysLeft(item, nowMs)
        if ((k != null && k <= 0) || (d != null && d <= 0)) return Urgency.DUE
        val soonKm = max(item.everyKm * 0.1, 100.0)
        if ((k != null && k <= soonKm) || (d != null && d <= ITEM_SOON_DAYS)) return Urgency.SOON
        return Urgency.OK
    }

    /** How far through its interval the item is, 0..1 (by km, or by time when it has no km). */
    fun used(item: ServiceItem, odometer: Double, nowMs: Long): Float {
        val byKm = if (item.everyKm > 0) ((odometer - item.lastKm) / item.everyKm).toFloat() else 0f
        val byTime = if (item.everyMonths > 0) {
            val end = addMonths(item.lastAtMs, item.everyMonths)
            ((nowMs - item.lastAtMs).toFloat() / (end - item.lastAtMs).coerceAtLeast(1))
        } else 0f
        return max(byKm, byTime).coerceIn(0f, 1f)
    }

    /** "Oil change due", "Oil change in 240 km", "Oil change in 12 days": whichever comes first. */
    fun itemNote(item: ServiceItem, odometer: Double, nowMs: Long): GarageNote {
        val u = urgency(item, odometer, nowMs)
        val k = kmLeft(item, odometer)
        val d = daysLeft(item, nowMs)
        val text = when {
            u == Urgency.DUE -> "${item.name} due"
            // Counted both ways: the days when they run out before the km do.
            k != null && d != null && d <= ITEM_SOON_DAYS && k > max(item.everyKm * 0.1, 100.0) -> "${item.name} in ${dayText(d)}"
            k != null -> "${item.name} in ${km(k)} km"
            d != null -> "${item.name} in ${dayText(d)}"
            else -> item.name
        }
        return GarageNote(text, u, item.id)
    }

    private fun dayText(d: Int) = when {
        d == 1 -> "1 day"
        d < 60 -> "$d days"
        else -> "${(d / 30.4).roundToInt()} months"
    }

    /** "Insurance ends in 12 days", "PUC expired 3 days ago"; null when no date is set. */
    fun docNote(label: String, untilMs: Long, nowMs: Long): GarageNote? {
        if (untilMs <= 0) return null
        val d = daysLeft(untilMs, nowMs)
        return when {
            d < 0 -> GarageNote("$label expired ${if (d == -1) "yesterday" else "${-d} days ago"}", Urgency.DUE)
            d == 0 -> GarageNote("$label ends today", Urgency.DUE)
            d == 1 -> GarageNote("$label ends tomorrow", Urgency.SOON)
            d <= DOC_SOON_DAYS -> GarageNote("$label ends in $d days", Urgency.SOON)
            else -> GarageNote("$label until ${dateText(untilMs)}", Urgency.OK)
        }
    }

    fun dateText(ms: Long): String = java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(java.util.Date(ms))

    /** What needs attention for the current bike and my licence, most urgent first. */
    fun alerts(g: Garage, nowMs: Long): List<GarageNote> {
        val b = g.current
        val notes = buildList {
            b?.items?.forEach { add(itemNote(it, b.odometer, nowMs)) }
            b?.let { docNote("Insurance", it.insuranceUntilMs, nowMs)?.let(::add) }
            b?.let { docNote("PUC", it.pucUntilMs, nowMs)?.let(::add) }
            docNote("Licence", g.licenceUntilMs, nowMs)?.let(::add)
        }
        return notes.filter { it.urgency != Urgency.OK }.sortedByDescending { it.urgency }
    }

    /** The next service on the current bike when nothing needs attention: "Next: Chain clean & lube in 420 km". */
    fun next(b: Bike, nowMs: Long): GarageNote? = b.items
        .filter { it.everyKm > 0 }
        .minByOrNull { kmLeft(it, b.odometer) ?: Double.MAX_VALUE }
        ?.let { itemNote(it, b.odometer, nowMs) }

    /** Said when a ride starts, if something is due: "Reminder: oil change is due on your Classic 350". */
    fun rideStartReminder(g: Garage, nowMs: Long): String? {
        val due = alerts(g, nowMs).firstOrNull { it.urgency == Urgency.DUE } ?: return null
        val b = g.current
        return if (due.itemId != null && b != null) {
            val item = b.items.first { it.id == due.itemId }
            "Reminder: ${item.name.lowercase()} is due on your ${b.name}"
        } else {
            "Reminder: ${due.text}"
        }
    }

    /** A ride ended: its km go on the current bike's odometer. */
    fun addRide(g: Garage, km: Double): Garage {
        val b = g.current ?: return g
        if (km < 0.3) return g
        return g.replace(b.copy(addedKm = floor((b.addedKm + km) * 10) / 10))
    }

    /** The rider typed the odometer: start counting from there. */
    fun setOdometer(b: Bike, km: Double) = b.copy(odoKm = km, addedKm = 0.0)

    fun done(b: Bike, itemId: String, nowMs: Long) =
        b.copy(items = b.items.map { if (it.id == itemId) it.copy(lastKm = b.odometer, lastAtMs = nowMs) else it })

    /** When document reminders go off: 9 am 30 days and 7 days before, and on the day (those still ahead). */
    fun reminderTimes(untilMs: Long, nowMs: Long): List<Long> {
        if (untilMs <= 0) return emptyList()
        return listOf(30, 7, 0).map { before ->
            Calendar.getInstance().apply {
                timeInMillis = untilMs
                add(Calendar.DAY_OF_YEAR, -before)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.filter { it > nowMs }
    }

    // ---- JSON (same as the web page's rc-web-garage) ----

    fun toJson(g: Garage): String = JSONObject()
        .put("bikes", JSONArray(g.bikes.map { b ->
            JSONObject().put("id", b.id).put("name", b.name).put("reg", b.reg).put("odo", b.odoKm).put("added", b.addedKm)
                .put("insurance", b.insuranceUntilMs).put("puc", b.pucUntilMs)
                .put("items", JSONArray(b.items.map { i ->
                    JSONObject().put("id", i.id).put("name", i.name).put("km", i.everyKm).put("months", i.everyMonths)
                        .put("lastKm", i.lastKm).put("lastAt", i.lastAtMs)
                }))
        }))
        .put("current", g.currentId ?: "")
        .put("licence", g.licenceUntilMs)
        .toString()

    fun fromJson(text: String): Garage {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return Garage()
        val bikes = o.optJSONArray("bikes") ?: JSONArray()
        return Garage(
            bikes = (0 until bikes.length()).mapNotNull { n ->
                val b = bikes.optJSONObject(n) ?: return@mapNotNull null
                val items = b.optJSONArray("items") ?: JSONArray()
                Bike(
                    id = b.optString("id").ifBlank { return@mapNotNull null },
                    name = b.optString("name").ifBlank { "My bike" },
                    reg = b.optString("reg"),
                    odoKm = b.optDouble("odo", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                    addedKm = b.optDouble("added", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                    insuranceUntilMs = b.optLong("insurance", 0),
                    pucUntilMs = b.optLong("puc", 0),
                    items = (0 until items.length()).mapNotNull { k ->
                        val i = items.optJSONObject(k) ?: return@mapNotNull null
                        ServiceItem(
                            id = i.optString("id").ifBlank { return@mapNotNull null },
                            name = i.optString("name").ifBlank { "Service" },
                            everyKm = i.optInt("km", 0).coerceAtLeast(0),
                            everyMonths = i.optInt("months", 0).coerceAtLeast(0),
                            lastKm = i.optDouble("lastKm", 0.0).takeIf { it.isFinite() } ?: 0.0,
                            lastAtMs = i.optLong("lastAt", 0),
                        )
                    },
                )
            },
            currentId = o.optString("current").ifBlank { null },
            licenceUntilMs = o.optLong("licence", 0),
        )
    }
}
