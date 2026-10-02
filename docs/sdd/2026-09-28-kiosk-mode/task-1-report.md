# Task 1 Report: Фундамент — KioskAdminReceiver, манифест, проба P1

**Status:** DONE
**Commit:** `e6f1780` — `feat(kiosk): DeviceAdminReceiver, permissions, singleTask; probe P1` (ветка `kiosk-mode`, worktree)

## What was implemented

Ровно три файла по брифу, без отклонений:

1. **Created** `app/src/main/java/com/respondent/pro/kiosk/KioskAdminReceiver.kt`
   — пустой `DeviceAdminReceiver` без логики (текст из брифа, Step 1, verbatim).
2. **Created** `app/src/main/res/xml/device_admin.xml`
   — 7 политик: `limit-password`, `watch-login`, `reset-password`, `force-lock`, `wipe-data`, `expire-password`, `encrypted-storage` (Step 2, verbatim).
3. **Modified** `app/src/main/AndroidManifest.xml` (Step 3):
   - три разрешения после существующих: `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`;
   - `<receiver android:name=".kiosk.KioskAdminReceiver" exported=true permission=BIND_DEVICE_ADMIN>` с `meta-data → @xml/device_admin` и intent-filter `DEVICE_ADMIN_ENABLED`, placed **перед `<activity>`**;
   - `android:launchMode="singleTask"` у существующего `<activity android:name=".MainActivity">`.

Идентификатор компонента: `com.respondent.pro/.kiosk.KioskAdminReceiver` — совпадает с требованием.

Не тронуто (per constraints): `AppStrings.kt` (строк интерфейса в задаче нет), Room/DataStore, никаких политик `DISALLOW_DEBUGGING_FEATURES` / `ADB_ENABLED=0`.

## Verification

### Step 4: сборка и установка

PowerShell, worktree `C:\projects\feedback-app\.worktrees\kiosk-mode`:

```
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
→ BUILD SUCCESSFUL in 11s
  40 actionable tasks: 18 executed, 22 up-to-date

C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
→ Performing Streamed Install
  Success
```

(Примечание: бриф в Step 4 указывал запуск из `C:\projects\feedback-app`, но глобальное ограничение задачи требует работать только в worktree — собрано и установлено из worktree.)

### Step 5: проба P1 — выдача Device Owner

```
C:\platform-tools\adb.exe shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver
→ Success: Device owner set to package ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}
  Active admin set to component {com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}
```

Проверка `dumpsys device_policy`:

```
C:\platform-tools\adb.exe shell dumpsys device_policy
→ Current Device Policy Manager state:
    Device Owner:
      admin=ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}
      name=
      package=com.respondent.pro
      canAccessDeviceIds=true
      User ID: 0

  Enabled Device Admins (User 0, provisioningState: 3):
    com.respondent.pro/.kiosk.KioskAdminReceiver:
      policies: wipe-data, reset-password, limit-password, watch-login,
                force-lock, expire-password, encrypted-storage
```

**P1: PASSED.** Строка device owner присутствует с точным `ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}`; все 7 политик из `device_admin.xml` видны в выводе. Небольшое наблюдение: на этом устройстве (Android 10) заголовок строки — `Device Owner:` (не literally `mDeviceOwnerComponent:`), но содержимое полностью соответствует ожидаемому (ожидание брифа — «строка с device owner присутствует»).

Device Owner **не удалялся** — оставлен для последующих задач (Task 3+).

## Files changed

| File | Change |
|---|---|
| `app/src/main/java/com/respondent/pro/kiosk/KioskAdminReceiver.kt` | created (9 lines) |
| `app/src/main/res/xml/device_admin.xml` | created (12 lines) |
| `app/src/main/AndroidManifest.xml` | +15 lines (3 permissions, receiver block, `launchMode`) |

Итого коммит: `3 files changed, 36 insertions(+)`, ничего лишнего.

## Self-review findings

- **Completeness:** все 6 шагов брифа выполнены; файлы и атрибуты дословно по брифу.
- **Quality:** имена/структура из брифа verbatim; код чистый, без лишнего.
- **Discipline:** ничего за пределами брифа (YAGNI); запрещённых политик нет; строк UI не добавлено.
- **Verification:** выводы сборки, установки, `dpm set-device-owner` и `dumpsys device_policy` зафиксированы выше.
- Замечания к фиксации: предупреждения git про LF→CRLF при добавлении двух новых файлов — штатное поведение репо, на содержимое не влияет.

## Issues / concerns

Нет. Отклонений от плана и блокеров не было.
