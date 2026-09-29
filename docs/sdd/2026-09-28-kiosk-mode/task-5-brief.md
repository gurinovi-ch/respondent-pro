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

