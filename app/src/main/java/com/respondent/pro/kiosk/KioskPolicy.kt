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
