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

