package com.ridecomm.app.ride

import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTipsTest {
    @Test
    fun eachBrandGetsItsOwnSteps() {
        assertTrue(BatteryTips.forBrand("Xiaomi").contains("Autostart"))
        assertTrue(BatteryTips.forBrand("POCO").contains("No restrictions"))
        assertTrue(BatteryTips.forBrand("samsung").contains("Sleeping apps"))
        assertTrue(BatteryTips.forBrand("realme").contains("Allow background activity"))
        assertTrue(BatteryTips.forBrand("vivo").contains("high background power"))
        assertTrue(BatteryTips.forBrand(null).contains("Unrestricted"))
        assertTrue(BatteryTips.forBrand("SomeNewBrand").contains("Unrestricted"))
    }
}
