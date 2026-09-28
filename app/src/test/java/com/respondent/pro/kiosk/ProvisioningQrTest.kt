package com.respondent.pro.kiosk

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningQrTest {

    private val url = "https://example.com/releases/latest/download/RESPONDENT.PRO.apk"

    @Test
    fun `payload contains admin component, download url and skip encryption`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, null)).asJsonObject
        assertEquals(
            "com.respondent.pro/.kiosk.KioskAdminReceiver",
            json["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"].asString
        )
        assertEquals(url, json["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"].asString)
        assertTrue(json["android.app.extra.PROVISIONING_SKIP_ENCRYPTION"].asBoolean)
    }

    @Test
    fun `wifi keys omitted when ssid is null or blank`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, "pass")).asJsonObject
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_SSID"))
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))

        val jsonBlank = JsonParser.parseString(ProvisioningQr.buildPayload(url, "  ", null)).asJsonObject
        assertFalse(jsonBlank.has("android.app.extra.PROVISIONING_WIFI_SSID"))
    }

    @Test
    fun `wifi keys present when ssid provided`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "secret1")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertEquals("secret1", json["android.app.extra.PROVISIONING_WIFI_PASSWORD"].asString)
    }

    @Test
    fun `password omitted when ssid given but password empty`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))
    }

    @Test
    fun `payload is valid json object`() {
        val parsed = JsonParser.parseString(ProvisioningQr.buildPayload(url, "Net", "pw"))
        assertTrue(parsed.isJsonObject)
    }
}
