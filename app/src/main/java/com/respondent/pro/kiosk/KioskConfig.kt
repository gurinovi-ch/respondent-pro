package com.respondent.pro.kiosk

/**
 * Константы инфокиоска: откуда планшет скачивает APK (QR-провижининг,
 * инструкция ADB) и команды настройки. URL — GitHub Releases (spec §13.5).
 */
object KioskConfig {

    /** Организация/репозиторий: публичный репозиторий, создан в Task 7. */
    const val OWNER_REPO = "gurinovi-ch/respondent-pro"

    const val APK_DOWNLOAD_URL =
        "https://github.com/gurinovi-ch/respondent-pro/releases/download/v1.1-test/RESPONDENT.PRO.apk"

    /**
     * URL-safe Base64 SHA-256 СЕРТИФИКАТА ПОДПИСИ APK (PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM).
     * Значение СТАБИЛЬНО для всех сборок, подписанных этим же (debug) ключом — в отличие от
     * PACKAGE_CHECKSUM (hash файла), который пришлось бы менять при каждой заливке нового APK
     * на релиз. Пересчёт: `apksigner verify --print-certs <apk>` → "certificate SHA-256 digest"
     * → Base64 URL_SAFE. Для debug-ключа: sha256=ff9415f0853d50c74379f3f6d5d8fb5ff08b276a9b0476b6386974f3503eb62d.
     * Менять ТОЛЬКО при смене ключа подписи.
     */
    const val APK_SIGNATURE_SHA256 =
        "_5QV8IU9UMdDefP21dj7X_CLJ2qbBHa2OGl081A-ti0="

    /** Команды инструкции ADB — по одной, нажатие копирует (Task 8). */
    fun adbCommands(): List<String> = listOf(
        "adb install -r RESPONDENT.PRO.apk",
        "adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver",
        "adb shell cmd package set-home-activity com.respondent.pro/.MainActivity",
        "adb shell appops set com.respondent.pro SYSTEM_ALERT_WINDOW allow"
    )
}
