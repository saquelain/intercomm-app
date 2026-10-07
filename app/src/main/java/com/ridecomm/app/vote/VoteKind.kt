package com.ridecomm.app.vote

import androidx.annotation.DrawableRes
import com.ridecomm.app.R

/** Stops the group can vote on. */
enum class VoteKind(@DrawableRes val icon: Int, val label: String, val asking: String, val stopName: String) {
    BREAK(R.drawable.ms_coffee, "Break", "wants a break", "Break"),
    FUEL(R.drawable.ms_local_gas_station, "Fuel", "wants to stop for fuel", "Fuel stop"),
    FOOD(R.drawable.ms_restaurant, "Food", "wants to stop for food", "Food stop"),
}

/** One-way messages that don't need a vote. */
enum class QuickMessage(@DrawableRes val icon: Int, val label: String, val says: String) {
    SLOW_DOWN(R.drawable.ms_speed, "Slow down", "slow down"),
    WAIT(R.drawable.ms_front_hand, "Wait for me", "wait for me"),
}
