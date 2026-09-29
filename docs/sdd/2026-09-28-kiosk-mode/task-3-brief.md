### Task 3: KioskManager — политики DO и Lock Task (TDD решалок + проверка на устройстве)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/KioskPolicy.kt`
- Create: `app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt`
- Test: `app/src/test/java/com/respondent/pro/kiosk/KioskPolicyTest.kt`
- Modify: `app/src/main/java/com/respondent/pro/MainActivity.kt`

**Interfaces:**
- Consumes: `KioskAdminReceiver` (Task 1).
- Produces (используют Tasks 5, 6, 8):
  - `class KioskManager` с методами `isDeviceOwner(): Boolean`, `status(): KioskStatus`, `applyPolicies()`, `onActivityResumed(activity: Activity)`, `onActivityPaused()`, `beginExcursion(activity: Activity)`, `shouldRestart(): Boolean` и свойствами `isForeground: Boolean`, `excursionActive: Boolean`;
  - `data class KioskStatus(val deviceOwner: Boolean, val lockTaskPermitted: Boolean)`;
  - `object KioskPolicy { fun actions(isDeviceOwner: Boolean, pkg: String): List<PolicyAction> }`, `object RestartPolicy { fun shouldRestart(isForeground: Boolean, excursionActive: Boolean): Boolean }`, `sealed interface PolicyAction` (варианты: `HideStatusBar`, `DisableKeyguard`, `BlockFactoryReset`, `BlockSafeBoot`, `MaxScreenTime`, `ProtectApp(pkg)`, `WhitelistLockTask(pkg)`).

- [ ] **Step 1: Написать падающие тесты**

`app/src/test/java/com/respondent/pro/kiosk/KioskPolicyTest.kt`:

```kotlin
package com.respondent.pro.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPolicyTest {

    private val pkg = "com.respondent.pro"

    @Test
    fun `no device owner — no actions`() {
        assertTrue(KioskPolicy.actions(isDeviceOwner = false, pkg = pkg).isEmpty())
    }

    @Test
    fun `with device owner — full lockdown set`() {
        val actions = KioskPolicy.actions(isDeviceOwner = true, pkg = pkg)
        assertTrue(actions.contains(PolicyAction.HideStatusBar))
        assertTrue(actions.contains(PolicyAction.DisableKeyguard))
        assertTrue(actions.contains(PolicyAction.BlockFactoryReset))
        assertTrue(actions.contains(PolicyAction.BlockSafeBoot))
        assertTrue(actions.contains(PolicyAction.MaxScreenTime))
        assertTrue(actions.contains(PolicyAction.ProtectApp(pkg)))
        assertTrue(actions.contains(PolicyAction.WhitelistLockTask(pkg)))
    }

    @Test
    fun `restart when backgrounded without excursion`() {
        assertTrue(RestartPolicy.shouldRestart(isForeground = false, excursionActive = false))
    }

    @Test
    fun `no restart when foreground`() {
        assertFalse(RestartPolicy.shouldRestart(isForeground = true, excursionActive = false))
    }

    @Test
    fun `no restart during settings excursion`() {
        // Review Focus №2: watchdog не должен вытаскивать из системных настроек
        assertFalse(RestartPolicy.shouldRestart(isForeground = false, excursionActive = true))
    }
}
```

- [ ] **Step 2: Запустить — упасть**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskPolicyTest*"
```
Expected: FAIL — `unresolved reference: KioskPolicy`.

- [ ] **Step 3: Реализовать `KioskPolicy.kt`**

```kotlin
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
```

- [ ] **Step 4: Запустить — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskPolicyTest*"
```
Expected: PASS (5 тестов).

- [ ] **Step 5: Реализовать `KioskManager.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
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
            Log.d(TAG, "Device Owner not granted — policies skipped")
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
            Log.d(TAG, "Policies applied: ${actions.size}")
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
```

Импорт квадификатора: `import dagger.hilt.android.qualifiers.ApplicationContext`.

- [ ] **Step 6: Изменить `MainActivity.kt`**

Добавить импорты:

```kotlin
import android.view.WindowManager
import com.respondent.pro.kiosk.KioskManager
```

В класс добавить поле (рядом с `settingsRepository`):

```kotlin
    @Inject
    lateinit var kioskManager: KioskManager
```

В `onCreate` после `super.onCreate(savedInstanceState)`:

```kotlin
        // Экран никогда не гаснет (киоск, spec §5)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Политики DO — идемпотентно, без DO тихо пропускается
        kioskManager.applyPolicies()
```

Добавить лайфсайкл-хуки (в конец класса):

```kotlin
    override fun onResume() {
        super.onResume()
        kioskManager.onActivityResumed(this)
    }

    override fun onPause() {
        kioskManager.onActivityPaused()
        super.onPause()
    }
```

- [ ] **Step 7: Собрать, установить, проверить Lock Task на устройстве**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\platform-tools\adb.exe shell am start -n com.respondent.pro/.MainActivity
C:\platform-tools\adb.exe shell input keyevent KEYCODE_HOME
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
```
Expected: в выводе `mCurrentFocus` — `com.respondent.pro` (Home не выводит из приложения → Lock Task активен).
Также: `adb shell dumpsys device_policy | Select-String "Lock task"` — наш пакет в whitelist.

**Откат на время разработки** (если планшет «залип» и нужен доступ):
```powershell
C:\platform-tools\adb.exe shell am force-stop com.respondent.pro     # выход из приложения
C:\platform-tools\adb.exe shell dpm remove-device-owner              # снять DO (осознанно)
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/KioskPolicy.kt app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt app/src/test/java/com/respondent/pro/kiosk/KioskPolicyTest.kt app/src/main/java/com/respondent/pro/MainActivity.kt
git commit -m "feat(kiosk): KioskManager with DO policies and lock task (TDD)"
```

---

