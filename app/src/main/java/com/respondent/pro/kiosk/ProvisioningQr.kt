package com.respondent.pro.kiosk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.io.FileOutputStream

/**
 * Payload для Android Enterprise QR-провижининга.
 * Формат ключей: android.app.extra.PROVISIONING_* (spec §8).
 */
object ProvisioningQr {

    private const val TAG = "ProvisioningQr"

    const val ADMIN_COMPONENT = "com.respondent.pro/.kiosk.KioskAdminReceiver"

    /**
     * @param apkSignatureChecksum URL-safe Base64 SHA-256 сертификата подписи APK по [apkUrl]
     *  (PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM) — обязателен: AOSP ManagedProvisioning
     *  (PackageDownloadInfo.validateFields) завершает провижининг ошибкой «Не удалось
     *  настроить устройство», если при заданном DOWNLOAD_LOCATION нет ни PACKAGE_CHECKSUM,
     *  ни SIGNATURE_CHECKSUM. Проверяется VerifyPackageTask после скачивания.
     */
    fun buildPayload(apkUrl: String, apkSignatureChecksum: String, wifiSsid: String?, wifiPassword: String?): String {
        val root = JsonObject()
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", ADMIN_COMPONENT)
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", apkSignatureChecksum)
        if (!wifiSsid.isNullOrBlank()) {
            root.addProperty("android.app.extra.PROVISIONING_WIFI_SSID", wifiSsid)
            if (!wifiPassword.isNullOrEmpty()) {
                root.addProperty("android.app.extra.PROVISIONING_WIFI_PASSWORD", wifiPassword)
                // Без этого ключа AOSP WifiConfigurationProvider трактует сеть как открытую
                // (NONE) и подключение с верным паролем проваливается. WPA = WPA/WPA2-PSK.
                root.addProperty("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE", "WPA")
            }
        }
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

    /** Сохраняет QR в cacheDir и открывает диалог отправки. false — при ошибке. */
    fun shareQr(context: Context, payload: String): Boolean {
        val bitmap = encodeQr(payload) ?: return false
        return try {
            val file = File(context.cacheDir, "provisioning_qr.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "QR"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "shareQr failed", e)
            false
        }
    }
}
