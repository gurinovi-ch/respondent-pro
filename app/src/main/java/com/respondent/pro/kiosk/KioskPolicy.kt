package com.respondent.pro.kiosk

/** Чистое описание политик — исполняет KioskManager. Без Android-зависимостей. */
sealed interface PolicyAction {
    object HideStatusBar : PolicyAction
    object DisableKeyguard : PolicyAction
    object BlockFactoryReset : PolicyAction
    object BlockSafeBoot : PolicyAction
    object MaxScreenTime : PolicyAction
    data class ProtectApp(val pkg: String) : PolicyAction
    data class WhitelistLockTask(val pkg: String) : PolicyAction
}

object KioskPolicy {
    /** Максимальное время до блокировки экрана, мс. Далее системной — экран не блокируется. */
    const val MAX_LOCK_TIME_MS = Long.MAX_VALUE / 2

    // Батарея: системное управление (charge limit, battery saver) не трогаем —
    // ни DISALLOW_CONFIG_BATTERY, ни STAY_ON_WHILE_PLUGGED_IN не выставляем.

    /**
     * Минимальная яркость окна приложения: 80%.
     * Системная настройка яркости при этом НЕ изменяется — при выходе
     * из приложения действует то, что выставлено в настройках Android.
     */
    const val MIN_BRIGHTNESS_RATIO = 0.8f

    /**
     * Яркость окна приложения (доля 0..1): максимум системной яркости и минимума.
     * Android 90% → 0.9; Android 50% → 0.8. Применяется всегда, без Device Owner.
     */
    fun windowBrightness(systemBrightness: Float): Float =
        if (systemBrightness > MIN_BRIGHTNESS_RATIO) systemBrightness else MIN_BRIGHTNESS_RATIO

    fun actions(isDeviceOwner: Boolean, pkg: String): List<PolicyAction> {
        if (!isDeviceOwner) return emptyList()
        return listOf(
            PolicyAction.HideStatusBar,
            PolicyAction.DisableKeyguard,
            PolicyAction.BlockFactoryReset,
            PolicyAction.BlockSafeBoot,
            PolicyAction.MaxScreenTime,
            PolicyAction.ProtectApp(pkg),
            PolicyAction.WhitelistLockTask(pkg)
        )
    }
}

object RestartPolicy {
    /**
     * Перезапускать, только если приложение в фоне И нет выхода в системные настройки
     * (excursion) — иначе watchdog вытаскивал бы пользователя из настроек (spec §6).
     */
    fun shouldRestart(isForeground: Boolean, excursionActive: Boolean): Boolean =
        !isForeground && !excursionActive
}
