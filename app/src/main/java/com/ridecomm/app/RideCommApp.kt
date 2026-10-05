package com.ridecomm.app

import android.app.Application
import com.ridecomm.app.ride.RideService

class RideCommApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RideService.createNotificationChannel(this)
    }
}
