package com.respondent.pro.kiosk

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningQrTest {

    private val url = "https://example.com/releases/latest/download/RESPONDENT.PRO.apk"
    private val signatureChecksum = "_5QV8IU9UMdDefP21dj7X_CLJ2qbBHa2OGl081A-ti0="

    @Test
    fun `payload contains admin component, download url and skip encryption`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, null, null)).asJsonObject
        assertEquals(
            "com.respondent.pro/.kiosk.KioskAdminReceiver",
            json["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"].asString
        )
        assertEquals(url, json["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"].asString)
        assertTrue(json["android.app.extra.PROVISIONING_SKIP_ENCRYPTION"].asBoolean)
    }

    // AOSP ManagedProvisioning (PackageDownloadInfo.validateFields) падает с
    // «Package checksum or signature checksum must be provided», если при заданном
    // DOWNLOAD_LOCATION нет ни одного checksum — QR-провижининг мгновенно
    // завершался ошибкой «Не удалось настроить устройство». Берём SIGNATURE_CHECKSUM
    // (SHA-256 сертификата подписи): стабилен для всех сборок с одним ключом,
    // в отличие от PACKAGE_CHECKSUM (hash файла не может быть внутри самого файла).
    @Test
    fun `payload contains url-safe base64 signature checksum`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, null, null)).asJsonObject
        assertEquals(
            signatureChecksum,
            json["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"].asString
        )
        assertFalse(json.has("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"))
    }

    @Test
    fun `wifi keys omitted when ssid is null or blank`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, null, "pass")).asJsonObject
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_SSID"))
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))

        val jsonBlank = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "  ", null)).asJsonObject
        assertFalse(jsonBlank.has("android.app.extra.PROVISIONING_WIFI_SSID"))
    }

    @Test
    fun `wifi keys present when ssid provided`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "MyNet", "secret1")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertEquals("secret1", json["android.app.extra.PROVISIONING_WIFI_PASSWORD"].asString)
    }

    // Без WIFI_SECURITY_TYPE AOSP WifiConfigurationProvider трактует сеть как
    // открытую (NONE) и подключение с верным паролем проваливается.
    @Test
    fun `wifi security type wpa present when password provided`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "MyNet", "secret1")).asJsonObject
        assertEquals("WPA", json["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"].asString)
    }

    @Test
    fun `wifi security type omitted when no password`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "MyNet", "")).asJsonObject
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"))
    }

    @Test
    fun `password omitted when ssid given but password empty`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "MyNet", "")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))
    }

    @Test
    fun `payload is valid json object`() {
        val parsed = JsonParser.parseString(ProvisioningQr.buildPayload(url, signatureChecksum, "Net", "pw"))
        assertTrue(parsed.isJsonObject)
    }
}
