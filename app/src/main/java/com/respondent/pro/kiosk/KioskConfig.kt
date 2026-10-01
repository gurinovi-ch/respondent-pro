package com.respondent.pro.kiosk

/**
 * Константы инфокиоска: откуда планшет скачивает APK (QR-провижининг,
 * инструкция ADB) и команды настройки. URL — GitHub Releases (spec §13.5).
 */
object KioskConfig {

    /** Организация/репозиторий: публичный репозиторий, создан в Task 7. */
    const val OWNER_REPO = "gurinovi-ch/respondent-pro"

    const val APK_DOWNLOAD_URL =
        "https://github.com/gurinovi-ch/respondent-pro/releases/download/v1.0-test/RESPONDENT.PRO.apk"

    /**
     * URL-safe Base64 SHA-256 файла по [APK_DOWNLOAD_URL] (PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM).
     * Обновлять ОБЯЗАТЕЛЬНО вместе с [APK_DOWNLOAD_URL] при новом релизе —
     * значение вычисляется как Base64(URL_SAFE) от SHA-256 самого APK-файла релиза.
     * Значение для v1.0-test: sha256=a6b08af8ccc6bca369ea7181f34fff166b691afcc9080e4ff9ce7efc34a2d21c.
     */
    const val APK_DOWNLOAD_SHA256 =
        "prCK-MzGvKNp6nGB80__FmtpGvzJCA5P-c5-_DSi0hw="

    /** Команды инструкции ADB — по одной, нажатие копирует (Task 8). */
    fun adbCommands(): List<String> = listOf(
        "adb install -r RESPONDENT.PRO.apk",
        "adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver",
        "adb shell cmd package set-home-activity com.respondent.pro/.MainActivity",
        "adb shell appops set com.respondent.pro SYSTEM_ALERT_WINDOW allow"
    )
}
