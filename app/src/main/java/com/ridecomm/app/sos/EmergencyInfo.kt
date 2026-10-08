package com.ridecomm.app.sos

import org.json.JSONObject

/**
 * What a helper needs to know if I'm hurt: blood group, allergies or conditions, and who to call.
 * Stays on my phone and only goes out with my SOS (if I allow it).
 */
data class EmergencyInfo(
    val bloodGroup: String = "",
    /** Allergies, conditions, medicines, e.g. "Allergic to penicillin. Diabetic." */
    val medical: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
) {
    val isEmpty: Boolean get() = bloodGroup.isBlank() && medical.isBlank() && contactName.isBlank() && contactPhone.isBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        if (bloodGroup.isNotBlank()) put("blood", bloodGroup.trim())
        if (medical.isNotBlank()) put("medical", medical.trim())
        if (contactName.isNotBlank()) put("contact", contactName.trim())
        if (contactPhone.isNotBlank()) put("phone", contactPhone.trim())
    }

    /** "Blood group O+. Allergic to penicillin. Contact: Ammi +91 98…" for the SOS text message. */
    fun smsText(): String = buildList {
        if (bloodGroup.isNotBlank()) add("Blood group ${bloodGroup.trim()}.")
        if (medical.isNotBlank()) add(medical.trim().let { if (it.endsWith('.')) it else "$it." })
        val contact = listOf(contactName.trim(), contactPhone.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (contact.isNotEmpty()) add("Contact: $contact.")
    }.joinToString(" ")

    companion object {
        val BLOOD_GROUPS = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

        fun fromJson(o: JSONObject?): EmergencyInfo? {
            if (o == null) return null
            return EmergencyInfo(
                bloodGroup = o.optString("blood").take(MAX_SHORT),
                medical = o.optString("medical").take(MAX_MEDICAL),
                contactName = o.optString("contact").take(MAX_SHORT),
                contactPhone = o.optString("phone").take(MAX_SHORT),
            ).takeUnless { it.isEmpty }
        }

        fun parse(text: String): EmergencyInfo =
            if (text.isBlank()) EmergencyInfo() else runCatching { fromJson(JSONObject(text)) }.getOrNull() ?: EmergencyInfo()

        const val MAX_SHORT = 40
        const val MAX_MEDICAL = 200
    }
}
