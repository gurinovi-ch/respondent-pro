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

