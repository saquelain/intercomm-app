package com.ridecomm.app.vote

/** Stops the group can vote on. */
enum class VoteKind(val emoji: String, val label: String, val asking: String, val stopName: String) {
    BREAK("☕", "Break", "wants a break", "Break"),
    FUEL("⛽", "Fuel", "wants to stop for fuel", "Fuel stop"),
    FOOD("🍔", "Food", "wants to stop for food", "Food stop"),
}

/** One-way messages that don't need a vote. */
enum class QuickMessage(val emoji: String, val label: String, val says: String) {
    SLOW_DOWN("🐢", "Slow down", "slow down"),
    WAIT("✋", "Wait for me", "wait for me"),
}
