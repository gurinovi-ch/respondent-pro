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

    /** Команды инструкции ADB — по одной, нажатие копирует (Task 8). */
    fun adbCommands(): List<String> = listOf(
        "adb install -r RESPONDENT.PRO.apk",
        "adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver",
        "adb shell cmd package set-home-activity com.respondent.pro/.MainActivity",
        "adb shell appops set com.respondent.pro SYSTEM_ALERT_WINDOW allow"
    )
}
