package com.respondent.pro.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class KioskStatus(val deviceOwner: Boolean, val lockTaskPermitted: Boolean)

/**
 * Единая точка киоск-режима: статус DO, применение политик,
 * вход/выход из Lock Task, флаги для watchdog (spec §5–7).
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "KioskManager"
    }

    val adminComponent: ComponentName = ComponentName(context, KioskAdminReceiver::class.java)

    @Volatile
    var isForeground: Boolean = false
        private set

    @Volatile
    var excursionActive: Boolean = false
        private set

    private val dpm: DevicePolicyManager?
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    fun isDeviceOwner(): Boolean = try {
        dpm?.isDeviceOwnerApp(context.packageName) == true
    } catch (e: Exception) {
        Log.e(TAG, "isDeviceOwnerApp failed", e)
        false
    }

    fun status(): KioskStatus {
        val owner = isDeviceOwner()
        val lockPermitted = owner && try {
            dpm?.isLockTaskPermitted(context.packageName) == true
        } catch (e: Exception) {
            Log.e(TAG, "isLockTaskPermitted failed", e)
            false
        }
        return KioskStatus(deviceOwner = owner, lockTaskPermitted = lockPermitted)
    }

    /** Идемпотентно. Вызывается из MainActivity.onCreate. Без DO — тихо ничего не делает. */
    fun applyPolicies() {
        val dpm = dpm ?: return
        val actions = KioskPolicy.actions(isDeviceOwner(), context.packageName)
        if (actions.isEmpty()) {
            Log.i(TAG, "Device Owner not granted — policies skipped")
            return
        }
        try {
            for (action in actions) {
                when (action) {
                    PolicyAction.HideStatusBar -> dpm.setStatusBarDisabled(adminComponent, true)
                    PolicyAction.DisableKeyguard -> dpm.setKeyguardDisabled(adminComponent, true)
                    PolicyAction.BlockFactoryReset ->
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                    PolicyAction.BlockSafeBoot ->
                        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    PolicyAction.MaxScreenTime ->
                        dpm.setMaximumTimeToLock(adminComponent, KioskPolicy.MAX_LOCK_TIME_MS)
                    is PolicyAction.ProtectApp ->
                        dpm.setUninstallBlocked(adminComponent, action.pkg, true)
                    is PolicyAction.WhitelistLockTask -> {
                        dpm.setLockTaskPackages(adminComponent, arrayOf(action.pkg))
                        if (Build.VERSION.SDK_INT >= 28) {
                            dpm.setLockTaskFeatures(adminComponent, 0)
                        }
                    }
                }
            }
            Log.i(TAG, "Policies applied: ${actions.size}")
        } catch (e: SecurityException) {
            Log.e(TAG, "applyPolicies failed", e)
        }
    }

    fun onActivityResumed(activity: Activity) {
        isForeground = true
        excursionActive = false
        startLockTask(activity)
    }

    fun onActivityPaused() {
        isForeground = false
    }

    /** Временный выход из Lock Task в системные настройки (spec §6). */
    fun beginExcursion(activity: Activity) {
        excursionActive = true
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "stopLockTask failed", e)
        }
    }

    /**
     * Деактивация режима киоска: выход из Lock Task и снятие Device Owner
     * штатным вызовом clearDeviceOwnerApp — приложение-владелец снимает
     * себя само, данные устройства не стираются. Возвращает успех.
     */
    fun disableKiosk(activity: Activity): Boolean {
        if (!isDeviceOwner()) return false
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "stopLockTask failed", e)
        }
        return try {
            // Снятие политики ProtectApp ДО отречения от владельца:
            // clearDeviceOwnerApp не сбрасывает метку блокировки удаления
            // в PackageManager — без этого остаётся навсегда
            // DELETE_FAILED_OWNER_BLOCKED и приложение нельзя удалить.
            dpm?.setUninstallBlocked(adminComponent, context.packageName, false)
            dpm?.clearDeviceOwnerApp(context.packageName)
            Log.i(TAG, "Device Owner cleared — kiosk disabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "clearDeviceOwnerApp failed", e)
            false
        }
    }

    fun shouldRestart(): Boolean = RestartPolicy.shouldRestart(isForeground, excursionActive)

    private fun startLockTask(activity: Activity) {
        if (!isDeviceOwner()) return
        try {
            activity.startLockTask()
        } catch (e: Exception) {
            Log.e(TAG, "startLockTask failed", e)
        }
    }
}
