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

