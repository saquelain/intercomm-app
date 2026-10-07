package com.ridecomm.app.audio

import kotlin.math.pow

/**
 * How the wind filter decides and acts. Low / Medium / High are presets; every value can also be
 * fine-tuned.
 */
data class GateSettings(
    /** Voice-band level (dBFS) that counts as speech. Lower = quieter voices get through. */
    val thresholdDb: Float = -42f,
    /**
     * How much louder the low rumble may be than the voice band and still count as speech (dB).
     * Lower = stronger wind rejection, but deep or muffled voices may be cut.
     */
    val rumbleAllowanceDb: Float = 6f,
    /** Keep sending this long after speech stops, so word endings aren't cut. */
    val holdMs: Int = 500,
    /** How much quieter blocked sound gets; [SILENCE_DB] or more = complete silence. */
    val reductionDb: Float = SILENCE_DB,
) {
    /** Gain applied while blocking (0 = silence). */
    val floorGain: Float get() = if (reductionDb >= SILENCE_DB) 0f else 10f.pow(-reductionDb / 20f)

    /** Which preset this is, if any. */
    val preset: Preset? get() = Preset.entries.firstOrNull { it.settings == this }

    enum class Preset(val label: String, val settings: GateSettings) {
        LOW("Low", GateSettings(thresholdDb = -34f)), // only clear, close speech
        MEDIUM("Medium", GateSettings()),
        HIGH("High", GateSettings(thresholdDb = -50f)), // quiet voices too; lets more noise through
    }

    companion object {
        // Not Preset.MEDIUM.settings: the presets are built from this class, so that would be circular.
        val MEDIUM = GateSettings()
        const val SILENCE_DB = 60f

        // Ranges for fine-tuning.
        const val THRESHOLD_MIN = -60f
        const val THRESHOLD_MAX = -25f
        const val RUMBLE_MIN = -12f
        const val RUMBLE_MAX = 18f
        const val HOLD_MIN = 150
        const val HOLD_MAX = 1_500
        const val REDUCTION_MIN = 6f
    }
}
