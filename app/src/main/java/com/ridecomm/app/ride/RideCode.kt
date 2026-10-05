package com.ridecomm.app.ride

import java.security.SecureRandom

/** Short ride codes riders can read out loud; skips look-alike characters (0/O, 1/I/L). */
object RideCode {
    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(): String = (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Normalizes typed input: uppercase, allowed characters only, at most [LENGTH] long. */
    fun clean(input: String): String = input.uppercase().filter { it in ALPHABET }.take(LENGTH)
}
