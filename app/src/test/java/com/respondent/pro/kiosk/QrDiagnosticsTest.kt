package com.respondent.pro.kiosk

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Диагностика QR-провижининга: чистая логика вердикта и чек-лист.
 * Зависимости (resolve/сеть/probe) инжектируются — здесь только подмены.
 */
class QrDiagnosticsTest {

    private val fullPayload = {
        ProvisioningQr.buildPayload(
            KioskConfig.APK_DOWNLOAD_URL,
            KioskConfig.APK_SIGNATURE_SHA256,
            "DiagNet",
            "pass1234"
        )
    }

    private fun diagnostics(
        sdkInt: Int = 29,
        setupWizard: Boolean = true,
        managedProvisioning: Boolean = true,
        network: Boolean = true,
        urlOk: Boolean = true,
        payload: () -> String = fullPayload
    ) = QrDiagnostics(
        sdkInt = sdkInt,
        hasSetupWizard = { setupWizard },
        hasManagedProvisioning = { managedProvisioning },
        networkOk = { network },
        probeUrl = { urlOk },
        payloadProvider = payload
    )

    @Test
    fun `all checks pass - ready`() {
        val result = diagnostics().run()
        assertEquals(QrCheck.entries.size, result.results.size)
        assertTrue(result.results.all { it.ok })
        assertTrue(result.ready)
        assertEquals(null, result.firstFailed)
    }

    @Test
    fun `android 7 requirement - sdk 24 ok sdk 23 fails`() {
        assertTrue(diagnostics(sdkInt = 24).run().ready)
        val result = diagnostics(sdkInt = 23).run()
        assertFalse(result.ready)
        val api = result.results.first { it.check == QrCheck.API_LEVEL }
        assertFalse(api.ok)
        assertEquals(QrCheck.API_LEVEL, result.firstFailed?.check)
    }

    @Test
    fun `missing setup wizard fails qr scanner check`() {
        val result = diagnostics(setupWizard = false).run()
        assertFalse(result.ready)
        assertFalse(result.results.first { it.check == QrCheck.QR_SCANNER }.ok)
    }

    @Test
    fun `missing managed provisioning fails its check`() {
        val result = diagnostics(managedProvisioning = false).run()
        assertFalse(result.ready)
        assertFalse(result.results.first { it.check == QrCheck.MANAGED_PROVISIONING }.ok)
    }

    @Test
    fun `no network fails network check`() {
        val result = diagnostics(network = false).run()
        assertFalse(result.ready)
        assertFalse(result.results.first { it.check == QrCheck.NETWORK }.ok)
    }

    @Test
    fun `unreachable apk url fails url check`() {
        val result = diagnostics(urlOk = false).run()
        assertFalse(result.ready)
        assertFalse(result.results.first { it.check == QrCheck.APK_URL }.ok)
    }

    // Реальная конфигурация (KioskConfig + ProvisioningQr) обязана проходить
    // самопроверку — регрессия payload (как дофиксовая без checksum) валит тест.
    @Test
    fun `real kiosk configuration passes payload self-check`() {
        val result = diagnostics(payload = fullPayload).run()
        val payloadResult = result.results.first { it.check == QrCheck.PAYLOAD }
        assertTrue("payload self-check failed: ${payloadResult.detail}", payloadResult.ok)
        assertTrue(result.ready)
    }

    @Test
    fun `payload without signature checksum fails with field detail`() {
        val broken = {
            val json = JsonParser.parseString(fullPayload()).asJsonObject
            json.remove("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM")
            json.toString()
        }
        val result = diagnostics(payload = broken).run()
        val payloadResult = result.results.first { it.check == QrCheck.PAYLOAD }
        assertFalse(payloadResult.ok)
        assertTrue(payloadResult.detail!!.contains("SIGNATURE_CHECKSUM"))
        assertFalse(result.ready)
    }

    @Test
    fun `payload with password but no wifi security type fails`() {
        val broken = {
            val json = JsonParser.parseString(fullPayload()).asJsonObject
            json.remove("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE")
            json.toString()
        }
        val payloadResult = diagnostics(payload = broken).run()
            .results.first { it.check == QrCheck.PAYLOAD }
        assertFalse(payloadResult.ok)
        assertTrue(payloadResult.detail!!.contains("WIFI_SECURITY_TYPE"))
    }

    @Test
    fun `payload with password and wpa security passes`() {
        val payload = {
            val root = JsonObject()
            root.addProperty(
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
                "com.respondent.pro/.kiosk.KioskAdminReceiver"
            )
            root.addProperty(
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
                KioskConfig.APK_DOWNLOAD_URL
            )
            root.addProperty(
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM",
                KioskConfig.APK_SIGNATURE_SHA256
            )
            root.addProperty("android.app.extra.PROVISIONING_WIFI_SSID", "Net")
            root.addProperty("android.app.extra.PROVISIONING_WIFI_PASSWORD", "pw")
            root.addProperty("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE", "WPA")
            root.toString()
        }
        assertTrue(diagnostics(payload = payload).run().ready)
    }

    @Test
    fun `invalid json payload fails gracefully`() {
        val payload = { "не json {" }
        val payloadResult = diagnostics(payload = payload).run()
            .results.first { it.check == QrCheck.PAYLOAD }
        assertFalse(payloadResult.ok)
    }

    @Test
    fun `payload without wifi password does not require security type`() {
        val payload = {
            val json = JsonParser.parseString(
                ProvisioningQr.buildPayload(
                    KioskConfig.APK_DOWNLOAD_URL,
                    KioskConfig.APK_SIGNATURE_SHA256,
                    null,
                    null
                )
            ).asJsonObject
            json.toString()
        }
        assertTrue(diagnostics(payload = payload).run().ready)
    }
}
