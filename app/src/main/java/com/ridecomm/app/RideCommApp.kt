package com.ridecomm.app

import android.app.Application
import com.ridecomm.app.ride.RideService
import com.ridecomm.app.profile.Profile
import com.ridecomm.app.sos.SosManager
import com.ridecomm.app.ui.map.MapSetup

class RideCommApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        RideService.createNotificationChannel(this)
        SosManager.init(this)
        Profile.load(this)
        MapSetup.configure(this)
    }
}
