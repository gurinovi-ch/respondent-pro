# Task 4 Report: HOME-лаунчер и автозапуск после загрузки

**Status: DONE_WITH_CONCERNS**
Worktree: `C:\projects\feedback-app\.worktrees\kiosk-mode` (ветка `kiosk-mode`)
Device: Lenovo TB-X606X (HVA4H2PY), Android 10, Device Owner выдан (Task 1), Lock Task активен (Task 3).

---

## 1. Что реализовано

### 1.1 Создан `app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt`

Код из брифа (Step 1) дословно, **с одной правкой R8**: строка лога — `Log.i(...)`
вместо `Log.d(...)` (прошивка подавляет `Log.d`, приёмка читает `adb logcat -d`).

```kotlin
package com.respondent.pro.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.respondent.pro.MainActivity

/** Автозапуск после загрузки системы (spec §3). БД/настройки не трогает. */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "BOOT_COMPLETED — launching MainActivity")   // R8: Log.i, не Log.d
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

Интент — payload-free (R3): без `action`, без extras; `MainActivity` осталась `singleTask`
без `onNewIntent` — ничего в поведении singleTask не менялось.

### 1.2 Изменён `app/src/main/AndroidManifest.xml`

- В существующий `intent-filter` `MainActivity`, после `LAUNCHER`, добавлены категории (Step 2):

```xml
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
```

- После `KioskAdminReceiver`, до `<activity>` (Step 2):

```xml
        <receiver
            android:name=".kiosk.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

`RECEIVE_BOOT_COMPLETED` уже был в манифесте (Task 1) — дополнительные разрешения не добавлялись.

### 1.3 R8-правка `app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt` (одобрена контроллером)

Ровно две verification-строки переведены с `Log.d` на `Log.i`:

- `Log.d(TAG, "Device Owner not granted — policies skipped")` → `Log.i(...)`
- `Log.d(TAG, "Policies applied: ${actions.size}")` → `Log.i(...)`

Остальные `Log.d` в `KioskManager` не трогались (проверено `git diff` — 2 строки +/2 строки −).
Правки включены в тот же коммит (см. §5).

Ничего из UI-строк, БД, DataStore не менялось. SDK-гард не потребовалось
(`FLAG_ACTIVITY_CLEAR_TASK` — API 11, `BOOT_COMPLETED` — API 1; minSdk 24).

---

## 2. Шаг 3: сборка и установка

```
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
→ BUILD SUCCESSFUL in 16s   (40 actionable tasks: 12 executed, 28 up-to-date)

C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
→ Performing Streamed Install
  Success
```

Установка прошла и при активном Lock Task, и при `setUninstallBlocked` (Device Owner) — `Success`.

---

## 3. Пробы на устройстве (фактические выводы)

### 3.1 P4 — HOME по умолчанию (Step 4)

```
> adb shell cmd package set-home-activity com.respondent.pro/.MainActivity
Success
> adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.respondent.pro/.MainActivity
```

Ожидание выполнено: резолвится `com.respondent.pro/.MainActivity`, а не `com.tblenovo.launcher/...`.
Команда на этом Android 10 поддержана (fallback на системный диалог не понадобился).

### 3.2 Перезагрузка №1 (Step 5)

```
> adb reboot ; Start-Sleep -Seconds 60 ; adb shell dumpsys window | Select-String "mCurrentFocus"
waited 63.6804622s after reboot
  mCurrentFocus=Window{e0a2b31 u0 com.respondent.pro/com.respondent.pro.MainActivity}
```

**Фокус — на `com.respondent.pro/.MainActivity`** ✅ (приложение поднялось само, захватило экран).

Логи (полный дамп, `Select-String`):

```
09-28 12:06:20.155  2750  2750 I BootReceiver: BOOT_COMPLETED — launching MainActivity
09-28 12:06:20.539  2750  2750 I KioskManager: Policies applied: 7
```

Строка `BOOT_COMPLETED — launching MainActivity` и `Policies applied: 7` присутствуют
(R8-перевод на `Log.i` подтвердил себя — `Log.d` на этом устройстве не виден).

⚠️ **Аномалия P4 после перезагрузки №1** — резолв HOME стал:

```
> adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false
android/com.android.internal.app.ResolverActivity
```

т.е. предпочтение `set-home-activity` после первой перезагрузки не сохранилось
(2 кандидата HOME → ResolverActivity). Повторная команда снова дала `Success` и
`com.respondent.pro/.MainActivity`.

### 3.3 Перезагрузка №2 (проверка воспроизводимости)

```
> adb reboot ; Start-Sleep -Seconds 65 ; adb wait-for-device
reboot#2 elapsed 68.7s
--- focus ---
  mCurrentFocus=Window{3efea85 u0 com.respondent.pro/com.respondent.pro.MainActivity}
--- home resolve ---
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.respondent.pro/.MainActivity
--- logs ---
09-28 12:09:46.662  2045  2045 I BootReceiver: BOOT_COMPLETED — launching MainActivity
09-28 12:09:46.894  2045  2045 I KioskManager: Policies applied: 7
```

**Фокус — на `com.respondent.pro/.MainActivity`** ✅, BootReceiver сработал ✅,
предпочтение HOME **сохранилось** ✅.

Итог по перезагрузкам: автозапуск и захват экрана воспроизводимы (2/2),
заняло ~64s и ~69s от команды `reboot` до читаемого `dumpsys` (порог 60s не превышен).

**Гипотеза** по аномалии №1 (не доказана, помечена как гипотеза): `set-home-activity`
был выполнен сразу после `adb install -r`, и асинхронная запись состояния PMS
при обновлении пакета перезаписала `packages.xml` уже без preference;
при перезагрузке №2 установки перед пробой не было — preference сохранился.

### 3.4 Финальное состояние устройства (перед отчётом, 12:11:22 +03)

```
mCurrentFocus=Window{cca4e03 u0 com.respondent.pro/com.respondent.pro.MainActivity}
resolve-activity HOME → com.respondent.pro/.MainActivity (match=0x108000, isDefault=true)
```

### 3.5 Замечание по команде приёмки логов

`adb logcat -d -s "BootReceiver:*" "KioskManager:*"` отработал **не с первого раза**:
сразу после перезагрузки он вернул только заголовки `--------- beginning of system/main`
без строк, тогда как полный `adb logcat -d | Select-String "BootReceiver|KioskManager"`
показывал обе строки. Рекомендация для приёмки (Task 10): если `-s`-фильтр пуст
сразу после загрузки — повторить через несколько секунд или взять полный дамп.

---

## 4. Тесты (регресс, перед коммитом)

```
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest
→ BUILD SUCCESSFUL in 5s   (30 actionable tasks: 6 executed, 24 up-to-date)
```

Разбор `app/build/test-results/testDebugUnitTest/*.xml`:

```
com.respondent.pro.kiosk.KioskPolicyTest      tests=5 failures=0 errors=0 skipped=0
com.respondent.pro.kiosk.ProvisioningQrTest   tests=5 failures=0 errors=0 skipped=0
```

**10/10 passed, 0 failures, 0 errors, 0 skipped** ✅

---

## 5. Коммит (Step 6)

```
6e673df feat(kiosk): HOME launcher role + boot autostart
        3 files changed, 39 insertions(+), 2 deletions(-)

 app/src/main/AndroidManifest.xml                   |  9 +++++++
 app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt | 28 ++++++++++++++++++++++
 app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt |  4 ++--
```

Тело коммита зафиксировало R8-правку:

> BootReceiver launches MainActivity on BOOT_COMPLETED (payload-free intent, Log.i per R8).
> MainActivity gains HOME+DEFAULT categories. KioskManager verification lines moved
> Log.d → Log.i so acceptance can read them via adb logcat (R8, controller-approved).

Рабочее дерево после коммита чистое (`git status --short` пуст).

---

## 6. Изменённые файлы

| File | Change |
|---|---|
| `app/src/main/java/com/respondent/pro/kiosk/BootReceiver.kt` | создан (28 строк, `Log.i` по R8) |
| `app/src/main/AndroidManifest.xml` | +`HOME`/`DEFAULT` категории у MainActivity; +receiver `BootReceiver` |
| `app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt` | R8: 2 строки `Log.d` → `Log.i` |

---

## 7. Self-review

- ✅ Код `BootReceiver` — из брифа, кроме одобренной замены `Log.d`→`Log.i`.
- ✅ Интент payload-free: без action, без extras (R3); `MainActivity` — по-прежнему `singleTask`,
  `onNewIntent` не добавлялся.
- ✅ Категории `HOME`+`DEFAULT` добавлены **внутрь** существующего `intent-filter` после `LAUNCHER`
  (LAUNCHER сохранён — иконка на рабочем столе не пропала).
- ✅ Receiver объявлен сразу после `KioskAdminReceiver`, `android:exported="true"`,
  action `BOOT_COMPLETED`; разрешение `RECEIVE_BOOT_COMPLETED` уже было.
- ✅ R8: в `KioskManager` переведены ровно 2 строки (`Device Owner not granted — policies skipped`,
  `Policies applied: ${actions.size}`); остальные `Log.d` не тронуты (`git diff` подтверждает 2/2).
- ✅ Сборка `assembleDebug` — BUILD SUCCESSFUL; `testDebugUnitTest` — 10/10.
- ✅ `am force-stop` / `am start -S` не использовались (R8b) — пробы шли только через перезагрузку.
- ✅ P4 выполнен ровно командой из брифа; `dumpsys window | Select-String mCurrentFocus`
  вместо `dumpsys device_policy`-секции Lock task (R4).
- ✅ Один коммит, включая R8-правку KioskManager.
- ⚠️ Аномалия: см. Concern 1 ниже.

---

## 8. Concerns

1. **Потеря preference `set-home-activity` после перезагрузки №1** (ResolverActivity вместо
   `com.respondent.pro/.MainActivity`), после повторного выполнения команды — перезагрузка №2
   показала сохранение. Для приёмки Task 10: **проверять `resolve-activity` после финальной
   установки и после перезагрузки**, при потере — повторить `set-home-activity`.
   На работу киоска не влияет: HOME-нажатие в Lock Task блокируется, а автозапуск идёт
   через `BootReceiver` (доказано 2/2 перезагрузками).
2. **Фильтр `logcat -s` ненадёжен сразу после загрузки** — см. §3.5; приёмке иметь запасной
   путь (полный `logcat -d` + `Select-String`).
3. Обе строки логов подтверждены на устройстве только на уровне `Log.i` (R8) —
   `Log.d` на этой прошивке не читается, поэтому остальные `Log.d` в `KioskManager`
   приёмкой на устройстве увидеть нельзя (их статус не менялся).
