package com.respondent.pro.kiosk

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Payload для Android Enterprise QR-провижининга.
 * Формат ключей: android.app.extra.PROVISIONING_* (spec §8).
 */
object ProvisioningQr {

    private const val TAG = "ProvisioningQr"

    const val ADMIN_COMPONENT = "com.respondent.pro/.kiosk.KioskAdminReceiver"

    fun buildPayload(apkUrl: String, wifiSsid: String?, wifiPassword: String?): String {
        val root = JsonObject()
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", ADMIN_COMPONENT)
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
        if (!wifiSsid.isNullOrBlank()) {
            root.addProperty("android.app.extra.PROVISIONING_WIFI_SSID", wifiSsid)
            if (!wifiPassword.isNullOrEmpty()) {
                root.addProperty("android.app.extra.PROVISIONING_WIFI_PASSWORD", wifiPassword)
            }
        }
        root.addProperty("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
        return root.toString()
    }

    /** Кодирует payload в QR-изображение. Возвращает null при ошибке кодирования. */
    fun encodeQr(payload: String, size: Int = 640): Bitmap? = try {
        BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, size, size)
    } catch (e: Exception) {
        Log.e(TAG, "QR encode failed", e)
        null
    }
}
