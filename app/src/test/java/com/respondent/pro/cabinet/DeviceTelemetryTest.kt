package com.respondent.pro.cabinet

import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceTelemetryTest {

    @Test
    fun `newestFix picks latest timestamp`() {
        val old = GeoFix(55.75, 37.61, 1_000L)
        val fresh = GeoFix(59.93, 30.31, 2_000L)

        assertEquals(fresh, newestFix(listOf(old, fresh)))
        assertNull(newestFix(emptyList()))
    }

    @Test
    fun `cellularType maps android network types`() {
        assertEquals("5G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_NR))

        assertEquals("4G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_LTE))

        assertEquals("3G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_UMTS))
        assertEquals("3G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_HSPA))

        assertEquals("2G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals("2G", DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_GPRS))

        assertNull(DeviceTelemetryReporter.cellularType(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }
}
