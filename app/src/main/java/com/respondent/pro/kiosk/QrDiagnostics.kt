package com.respondent.pro.kiosk

import com.google.gson.JsonParser

/** Пункт диагностики QR-провижининга. */
enum class QrCheck {
    /** Android 7.0+ (SDK >= 24) — порог QR-провижининга и minSdk приложения. */
    API_LEVEL,

    /** В прошивке есть SetupWizard с QR-входом (Google или AOSP). */
    QR_SCANNER,

    /** Есть ManagedProvisioning — он валидирует payload (PreProvisioning). */
    MANAGED_PROVISIONING,

    /** Сеть есть: Wi-Fi/интернет доступны прямо сейчас. */
    NETWORK,

    /** Самопроверка payload: обязательные поля и WIFI_SECURITY_TYPE. */
    PAYLOAD,

    /** APK по APK_DOWNLOAD_URL доступен (HEAD-запрос). */
    APK_URL
}

data class QrCheckResult(
    val check: QrCheck,
    val ok: Boolean,
    /** Детали для отображения: имена полей, код ошибки, URL. */
    val detail: String? = null
)

data class QrDiagnosticsResult(val results: List<QrCheckResult>) {
    /** Готовность: упал ли хотя бы один пункт. */
    val ready: Boolean get() = results.all { it.ok }

    /** Первый провал — для вердикта «Не готов: …». */
    val firstFailed: QrCheckResult? get() = results.firstOrNull { !it.ok }
}

/**
 * Диагностика возможности QR-провижининга ПЕРЕД стиранием устройства.
 *
 * Чистая логика: все внешние зависимости (resolve компонентов, сеть, probe URL)
 * инжектируются лямбдами — [run] детерминирован и покрыт юнит-тестами.
 * Реальные зависимости собираются во ViewModel.
 *
 * Что НЕ проверяется (честное ограничение): «6 тапов» на экране приветствия
 * (внутренности OOBE закрыты) и FRP (системный persistent data block).
 */
class QrDiagnostics(
    private val sdkInt: Int,
    private val hasSetupWizard: () -> Boolean,
    private val hasManagedProvisioning: () -> Boolean,
    private val networkOk: () -> Boolean,
    private val probeUrl: (String) -> Boolean,
    private val payloadProvider: () -> String = {
        // Эталонный payload с тестовым Wi-Fi: прогоняет ВСЕ ветки сборки,
        // включая ветку WIFI_SECURITY_TYPE при пароле.
        ProvisioningQr.buildPayload(
            KioskConfig.APK_DOWNLOAD_URL,
            KioskConfig.APK_SIGNATURE_SHA256,
            "DiagnosticProbe",
            "diagnostic-pass"
        )
    },
    private val apkUrl: String = KioskConfig.APK_DOWNLOAD_URL
) {

    fun run(): QrDiagnosticsResult {
        val results = listOf(
            QrCheckResult(QrCheck.API_LEVEL, sdkInt >= MIN_SDK, "SDK=$sdkInt"),
            QrCheckResult(QrCheck.QR_SCANNER, hasSetupWizard()),
            QrCheckResult(QrCheck.MANAGED_PROVISIONING, hasManagedProvisioning()),
            QrCheckResult(QrCheck.NETWORK, networkOk()),
            checkPayload(),
            QrCheckResult(QrCheck.APK_URL, probeUrl(apkUrl), apkUrl)
        )
        return QrDiagnosticsResult(results)
    }

    private fun checkPayload(): QrCheckResult {
        val json = try {
            JsonParser.parseString(payloadProvider()).asJsonObject
        } catch (e: Exception) {
            return QrCheckResult(QrCheck.PAYLOAD, false, "invalid JSON: ${e.javaClass.simpleName}")
        }

        val problems = mutableListOf<String>()
        if (json.str(FIELD_COMPONENT).isNullOrBlank()) problems += "COMPONENT_NAME"
        if (json.str(FIELD_DOWNLOAD_LOCATION).isNullOrBlank()) problems += "DOWNLOAD_LOCATION"
        if (json.str(FIELD_SIGNATURE_CHECKSUM).isNullOrBlank()) problems += "SIGNATURE_CHECKSUM"
        // Пароль без типа шифрования = попытка подключиться к открытой сети (баг №2).
        if (!json.str(FIELD_WIFI_PASSWORD).isNullOrBlank() &&
            json.str(FIELD_WIFI_SECURITY_TYPE) != SECURITY_WPA
        ) {
            problems += "WIFI_SECURITY_TYPE"
        }
        return QrCheckResult(
            QrCheck.PAYLOAD,
            problems.isEmpty(),
            problems.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
    }

    private fun com.google.gson.JsonObject.str(key: String): String? =
        if (has(key) && get(key).isJsonPrimitive) get(key).asString else null

    companion object {
        const val MIN_SDK = 24
        const val SECURITY_WPA = "WPA"

        private const val FIELD_COMPONENT =
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
        private const val FIELD_DOWNLOAD_LOCATION =
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
        private const val FIELD_SIGNATURE_CHECKSUM =
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"
        private const val FIELD_WIFI_PASSWORD =
            "android.app.extra.PROVISIONING_WIFI_PASSWORD"
        private const val FIELD_WIFI_SECURITY_TYPE =
            "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"
    }
}
