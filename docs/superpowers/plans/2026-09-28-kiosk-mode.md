# Kiosk Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Превратить планшет в автономный инфокиоск: автозапуск при загрузке, главный лаунчер, автоперезапуск при сбоях, полная блокировка от посторонних через Device Owner + Lock Task, статус и инструкции настройки (ADB/QR) в настройках приложения.

**Architecture:** Новый пакет `com.respondent.pro.kiosk`: `KioskManager` (единая точка киоска, Hilt `@Singleton`), чистые логики-решалки `KioskPolicy`/`RestartPolicy` (юнит-тесты, без Android), receiver'ы (`BootReceiver`, `RestartReceiver`, `KioskAdminReceiver`), `WatchdogScheduler` (AlarmManager-цепочка), `ProvisioningQr` (QR-payload через Gson). Интеграция: `MainActivity` (лайфсайкл-хуки, HOME-intent, `FLAG_KEEP_SCREEN_ON`), `RespondentApp` (перезапуск после крэша), `SettingsScreen` (Card «Инфокиоск»: статус + spoilера ADB/QR + генератор QR).

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Hilt, DevicePolicyManager/AlarmManager (нативные API), Gson (payload), ZXing (`com.journeyapps:zxing-android-embedded:4.3.0`), JUnit 4, adb.

**Spec:** `docs/superpowers/specs/2026-09-28-kiosk-mode-design.md`

## Global Constraints

- `minSdk = 24` / `targetSdk = 35` / `compileSdk = 35` — поддержка Android 7–15; **все** системные вызовы — под `Build.VERSION.SDK_INT`-гардами там, где API старше 24 (spec §4).
- Сборка: `$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug` в `C:\projects\feedback-app`. Юнит-тесты: `$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest`.
- adb: `C:\platform-tools\adb.exe` (устройство подключено по USB).
- Новые строки интерфейса — только в `ui/i18n/AppStrings.kt`, всегда ru + en (конвенция проекта).
- БД (Room) и DataStore не меняются (spec §2).
- Идентификаторы (spec §8/§14): пакет `com.respondent.pro`; админ-компонент `com.respondent.pro/.kiosk.KioskAdminReceiver`; имя ассета APK на GitHub Releases — `RESPONDENT.PRO.apk`.
- **Запрещено** выставлять политики, отключающие отладку/параметры разработчика (`DISALLOW_DEBUGGING_FEATURES`, `ADB_ENABLED=0`) — spec §5, решение 7.
- Деструктивные операции: `dpm remove-device-owner` и `reboot` — только явными шагами плана; **factory reset — только с прямого согласия пользователя** (в плане не выполняется).
- В каждой задаче — отдельный commit.

## Review Focus

Пять условий, которые spec подразумевает, но без которых пользователь заметит поломку первым:

1. **Фоновый старт из `RestartReceiver` заблокирован Android 10+** → после убийства процесса приложение не поднимется. Ожидание: перезапуск ≤ ~60 сек. Тест: Task 6, шаг «Проба P2» (`am kill` + ожидание 75 сек + проверка фокуса).
2. **Excursion-флаг не сработает** → watchdog утащит пользователя из системных настроек обратно каждую минуту. Ожидание: в настройках перезапуска НЕ происходит. Тесты: Task 3 `RestartPolicyTest` (excursion → `false`) и Task 6 — нахождение в настройках > 75 сек без возврата.
3. **Exact alarm запрещён (API 31+)** → страж молча перестанет работать. Ожидание: откат на неточный alarm. Тест: Task 5 `WatchdogSchedulerTest` (кейсы `(31,false)`/`(35,false)` → `INEXACT`).
4. **Device Owner не выдан/снят** → приложение падает при вызове DO-API. Ожидание: тихая деградация, статус ⚠️. Тесты: Task 3 `KioskPolicyTest` (`actions(false,…)` пусто) и Task 8 — `dpm remove-device-owner` → статус ⚠️ → возврат DO.
5. **QR-payload невалиден** → клиентский планшет молча не пройдёт провижининг. Ожидание: валидный JSON с точными ключами Android Enterprise. Тест: Task 2 `ProvisioningQrTest` (разбор через `JsonParser` + ассерт ключей).

---

## File Structure

**Создать:**

| Файл | Ответственность |
|------|-----------------|
| `app/src/main/java/com/respondent/pro/kiosk/KioskAdminReceiver.kt` | `DeviceAdminReceiver` — цель выдачи DO (логики нет) |
| `app/src/main/java/com/respondent/pro/kiosk/KioskPolicy.kt` | Чистые решалки: `PolicyAction`, `KioskPolicy.actions()`, `RestartPolicy.shouldRestart()` |
| `app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt` | `@Singleton`: статус DO, применение политик, lock task, excursion-флаги |
| `app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt` | `BOOT_COMPLETED` → запуск `MainActivity` |
| `app/src/main/java/com/respondent/pro/kiosk/WatchdogScheduler.kt` | Планирование alarm'ов (exact/inexact решалка) |
| `app/src/main/java/com/respondent/pro/kiosk/RestartReceiver.kt` | Приём alarm → перезапуск/переплан |
| `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt` | QR-payload (Gson) + кодирование QR (ZXing) |
| `app/src/main/java/com/respondent/pro/kiosk/KioskConfig.kt` | Константы: URL APK, adb-команды |
| `app/src/main/res/xml/device_admin.xml` | Политики админа |
| `app/src/main/res/xml/file_paths.xml` | FileProvider-пути для share QR |
| `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt` | Юнит-тесты payload |
| `app/src/test/java/com/respondent/pro/kiosk/KioskPolicyTest.kt` | Юнит-тесты решалок политик |
| `app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt` | Юнит-тесты решалки alarm'ов |

**Изменить:** `AndroidManifest.xml`, `MainActivity.kt`, `RespondentApp.kt`, `viewmodel/SettingsViewModel.kt`, `ui/screens/SettingsScreen.kt`, `ui/i18n/AppStrings.kt`, `app/build.gradle.kts` (ZXing).

**Замечание по компоновке UI (интерпретация spec §8):** в текущем `SettingsScreen` порядок блоков — … «Способ отправки» (№11) … «Инструкции» (№12, последний). Spec требует разместить «Инфокиоск» **после спойлера инструкций** — значит KioskCard станет последним блоком списка; формулировка spec «перед способом отправки» относилась к более раннему порядку и уточняется здесь: **KioskCard — после `InstructionsSpoiler`, последним.**

---

### Task 1: Фундамент — KioskAdminReceiver, манифест, проба P1 (выдача Device Owner)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/KioskAdminReceiver.kt`
- Create: `app/src/main/res/xml/device_admin.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: компонент `com.respondent.pro.kiosk.KioskAdminReceiver` (manifest-объявление + `device_admin.xml`) — на нём строятся `ComponentName` в `KioskManager` (Task 3) и adb-команды инструкции (Task 7/8). Разрешения `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW` декларируются здесь — их используют Task 4/5/6. `android:launchMode="singleTask"` у MainActivity — требование перезапуска из receiver'ов (Task 4/5).

- [ ] **Step 1: Создать `KioskAdminReceiver.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Цель выдачи Device Owner. Логики не содержит — объявлен в манифесте
 * с device_admin.xml, на этот компонент ссылается dpm set-device-owner.
 */
class KioskAdminReceiver : DeviceAdminReceiver()
```

- [ ] **Step 2: Создать `res/xml/device_admin.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
    </uses-policies>
</device-admin>
```

- [ ] **Step 3: Изменить `AndroidManifest.xml`**

В `<manifest>` добавить разрешения (после существующих):

```xml
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

В `<application>` — receiver админа (перед `<activity>`):

```xml
        <receiver
            android:name=".kiosk.KioskAdminReceiver"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>
```

У существующего `<activity android:name=".MainActivity">` добавить атрибут:

```xml
            android:launchMode="singleTask"
```

- [ ] **Step 4: Собрать и установить**

Run (PowerShell, `C:\projects\feedback-app`):
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 5: Проба P1 — выдать Device Owner**

```powershell
C:\platform-tools\adb.exe shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver
```
Expected: `Success: set device owner`. Если ошибка про аккаунты/пользователей — НЕ продолжать, сообщить партнёру (spec §10 P1).
Проверка:
```powershell
C:\platform-tools\adb.exe shell dumpsys device_policy
```
Expected: в начале вывода `mDeviceOwnerComponent: ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}` (строка с device owner присутствует).

При необходимости вернуть состояние (рабочая отладка на время разработки):
```powershell
C:\platform-tools\adb.exe shell dpm remove-device-owner   # только осознанно!
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/KioskAdminReceiver.kt app/src/main/res/xml/device_admin.xml app/src/main/AndroidManifest.xml
git commit -m "feat(kiosk): DeviceAdminReceiver, permissions, singleTask; probe P1"
```

---

### Task 2: ProvisioningQr — payload QR-провижининга (TDD)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt`
- Test: `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt`

**Interfaces:**
- Consumes: Gson (`com.google.code.gson`, уже в зависимостях).
- Produces: `object ProvisioningQr { const val ADMIN_COMPONENT: String; fun buildPayload(apkUrl: String, wifiSsid: String?, wifiPassword: String?): String }` — `buildPayload` используют Task 9 (UI-генератор); `ADMIN_COMPONENT` также в `KioskConfig` (Task 7). Кодирование QR (`encodeQr`) добавляется в Task 9.

- [ ] **Step 1: Написать падающий тест**

`app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt`:

```kotlin
package com.respondent.pro.kiosk

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningQrTest {

    private val url = "https://example.com/releases/latest/download/RESPONDENT.PRO.apk"

    @Test
    fun `payload contains admin component, download url and skip encryption`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, null)).asJsonObject
        assertEquals(
            "com.respondent.pro/.kiosk.KioskAdminReceiver",
            json["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"].asString
        )
        assertEquals(url, json["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"].asString)
        assertTrue(json["android.app.extra.PROVISIONING_SKIP_ENCRYPTION"].asBoolean)
    }

    @Test
    fun `wifi keys omitted when ssid is null or blank`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, "pass")).asJsonObject
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_SSID"))
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))

        val jsonBlank = JsonParser.parseString(ProvisioningQr.buildPayload(url, "  ", null)).asJsonObject
        assertFalse(jsonBlank.has("android.app.extra.PROVISIONING_WIFI_SSID"))
    }

    @Test
    fun `wifi keys present when ssid provided`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "secret1")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertEquals("secret1", json["android.app.extra.PROVISIONING_WIFI_PASSWORD"].asString)
    }

    @Test
    fun `password omitted when ssid given but password empty`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))
    }

    @Test
    fun `payload is valid json object`() {
        val parsed = JsonParser.parseString(ProvisioningQr.buildPayload(url, "Net", "pw"))
        assertTrue(parsed.isJsonObject)
    }
}
```

- [ ] **Step 2: Запустить — тест должен упасть**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Expected: FAIL — `unresolved reference: ProvisioningQr`.

- [ ] **Step 3: Реализовать `ProvisioningQr.kt`**

```kotlin
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
        BarcodeEncoder().encodeAsBitmap(payload, BarcodeFormat.QR_CODE, size, size)
    } catch (e: Exception) {
        Log.e(TAG, "QR encode failed", e)
        null
    }
}
```

- [ ] **Step 4: Добавить зависимость ZXing в `app/build.gradle.kts`**

В `dependencies { … }` после строки `implementation("com.sun.mail:android-activation:1.6.7")`:

```kotlin
    // QR-код для провижининга
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
```

(`encodeQr` использует `BarcodeEncoder`; тест payload от ZXing не зависит, но компиляция класса требует зависимость.)

- [ ] **Step 5: Запустить тест — должен пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Expected: `BUILD SUCCESSFUL`, 5 тестов PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt app/build.gradle.kts
git commit -m "feat(kiosk): provisioning QR payload (TDD) + zxing"
```

---

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

### Task 4: HOME-лаунчер и автозапуск после загрузки

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MainActivity` (Task 3 — singleTask уже установлен).
- Produces: `class BootReceiver : BroadcastReceiver()` — объявлен в манифесте с `BOOT_COMPLETED`; категория `HOME`+`DEFAULT` у MainActivity — потребуется Task 7 (команда `set-home-activity`) и проверке приёмки (Task 10).

- [ ] **Step 1: Создать `BootReceiver.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Автозапуск после загрузки системы (spec §3). БД/настройки не трогает. */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "BOOT_COMPLETED — launching MainActivity")
        try {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
        }
    }
}
```

Полный набор импортов файла:

```kotlin
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.respondent.pro.MainActivity
```

- [ ] **Step 2: Изменить `AndroidManifest.xml`**

Внутрь существующего intent-filter MainActivity добавить категории (после `LAUNCHER`):

```xml
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
```

Receiver после `KioskAdminReceiver`:

```xml
        <receiver
            android:name=".kiosk.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Собрать и установить**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 4: Проба P4 — сделать лаунчером по умолчанию**

```powershell
C:\platform-tools\adb.exe shell cmd package set-home-activity com.respondent.pro/.MainActivity
C:\platform-tools\adb.exe shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
```
Expected: во втором выводе — `com.respondent.pro/.MainActivity` (а не `com.tblenovo.launcher/...`).
Если команда не поддержана (Android 7–9) — это нормально (spec §4): лаунчер выберется системным диалогом при первом нажатии Home; команду оставить в инструкции с пометкой «Android 10+».

- [ ] **Step 5: Проба — перезагрузка**

```powershell
C:\platform-tools\adb.exe reboot
Start-Sleep -Seconds 60
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
```
Expected: `mCurrentFocus` содержит `com.respondent.pro` — приложение поднялось само после загрузки (BootReceiver) и захватило экран (Lock Task).
Проверить лог приёмки: `adb logcat -d -s BootReceiver:* KioskManager:*` — строка `BOOT_COMPLETED — launching MainActivity` и `Policies applied`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(kiosk): HOME launcher role + boot autostart"
```

---

### Task 5: Перезапуск после сбоя — CrashHandler + Watchdog (TDD решалки)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/WatchdogScheduler.kt`
- Create: `app/src/main/java/com/respondent/pro/kiosk/RestartReceiver.kt`
- Test: `app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt`
- Modify: `app/src/main/java/com/respondent/pro/RespondentApp.kt`
- Modify: `app/src/main/java/com/respondent/pro/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `KioskManager.shouldRestart()/isForeground/excursionActive` и `RestartPolicy` (Task 3), `MainActivity` singleTask (Task 1).
- Produces:
  - `object WatchdogScheduler { enum class AlarmMode { EXACT, INEXACT }; const val PERIOD_MS: Long; const val CRASH_DELAY_MS: Long; fun decide(sdkInt: Int, canScheduleExact: Boolean): AlarmMode; fun scheduleNext(context: Context); fun scheduleRestart(context: Context, delayMs: Long = CRASH_DELAY_MS) }` — `scheduleRestart` вызывает `RespondentApp`, `scheduleNext` — `MainActivity` и `RestartReceiver`;
  - `class RestartReceiver` с action'ами `com.respondent.pro.kiosk.ACTION_WATCHDOG` и `com.respondent.pro.kiosk.ACTION_RESTART`;
  - Hilt-entry-point `KioskEntryPoint` (в файле `RestartReceiver.kt`) для доступа к `KioskManager` из manifest-receiver'а.

- [ ] **Step 1: Написать падающий тест**

`app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt`:

```kotlin
package com.respondent.pro.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogSchedulerTest {

    @Test
    fun `exact alarm allowed before android 12`() {
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 29, canScheduleExact = false))
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 30, canScheduleExact = false))
    }

    @Test
    fun `exact alarm used when permitted on android 12+`() {
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 31, canScheduleExact = true))
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 35, canScheduleExact = true))
    }

    @Test
    fun `inexact fallback when exact alarm not permitted on android 12+`() {
        // Review Focus №3
        assertEquals(WatchdogScheduler.AlarmMode.INEXACT, WatchdogScheduler.decide(sdkInt = 31, canScheduleExact = false))
        assertEquals(WatchdogScheduler.AlarmMode.INEXACT, WatchdogScheduler.decide(sdkInt = 35, canScheduleExact = false))
    }
}
```

- [ ] **Step 2: Запустить — упасть**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*WatchdogSchedulerTest*"
```
Expected: FAIL — `unresolved reference: WatchdogScheduler`.

- [ ] **Step 3: Реализовать `WatchdogScheduler.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Цепочка alarm'ов: каждую минуту RestartReceiver проверяет — на экране ли приложение,
 * иначе перезапускает (spec §3). Точность: exact, при отказе (API 31+) — неточный.
 */
object WatchdogScheduler {

    private const val TAG = "WatchdogScheduler"

    const val ACTION_WATCHDOG = "com.respondent.pro.kiosk.ACTION_WATCHDOG"
    const val ACTION_RESTART = "com.respondent.pro.kiosk.ACTION_RESTART"

    const val PERIOD_MS = 60_000L
    const val CRASH_DELAY_MS = 1_000L

    private const val REQUEST_WATCHDOG = 1001
    private const val REQUEST_RESTART = 1002

    enum class AlarmMode { EXACT, INEXACT }

    /** Решалка (TDD): exact-alarm разрешён только с API 31 и только если не отозван. */
    fun decide(sdkInt: Int, canScheduleExact: Boolean): AlarmMode =
        if (sdkInt >= 31 && !canScheduleExact) AlarmMode.INEXACT else AlarmMode.EXACT

    private fun mode(context: Context): AlarmMode {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        return decide(Build.VERSION.SDK_INT, canExact)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RestartReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Следующий шаг периодического стража (вызывается при старте приложения и из receiver'а). */
    fun scheduleNext(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + PERIOD_MS
            val pi = pendingIntent(context, ACTION_WATCHDOG, REQUEST_WATCHDOG)
            if (mode(context) == AlarmMode.EXACT) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            Log.d(TAG, "Watchdog armed (${mode(context)})")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNext failed", e)
        }
    }

    /** Разовый перезапуск после крэша (spec §3: CrashHandler → ~1 сек). */
    fun scheduleRestart(context: Context, delayMs: Long = CRASH_DELAY_MS) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + delayMs
            val pi = pendingIntent(context, ACTION_RESTART, REQUEST_RESTART)
            if (mode(context) == AlarmMode.EXACT) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            Log.d(TAG, "Restart alarm in ${delayMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleRestart failed", e)
        }
    }
}
```

- [ ] **Step 4: Запустить тест — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*WatchdogSchedulerTest*"
```
Expected: PASS (3 теста).

- [ ] **Step 5: Реализовать `RestartReceiver.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.respondent.pro.MainActivity

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KioskEntryPoint {
    fun kioskManager(): KioskManager
}

/**
 * Приём alarm'ов стража и разового перезапуска (spec §3).
 * ACTION_WATCHDOG — всегда перепланирует следующий шаг (цепочка не рвётся),
 * и перезапускает приложение, если оно в фоне и не идёт excursion.
 */
class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WatchdogScheduler.ACTION_WATCHDOG -> {
                WatchdogScheduler.scheduleNext(context)
                maybeRestart(context)
            }
            WatchdogScheduler.ACTION_RESTART -> maybeRestart(context)
        }
    }

    private fun maybeRestart(context: Context) {
        val kiosk = EntryPointAccessors
            .fromApplication(context.applicationContext, KioskEntryPoint::class.java)
            .kioskManager()
        if (!kiosk.shouldRestart()) {
            Log.d(TAG, "Skip restart (foreground=${kiosk.isForeground}, excursion=${kiosk.excursionActive})")
            return
        }
        Log.d(TAG, "Restarting MainActivity")
        try {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(launch)
        } catch (e: Exception) {
            // Review Focus №1: на Android 10+ фоновый старт может быть заблокирован —
            // логируем; следующая попытка через 60 сек (цепочка alarm'ов переживает отказ)
            Log.e(TAG, "startActivity failed (background start restriction?)", e)
        }
    }
}
```

- [ ] **Step 6: Зарегистрировать receiver в `AndroidManifest.xml`**

После `BootReceiver`:

```xml
        <receiver
            android:name=".kiosk.RestartReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.respondent.pro.kiosk.ACTION_WATCHDOG" />
                <action android:name="com.respondent.pro.kiosk.ACTION_RESTART" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 7: Включить перезапуск в `RespondentApp.kt`**

Добавить импорт:

```kotlin
import com.respondent.pro.kiosk.WatchdogScheduler
```

Внутри лямбды `Thread.setDefaultUncaughtExceptionHandler { thread, throwable -> … }`, **после** блока записи лога в файл и **перед** `defaultHandler?.uncaughtException(...)`:

```kotlin
                // Планируем перезапуск киоска после крэша (spec §3)
                try {
                    WatchdogScheduler.scheduleRestart(context)
                } catch (_: Exception) {}
```

(`context` — параметр `installCrashHandler(context: Context)`, уже доступен в лямбде.)

- [ ] **Step 8: Запустить цепочку стража при старте приложения — `MainActivity.kt`**

Добавить импорт `import com.respondent.pro.kiosk.WatchdogScheduler` и в `onCreate` после `kioskManager.applyPolicies()`:

```kotlin
        // Перепланируем цепочку стража на каждый запуск (spec §3)
        WatchdogScheduler.scheduleNext(this)
```

- [ ] **Step 9: Собрать, установить, проба — крэш**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\platform-tools\adb.exe shell am start -n com.respondent.pro/.MainActivity
Start-Sleep -Seconds 3
C:\platform-tools\adb.exe shell am crash com.respondent.pro
Start-Sleep -Seconds 6
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
C:\platform-tools\adb.exe logcat -d -s WatchdogScheduler:* RestartReceiver:* RespondentApp:*
```
Expected: `mCurrentFocus` — `com.respondent.pro` (приложение поднялось); в логах — `Restart alarm in 1000ms`, `Restarting MainActivity`; в `filesDir/crash_log.txt` появилась запись крэша (`adb shell run-as com.respondent.pro cat files/crash_log.txt`).

**Проба P2 (`am kill`) переносится в Task 6** — легально перевести приложение в фон можно только после появления excursion-механики (иначе Lock Task не даст этого сделать).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/WatchdogScheduler.kt app/src/main/java/com/respondent/pro/kiosk/RestartReceiver.kt app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt app/src/main/AndroidManifest.xml app/src/main/java/com/respondent/pro/RespondentApp.kt app/src/main/java/com/respondent/pro/MainActivity.kt
git commit -m "feat(kiosk): crash restart + watchdog alarm chain (TDD)"
```

---

### Task 6: Выход в системные настройки — оверлей-кнопка и пробы P2/P5

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/SettingsExcursionOverlay.kt`
- Modify: `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt` (кнопка «Настройки Android»)

**Interfaces:**
- Consumes: `KioskManager.beginExcursion(activity)` (Task 3); `KioskManager.onActivityResumed` сбрасывает `excursionActive` (Task 3); `MainActivity` singleTask — кнопка оверлея возвращает через `Intent` к `MainActivity`.
- Produces: `object SettingsExcursionOverlay { fun show(context: Context); fun hide(context: Context) }` — `show` вызывает `SettingsScreen`, `hide` вызывается из `MainActivity.onResume` (шаг 3).

- [ ] **Step 1: Создать `SettingsExcursionOverlay.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.respondent.pro.MainActivity

/**
 * Плавающая кнопка «◀ В RESPONDENT.PRO» поверх системных настроек (spec §6).
 * Показывается только при наличии разрешения SYSTEM_ALERT_WINDOW.
 */
object SettingsExcursionOverlay {

    private const val TAG = "ExcursionOverlay"
    private const val OVERLAY_TEXT = "◀ В RESPONDENT.PRO"

    private var currentView: View? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Открывает системный экран выдачи разрешения «Поверх других окон». */
    fun openPermissionScreen(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPermissionScreen failed", e)
        }
    }

    fun show(context: Context) {
        if (currentView != null) return
        if (!canDraw(context)) {
            Log.d(TAG, "Overlay permission not granted — button not shown")
            return
        }
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = (16 * density).toInt()
                y = (96 * density).toInt() // выше системной панели навигации
            }
            val view = TextView(context).apply {
                text = OVERLAY_TEXT
                setBackgroundColor(0xE6000000.toInt())
                setTextColor(Color.WHITE)
                setPadding(
                    (14 * density).toInt(), (10 * density).toInt(),
                    (14 * density).toInt(), (10 * density).toInt()
                )
                textSize = 15f
                setOnClickListener {
                    try {
                        val back = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(back)
                    } catch (e: Exception) {
                        Log.e(TAG, "return to app failed", e)
                    }
                    hide(context)
                }
            }
            wm.addView(view, params)
            currentView = view
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "show failed", e)
        }
    }

    fun hide(context: Context) {
        val view = currentView ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "hide failed", e)
        }
        currentView = null
    }
}
```

- [ ] **Step 2: Изменить кнопку «Настройки Android» в `SettingsScreen.kt`**

Текущий код (строка ~205):

```kotlin
            // 10. Настройки Android
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                },
                ...
```

Заменить `onClick` на:

```kotlin
                onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        kioskManager.beginExcursion(activity)
                    }
                    if (SettingsExcursionOverlay.canDraw(context)) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        SettingsExcursionOverlay.show(context)
                    } else {
                        // Разрешения нет — один раз просим его выдать (spec §6)
                        SettingsExcursionOverlay.openPermissionScreen(context)
                    }
                },
```

Импорты в начало файла:

```kotlin
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.SettingsExcursionOverlay
```

`kioskManager` нужен в композиции — добавить в `SettingsScreen` (шаг 3). `context` там уже объявлен (`val context = LocalContext.current` — если в текущей версии именуется иначе, использовать существующее имя).

- [ ] **Step 3: Доставить `KioskManager` в `SettingsScreen`**

В `SettingsViewModel.kt` добавить импорт `com.respondent.pro.kiosk.KioskManager` и в конструктор:

```kotlin
    private val kioskManager: KioskManager
```
и публичный геттер для экрана:

```kotlin
    /** KioskManager — для excursion-выхода в системные настройки. */
    val kiosk: KioskManager get() = kioskManager
```

В `SettingsScreen.kt` внутри функции (после `val context = …`):

```kotlin
    val kioskManager = viewModel.kiosk
```

Примечание: UI-статус (`kioskStatus`) будет добавлен в Task 8 к тому же VM — здесь только доступ к менеджеру.

- [ ] **Step 4: Скрытие оверлея при возврате — `MainActivity.kt`**

Импорт:

```kotlin
import com.respondent.pro.kiosk.SettingsExcursionOverlay
```

В `onResume` **перед** `kioskManager.onActivityResumed(this)`:

```kotlin
        // Оверлей убираем первым — иначе он останется поверх нашего экрана
        SettingsExcursionOverlay.hide(this)
```

(`onActivityResumed` сбросит `excursionActive` и вернёт Lock Task.)

- [ ] **Step 5: Собрать и установить**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
Expected: `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 6: Проба — excursion и оверлей (ручная на планшете)**

1. Открыть настройки приложения: долгое нажатие (3 сек) на название организации → PIN `0000`.
2. Нажать «Настройки Android».
   - Первый раз: откроется системный экран «Поверх других окон» → включить тумблер для RESPONDENT.PRO → «Назад».
   - Повторно: откроются системные настройки, справа внизу — кнопка «◀ В RESPONDENT.PRO».
3. Нажать оверлей-кнопку → возврат в приложение, Lock Task восстановлен.

Проверка на ПК (P4-повтор для excursion):
```powershell
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
```
Expected: `com.respondent.pro`.

- [ ] **Step 7: Проба P2 — убийство процесса и watchdog (Review Focus №1)**

1. На планшете: Настройки приложения → «Настройки Android» (приложение уходит в фон, открыт системный экран).
2. На ПК:
```powershell
C:\platform-tools\adb.exe shell am kill com.respondent.pro
C:\platform-tools\adb.exe logcat -c
Start-Sleep -Seconds 75
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
C:\platform-tools\adb.exe logcat -d -s RestartReceiver:* WatchdogScheduler:*
```
Expected: `mCurrentFocus` — `com.respondent.pro` (страж поднял приложение ≤ ~60 сек); в логах `Restarting MainActivity`.
**Если `startActivity failed (background start restriction?)` и фокус не наш** — вернуться к spec §10 P2 (резерв: foreground-service с `START_STICKY`, который поднимает активность) — это отдельная эскалация к партнёру, не реализовывать молча.

Проверка «watchdog не мешает в настройях» (Review Focus №2):
3. Повторить шаг 1, **не убивать** процесс, оставаться в системных настройках **75 секунд**.
Expected: приложение НЕ вытаскивает обратно (excursion-флаг), фокус остаётся на системных настройках.

- [ ] **Step 8: Проба P5 — точность времени крэш-перезапуска**

```powershell
C:\platform-tools\adb.exe logcat -c
C:\platform-tools\adb.exe shell am crash com.respondent.pro
Start-Sleep -Seconds 4
C:\platform-tools\adb.exe logcat -d -s RestartReceiver:* | Select-String "Restarting"
C:\platform-tools\adb.exe shell dumpsys window | Select-String "mCurrentFocus"
```
Expected: перезапуск < 2 секунд после крэша, фокус — `com.respondent.pro`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/SettingsExcursionOverlay.kt app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt app/src/main/java/com/respondent/pro/MainActivity.kt
git commit -m "feat(kiosk): settings excursion with floating return button; probes P2/P5"
```

---

### Task 7: GitHub-репозиторий и Release APK (KioskConfig)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/KioskConfig.kt`
- Modify: ничего больше; используется существующий git (`master` уже закоммичен)

**Interfaces:**
- Consumes: собранная колонка kiosk-компонентов (Task 1–6) — APK в Release должен уметь быть DO.
- Produces: `object KioskConfig { const val APK_DOWNLOAD_URL: String; const val OWNER_REPO: String; fun adbCommands(): List<String> }` — `APK_DOWNLOAD_URL` используется Task 9 (QR payload), `adbCommands()` — Task 8 (инструкция ADB).

**ВНИМАНИЕ: задача интерактивная — потребуется учётная запись GitHub партнёра.**

- [ ] **Step 1: Проверить git-remote и доступ к GitHub**

```powershell
cd C:\projects\feedback-app
git remote -v
gh auth status
```
- Если remote уже есть — перейти к Step 3 (URL взять из remote).
- Если `gh` не установлен или не авторизован — остановиться и попросить партнёра выполнить `gh auth login` (интерактивно) либо сообщить имя GitHub-пользователя/организации для ручного создания репозитория.

- [ ] **Step 2: Создать публичный репозиторий и запушить**

```powershell
gh repo create respondent-pro --public --source . --push
```
(Имя `respondent-pro` — по умолчанию; если партнёр назначил другое — использовать его и зафиксировать в `OWNER_REPO`.)
Expected: создан `https://github.com/<owner>/respondent-pro`, ветка `master` запушена.

- [ ] **Step 3: Собрать APK и опубликовать Release с ассетом `RESPONDENT.PRO.apk`**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
Copy-Item app\build\outputs\apk\debug\app-debug.apk app\build\outputs\apk\debug\RESPONDENT.PRO.apk
gh release create v1.0-test app\build\outputs\apk\debug\RESPONDENT.PRO.apk --title "1.0-test" --notes "Инфокиоск: DO + Lock Task + автозапуск"
```
Expected: релиз `v1.0-test`, ассет `RESPONDENT.PRO.apk`.
Проверка URL (он должен отдаваться напрямую):
```powershell
(Invoke-WebRequest -Uri "https://github.com/<owner>/respondent-pro/releases/latest/download/RESPONDENT.PRO.apk" -Method Head -UseBasicParsing).StatusCode
```
Expected: `200`. Реальный `<owner>` подставить из `gh repo view --json url -q .url`.

- [ ] **Step 4: Создать `KioskConfig.kt` с реальными значениями**

```kotlin
package com.respondent.pro.kiosk

/**
 * Константы инфокиоска: откуда планшет скачивает APK (QR-провижининг,
 * инструкция ADB) и команды настройки. URL — GitHub Releases (spec §13.5).
 */
object KioskConfig {

    /** Организация/репозиторий: заполняется после создания репозитория (Step 3). */
    const val OWNER_REPO = "<owner>/respondent-pro"   // ← заменить на реальный owner из Step 3

    const val APK_DOWNLOAD_URL =
        "https://github.com/$OWNER_REPO/releases/latest/download/RESPONDENT.PRO.apk"

    /** Команды инструкции ADB — по одной, нажатие копирует (Task 8). */
    fun adbCommands(): List<String> = listOf(
        "adb install -r RESPONDENT.PRO.apk",
        "adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver",
        "adb shell cmd package set-home-activity com.respondent.pro/.MainActivity",
        "adb shell appops set com.respondent.pro SYSTEM_ALERT_WINDOW allow"
    )
}
```

`<owner>` в Step 4 — **единственное допустимое место «подстановки после выполнения шага»**: значение известно из Step 3 в той же задаче и подставляется немедленно; в следующем шаге проверяется, что плейсхолдеров в файле не осталось.

- [ ] **Step 5: Проверить, что плейсхолдеров не осталось**

```powershell
Select-String -Path app\src\main\java\com\respondent\pro\kiosk\KioskConfig.kt -Pattern "<owner>"
```
Expected: пусто (все `<owner>` заменены).

- [ ] **Step 6: Собрать и закоммитить**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/respondent/pro/kiosk/KioskConfig.kt
git commit -m "feat(kiosk): KioskConfig with GitHub Releases APK url and adb commands"
git push
```

---

### Task 8: Экран настроек — статус Device Owner + инструкция ADB

**Files:**
- Modify: `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt`
- Modify: `app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt`
- Test: `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt` (create)

**Interfaces:**
- Consumes: `KioskManager.status(): KioskStatus` (Task 3), `KioskConfig.adbCommands()/APK_DOWNLOAD_URL` (Task 7), `SettingsViewModel.kiosk` (Task 6).
- Produces: строки `kioskTitle`, `kioskStatusOwner`, `kioskStatusLock`, `kioskStatusNoOwner`, `kioskAdbSpoiler`, `kioskAdbSteps`, `kioskCmdHint` (ru+en) и состояние `SettingsViewModel.kioskStatus: StateFlow<KioskStatus?>` с `refreshKioskStatus()` — Task 9 добавит к `KioskCard` QR-часть, используя те же `kioskStatus`/раскрытие.

- [ ] **Step 1: Добавить строки в `AppStrings.kt`**

В класс `AppStrings` (после поля `instructionsTitle`):

```kotlin
    // Инфокиоск
    val kioskTitle: String,
    val kioskStatusOwner: String,
    val kioskStatusLock: String,
    val kioskStatusNoOwner: String,
    val kioskAdbSpoiler: String,
    val kioskAdbSteps: String,
    val kioskCmdHint: String,
```

В `ruStrings = AppStrings(…)` (в том же месте):

```kotlin
    kioskTitle = "ИНФОКИОСК",
    kioskStatusOwner = "✅ Device Owner выдан",
    kioskStatusLock = "Lock Task активен",
    kioskStatusNoOwner = "⚠️ Device Owner не выдан — полная блокировка недоступна",
    kioskAdbSpoiler = "Настройка через ADB (с ПК)",
    kioskAdbSteps = "1. Включите USB-отладку: Параметры → О телефоне → 7 касаний по «Номеру сборки».\n" +
        "2. Скачайте APK по ссылке ниже (нажатие копирует).\n" +
        "3. Подключите планшет к ПК и выполните команды по одной (нажатие копирует команду).\n" +
        "4. Вернитесь в приложение — статус должен стать ✅.",
    kioskCmdHint = "Нажмите на текст, чтобы скопировать",
```

В `enStrings = AppStrings(…)`:

```kotlin
    kioskTitle = "KIOSK MODE",
    kioskStatusOwner = "✅ Device Owner granted",
    kioskStatusLock = "Lock Task active",
    kioskStatusNoOwner = "⚠️ Device Owner not granted — full lockdown unavailable",
    kioskAdbSpoiler = "Setup via ADB (from PC)",
    kioskAdbSteps = "1. Enable USB debugging: Settings → About tablet → tap \"Build number\" 7 times.\n" +
        "2. Download the APK via the link below (tap to copy).\n" +
        "3. Connect the tablet to a PC and run the commands one by one (tap to copy).\n" +
        "4. Return to the app — status should become ✅.",
    kioskCmdHint = "Tap text to copy",
```

- [ ] **Step 2: Написать падающий тест локализации**

`app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt`:

```kotlin
package com.respondent.pro.ui.i18n

import org.junit.Assert.assertTrue
import org.junit.Test

class KioskStringsTest {

    private fun kioskValues(s: AppStrings) = listOf(
        s.kioskTitle, s.kioskStatusOwner, s.kioskStatusLock, s.kioskStatusNoOwner,
        s.kioskAdbSpoiler, s.kioskAdbSteps, s.kioskCmdHint
    )

    @Test
    fun `kiosk strings are present and non-blank in ru`() {
        assertTrue(kioskValues(ruStrings).all { it.isNotBlank() })
    }

    @Test
    fun `kiosk strings are present and non-blank in en`() {
        assertTrue(kioskValues(enStrings).all { it.isNotBlank() })
    }
}
```

- [ ] **Step 3: Запустить — упасть (строк ещё нет)**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: FAIL — компиляция: `unresolved reference: kioskTitle`.

- [ ] **Step 4: Статус в `SettingsViewModel.kt`**

Импорты:

```kotlin
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.KioskStatus
import kotlinx.coroutines.flow.MutableStateFlow
```
(`MutableStateFlow` уже импортирован — не дублировать.)

В конструктор добавить (после `telegramApi`):

```kotlin
    private val kioskManager: KioskManager
```

В тело класса (после `chatIdResult`):

```kotlin
    /** Статус инфокиоска — обновляется при каждом открытии экрана настроек */
    private val _kioskStatus = MutableStateFlow<KioskStatus?>(null)
    val kioskStatus: StateFlow<KioskStatus?> = _kioskStatus

    fun refreshKioskStatus() {
        _kioskStatus.value = kioskManager.status()
    }
```

- [ ] **Step 5: Прогнать тест — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: PASS (2 теста).

- [ ] **Step 6: `KioskCard` в `SettingsScreen.kt`**

Состояния (рядом с `instructionsExpanded`, строка ~44):

```kotlin
    var kioskExpanded by remember { mutableStateOf(false) }
    var adbExpanded by remember { mutableStateOf(false) }
```

Статус (после существующих `val … by … collectAsState()`; если collectAsState для settings уже есть — добавить рядом):

```kotlin
    val kioskStatus by viewModel.kioskStatus.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshKioskStatus() }
```

Вставка в разметку — **сразу после `InstructionsSpoiler(...)` и перед `Spacer(modifier = Modifier.height(24.dp))`** (последний блок списка, spec §8):

```kotlin
            // 13. Инфокиоск — статус и инструкции
            KioskCard(
                status = kioskStatus,
                expanded = kioskExpanded,
                onToggle = { kioskExpanded = !kioskExpanded },
                adbExpanded = adbExpanded,
                onToggleAdb = { adbExpanded = !adbExpanded }
            )
```

Композабл (в конец файла, после `InstructionsSpoiler`):

```kotlin
@Composable
private fun KioskCard(
    status: KioskStatus?,
    expanded: Boolean,
    onToggle: () -> Unit,
    adbExpanded: Boolean,
    onToggleAdb: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.kioskTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Статус виден всегда, даже свёрнутым
            Text(
                text = when {
                    status == null -> ""
                    status.deviceOwner && status.lockTaskPermitted ->
                        "${strings.kioskStatusOwner}\n${strings.kioskStatusLock}"
                    status.deviceOwner -> strings.kioskStatusOwner
                    else -> strings.kioskStatusNoOwner
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status?.deviceOwner == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // ▶ Настройка через ADB
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleAdb() },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.kioskAdbSpoiler,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(text = if (adbExpanded) "▲" else "▼")
                    }

                    AnimatedVisibility(visible = adbExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = strings.kioskAdbSteps,
                                style = MaterialTheme.typography.bodySmall
                            )
                            // Ссылка на APK — копируется по нажатию
                            CommandText(text = KioskConfig.APK_DOWNLOAD_URL)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.kioskCmdHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            KioskConfig.adbCommands().forEach { cmd ->
                                CommandText(text = cmd)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandText(text: String) {
    val context = LocalContext.current
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb", text))
            }
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    )
}
```

Импорты (добавить недостающие — проверить по факту компиляции):

```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.respondent.pro.kiosk.KioskConfig
import com.respondent.pro.kiosk.KioskStatus
```

(`LocalContext` — если не импортирован: `import androidx.compose.ui.platform.LocalContext`.)

- [ ] **Step 7: Собрать, установить, проверка статуса ✅**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
На планшете: долгое нажатие на название организации (3 сек) → PIN `0000` → внизу «ИНФОКИОСК» → статус зелёный `✅ Device Owner выдан / Lock Task активен` → раскрыть ADB → 4 команды видны, нажатие копирует (проверить: скопировать команду → вставить в любое поле).
Expected: статус ✅, команды копируются.

- [ ] **Step 8: Проверка статуса ⚠️ без DO (Review Focus №4)**

```powershell
C:\platform-tools\adb.exe shell dpm remove-device-owner
```
На планшете: выйти и заново открыть настройки (PIN `0000`) → статус `⚠️ Device Owner не выдан…` (красный), приложение НЕ упало.
Вернуть DO:
```powershell
C:\platform-tools\adb.exe shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver
```
Expected: `Success: set device owner`; перезайти в настройки → снова ✅.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt
git commit -m "feat(kiosk): DO status card and ADB instruction in settings (i18n)"
```

---

### Task 9: Генератор QR — ZXing, FileProvider, инструкция QR

**Files:**
- Modify: `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt`
- Modify: `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt`

**Interfaces:**
- Consumes: `ProvisioningQr.buildPayload/encodeQr` (Task 2), `KioskConfig.APK_DOWNLOAD_URL` (Task 7), `KioskCard` и состояния из Task 8.
- Produces: строки `kioskQrSpoiler`, `kioskQrSteps`, `kioskQrSsidLabel`, `kioskQrPasswordLabel`, `kioskQrShowButton`, `kioskQrShareButton` (ru+en); FileProvider-авторитет `${applicationId}.fileprovider`; логика `shareQr(context, payload): Boolean` внутри `ProvisioningQr`.

- [ ] **Step 1: Добавить строки QR — в `AppStrings.kt`**

В класс `AppStrings` (после `kioskCmdHint`):

```kotlin
    val kioskQrSpoiler: String,
    val kioskQrSteps: String,
    val kioskQrSsidLabel: String,
    val kioskQrPasswordLabel: String,
    val kioskQrShowButton: String,
    val kioskQrShareButton: String,
```

В `ruStrings`:

```kotlin
    kioskQrSpoiler = "Настройка через QR-код (без ПК)",
    kioskQrSteps = "1. Введите Wi-Fi (или оставьте пустым — сеть выберут в мастере) и нажмите «Показать QR-код».\n" +
        "2. Сохраните/отправите QR изображение на второй устройство (телефон).\n" +
        "3. Сбросьте планшет до заводского настроек.\n" +
        "4. На приветственном экране: Android 7–9 — 6 касаний по экрану; Android 10+ — иконка доступности → камера.\n" +
        "5. Наведите камеру на QR: планшет скачает APK, станет Device Owner и подключится к Wi-Fi.\n" +
        "6. После запуска: выберите лаунчер (один раз) и выдайте разрешение «Поверх других окон».",
    kioskQrSsidLabel = "Wi-Fi сеть (SSID)",
    kioskQrPasswordLabel = "Пароль Wi-Fi",
    kioskQrShowButton = "Показать QR-код",
    kioskQrShareButton = "Поделиться",
```

В `enStrings`:

```kotlin
    kioskQrSpoiler = "Setup via QR code (no PC)",
    kioskQrSteps = "1. Enter Wi-Fi (or leave empty — network is chosen in the wizard) and tap \"Show QR code\".\n" +
        "2. Save/share the QR image to another device (phone).\n" +
        "3. Factory-reset the tablet.\n" +
        "4. On the welcome screen: Android 7–9 — tap the screen 6 times; Android 10+ — accessibility icon → camera.\n" +
        "5. Point the camera at the QR: the tablet downloads the APK, becomes Device Owner and connects to Wi-Fi.\n" +
        "6. On first launch: choose the launcher (once) and grant \"Display over other apps\".",
    kioskQrSsidLabel = "Wi-Fi network (SSID)",
    kioskQrPasswordLabel = "Wi-Fi password",
    kioskQrShowButton = "Show QR code",
    kioskQrShareButton = "Share",
```

- [ ] **Step 2: Расширить тест строк — упадёт**

В `KioskStringsTest.kt` в обе функции добавить в `kioskValues`:

```kotlin
        s.kioskQrSpoiler, s.kioskQrSteps, s.kioskQrSsidLabel,
        s.kioskQrPasswordLabel, s.kioskQrShowButton, s.kioskQrShareButton
```

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: FAIL — `unresolved reference: kioskQrSpoiler`.

- [ ] **Step 3: FileProvider — `file_paths.xml` и манифест**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="qr_cache" path="." />
</paths>
```

В `AndroidManifest.xml` внутрь `<application>` (после receiver'ов):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Логика сохранения/отправки QR — в `ProvisioningQr.kt`**

Добавить импорты:

```kotlin
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
```

Добавить в `object ProvisioningQr`:

```kotlin
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
```

- [ ] **Step 5: QR-секция в `KioskCard` (`SettingsScreen.kt`)**

Состояния — добавить к тем, что в `SettingsScreen` из Task 8:

```kotlin
    var qrExpanded by remember { mutableStateOf(false) }
    var qrSsid by remember { mutableStateOf("") }
    var qrPassword by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
```

Вызов — расширить вызов `KioskCard` (Task 8) новыми параметрами:

```kotlin
            KioskCard(
                status = kioskStatus,
                expanded = kioskExpanded,
                onToggle = { kioskExpanded = !kioskExpanded },
                adbExpanded = adbExpanded,
                onToggleAdb = { adbExpanded = !adbExpanded },
                qrExpanded = qrExpanded,
                onToggleQr = { qrExpanded = !qrExpanded },
                qrSsid = qrSsid,
                onSsidChange = { qrSsid = it },
                qrPassword = qrPassword,
                onPasswordChange = { qrPassword = it },
                onShowQr = {
                    val payload = ProvisioningQr.buildPayload(
                        KioskConfig.APK_DOWNLOAD_URL,
                        qrSsid.ifBlank { null },
                        qrPassword.ifBlank { null }
                    )
                    qrBitmap = ProvisioningQr.encodeQr(payload)
                },
                qrBitmap = qrBitmap,
                onDismissQr = { qrBitmap = null },
                onShareQr = {
                    val payload = ProvisioningQr.buildPayload(
                        KioskConfig.APK_DOWNLOAD_URL,
                        qrSsid.ifBlank { null },
                        qrPassword.ifBlank { null }
                    )
                    ProvisioningQr.shareQr(context, payload)
                }
            )
```

Расширить сигнатуру `KioskCard` (Task 8) параметрами:

```kotlin
    qrExpanded: Boolean,
    onToggleQr: () -> Unit,
    qrSsid: String,
    onSsidChange: (String) -> Unit,
    qrPassword: String,
    onPasswordChange: (String) -> Unit,
    onShowQr: () -> Unit,
    qrBitmap: android.graphics.Bitmap?,
    onDismissQr: () -> Unit,
    onShareQr: () -> Unit
```

Внутрь `KioskCard`, **после** блока ADB (после закрывающей `}` внутреннего `AnimatedVisibility(adbExpanded)`), добавить QR-секцию:

```kotlin
                    // ▶ Настройка через QR-код
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleQr() },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.kioskQrSpoiler,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(text = if (qrExpanded) "▲" else "▼")
                    }

                    AnimatedVisibility(visible = qrExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = strings.kioskQrSteps,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = qrSsid,
                                onValueChange = onSsidChange,
                                label = { Text(strings.kioskQrSsidLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = qrPassword,
                                onValueChange = onPasswordChange,
                                label = { Text(strings.kioskQrPasswordLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = onShowQr,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(strings.kioskQrShowButton)
                            }
                        }
                    }
```

Диалог с изображением — **вне** `KioskCard`, в теле `SettingsScreen` рядом с вызовом карточки (у `qrBitmap` состояние на уровне экрана):

```kotlin
            qrBitmap?.let { bmp ->
                AlertDialog(
                    onDismissRequest = onDismissQrBitmap,   // = { qrBitmap = null }
                    title = { Text(strings.kioskQrShowButton) },
                    content = {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = strings.kioskQrShowButton,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { shareQrForCurrentPayload() }) {
                            Text(strings.kioskQrShareButton)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { qrBitmap = null }) {
                            Text(strings.btnCancel)
                        }
                    }
                )
            }
```

`shareQrForCurrentPayload()` — локальная лямбда, повторяющая сборку payload (как `onShareQr` из вызова `KioskCard`); `strings` — уже объявленный в функции `LocalAppStrings.current`. Импорты: `androidx.compose.foundation.Image`, `androidx.compose.ui.graphics.asImageBitmap`, `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.TextButton`, `androidx.compose.material3.OutlinedTextField`, `com.respondent.pro.kiosk.ProvisioningQr`.

Примечание: `strings.btnCancel` уже существует в `AppStrings`.

- [ ] **Step 6: Прогнать тест строк — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: PASS (2 теста, все QR-строки непустые в ru и en).

- [ ] **Step 7: Собрать, установить, проверка на устройстве**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
На планшете: Настройки (PIN `0000`) → «ИНФОКИОСК» → «Настройка через QR-код» → ввести тестовые SSID/пароль → «Показать QR-код» → диалог с QR → «Поделиться» → открывается шеринг-шторка (сохранить/отправить).
Expected: QR отображается, шеринг работает, кнопка «Отмена» закрывает диалог.

Дополнительно (сверка payload с юнит-тестом): QR содержит URL `…/releases/latest/download/RESPONDENT.PRO.apk` — визуально распознать камерой телефона и сверить строку.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(kiosk): QR generator UI with share via FileProvider"
```

---

### Task 10: Финальная приёмка — чек-лист spec §12

**Files:**
- Нет новых файлов; при отклонениях — точечные правки в созданных ранее.

**Interfaces:**
- Consumes: всё из Tasks 1–9.
- Produces: подтверждённый чек-лист приёмки + (при необходимости) фиксы.

- [ ] **Step 1: Прогнать все юнит-тесты**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, все тесты PASS (`ProvisioningQrTest`, `KioskPolicyTest`, `WatchdogSchedulerTest`, `KioskStringsTest`).

- [ ] **Step 2: Чек-лист на устройстве (spec §12) — выполнить по пунктам**

| # | Проверка | Действие / команда | Ожидание |
|---|----------|--------------------|----------|
| 1 | Автозапуск после перезагрузки + лаунчер по умолчанию | `adb reboot`; `Start-Sleep 60`; `adb shell dumpsys window \| Select-String mCurrentFocus`; `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN` | Фокус — `com.respondent.pro`; HOME резолвится в `.MainActivity` |
| 2 | Крэш → перезапуск < 2 сек | `adb logcat -c`; `adb shell am crash com.respondent.pro`; `Start-Sleep 4`; `adb shell dumpsys window \| Select-String mCurrentFocus` | Фокус — `com.respondent.pro`; лог `Restarting MainActivity` |
| 3 | Убийство процесса → перезапуск ≤ ~60 сек | Экскурсия в настройки (кнопка «Настройки Android») → `adb shell am kill com.respondent.pro` → `Start-Sleep 75` → фокус | Фокус — `com.respondent.pro`; лог `Restarting MainActivity` |
| 4 | Lock Task: Home/Назад/Недавние не выходят | `adb shell input keyevent KEYCODE_HOME`; `adb shell input keyevent KEYCODE_APP_SWITCH`; после каждого — `dumpsys window \| Select-String mCurrentFocus` | Фокус всегда `com.respondent.pro` |
| 5 | Панель уведомлений недоступна; экран не гаснет | На устройстве: провести сверху вниз; оставить экран 3+ мин | Тень/панель не появляются; экран не гаснет |
| 6 | «Настройки Android» → оверлей → возврат → Lock Task | Шаг 6 Task 6 (ручной) | Возврат по кнопке, Lock Task восстановлен |
| 7 | Без DO → деградация → возврат DO | `adb shell dpm remove-device-owner` → перезайти в настройки → `adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver` → перезайти в настройки | Статус ⚠️, приложение живо, политик нет; после возврата DO — ✅ |
| 8 | Инструкция ADB: команды копируются | Настройки → «ИНФОКИОСК» → раскрыть ADB → нажать команду → вставить в поле заметок | Вставляется текст команды |
| 9 | QR-payload валиден | юнит-тест Task 2 (шаг 1) | PASS |
| 10 | Все строки локализованы ru/en | юнит-тест Task 8/9 (`KioskStringsTest`) | PASS |

- [ ] **Step 3: Проверить отсутствие запретов отладки (spec §5, решение 7)**

```powershell
Select-String -Path app\src\main\java\com\respondent\pro\kiosk\*.kt -Pattern "DISALLOW_DEBUGGING_FEATURES|ADB_ENABLED"
```
Expected: пусто (политик отключения отладки нет).

- [ ] **Step 4: Фиксы отклонений (если найдены)**

Любое исправление — минимальное, в задаче-владельце файла, со своим прогоном тестов и commit:
```bash
git add -A
git commit -m "fix(kiosk): <что исправлено по итогам приёмки>"
```

- [ ] **Step 5: Итоговый отчёт партнёру**

Сообщить: результаты чек-листа (таблица Step 2 — выполнено/не выполнено), выводы P1/P2/P5 (включая, сработал ли фоновый старт на Android 10), и напомнить:
- обновление APK на клиентских планшетах — ручная публикация нового ассета `RESPONDENT.PRO.apk` в GitHub Releases (автообновление — отдельная задача);
- factory reset для реального QR-провижининга выполняется только по явному согласию владельца планшета.
