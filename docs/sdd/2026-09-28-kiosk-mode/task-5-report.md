# Task 5 Report: Перезапуск после сбоя — CrashHandler + Watchdog (TDD решалки)

**Status: DONE**
**Commit: `e3b2a8e` — feat(kiosk): crash restart + watchdog alarm chain (TDD)** (ветка `kiosk-mode`)

---

## 1. What was implemented

| File | Change |
|---|---|
| `app/src/main/java/com/respondent/pro/kiosk/WatchdogScheduler.kt` | NEW — `object` с `AlarmMode{EXACT,INEXACT}`, `PERIOD_MS=60_000`, `CRASH_DELAY_MS=1_000`, решалкой `decide(sdkInt, canScheduleExact)`, `scheduleNext()` (периодическая цепочка) и `scheduleRestart(delayMs)` (разовый перезапуск после крэша) |
| `app/src/main/java/com/respondent/pro/kiosk/RestartReceiver.kt` | NEW — manifest-receiver на `ACTION_WATCHDOG`/`ACTION_RESTART`; Hilt entry point `KioskEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class)`) в том же файле; `maybeRestart()` → `KioskManager.shouldRestart()` (T3, не дублируется) → `startActivity(MainActivity)` |
| `app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt` | NEW — 3 unit-теста решалки |
| `app/src/main/AndroidManifest.xml` | Регистрация `.kiosk.RestartReceiver` (exported=false) после `BootReceiver` |
| `app/src/main/java/com/respondent/pro/RespondentApp.kt` | В crash-handler лямбде: после записи лог-файла, перед `defaultHandler?.uncaughtException(...)` → `WatchdogScheduler.scheduleRestart(context)` в try/catch |
| `app/src/main/java/com/respondent/pro/MainActivity.kt` | В `onCreate` после `kioskManager.applyPolicies()` → `WatchdogScheduler.scheduleNext(this)` |

Потреблено из T3 без изменений: `KioskManager.shouldRestart()/isForeground/excursionActive`, `RestartPolicy`. Дублирования нет — receiver вызывает `kiosk.shouldRestart()`.

### R8-замены (обязательные правки к брифу)

| Место | Бриф | Сделано |
|---|---|---|
| `WatchdogScheduler.scheduleNext` | `Log.d("Watchdog armed (...)")` | **`Log.i`** |
| `WatchdogScheduler.scheduleRestart` | `Log.d("Restart alarm in ...ms")` | **`Log.i`** |
| `RestartReceiver.maybeRestart` (skip) | `Log.d("Skip restart (...)")` | **`Log.i`** |
| `RestartReceiver.maybeRestart` (go) | `Log.d("Restarting MainActivity")` | **`Log.i`** |

Строки `Log.e` — без изменений. Короткий `Log.d` в `mode()`-логировании: в брифе его **не было** — не добавлял (и не нужен по правилу R8/амендмента 5). В новых файлах живых вызовов `Log.d` не осталось (проверено grep'ом — только упоминания в R8-комментариях).

Сохранён short-circuit guard (глобальное ограничение): `Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()` — `canScheduleExactAlarms()` не вызывается на API < 31 (Android 7-10).

Прочие правки амендментов: `am force-stop`/`am start -S`/`am kill` **не использовались** (P2 → Task 6); лог-харнесс вместо `-s`-фильтра; только `am crash`.

---

## 2. TDD Evidence

### RED (Step 2) — фактический вывод

```
$ $env:JAVA_HOME="...jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*WatchdogSchedulerTest*"
e: ...WatchdogSchedulerTest.kt:10:22 Unresolved reference 'WatchdogScheduler'.
e: ...WatchdogSchedulerTest.kt:11:22 Unresolved reference 'WatchdogScheduler'.
e: ...WatchdogSchedulerTest.kt:16:22 Unresolved reference 'WatchdogScheduler'.
e: ...WatchdogSchedulerTest.kt:17:22 Unresolved reference 'WatchdogScheduler'.
e: ...WatchdogSchedulerTest.kt:23:22 Unresolved reference 'WatchdogScheduler'.
e: ...WatchdogSchedulerTest.kt:24:22 Unresolved reference 'WatchdogScheduler'.
> Task :app:compileDebugUnitTestKotlin FAILED
BUILD FAILED in 3s
```

Ожидаемый RED получен (`unresolved reference: WatchdogScheduler`).

### GREEN (Step 4) — фактический вывод

```
$ .\gradlew.bat testDebugUnitTest --tests "*WatchdogSchedulerTest*"
BUILD SUCCESSFUL in 14s
```

XML-отчёт: `<testsuite name="com.respondent.pro.kiosk.WatchdogSchedulerTest" tests="3" skipped="0" failures="0" errors="0" time="0.009">`

*Процессное отклонение:* Steps 5-6 (`RestartReceiver` + манифест) выполнены **до** запуска GREEN, потому что `pendingIntent()` в `WatchdogScheduler` ссылается на `RestartReceiver::class.java` — main-исходники не компилировались бы без него. На результат теста (чистая решалка `decide()`) это не влияло: RED зафиксирован до любой реализации, GREEN — после.

---

## 3. Device probe: `am crash` (Step 9)

Сборка `assembleDebug` → `install -r` → **Success**. Протокол (R8-харнесс): `logcat -c` → `am start` → 3 с → `am crash` → 6 с → `dumpsys window` → `logcat -d | Select-String "WatchdogScheduler|RestartReceiver|RespondentApp|BootReceiver"`.

### Фактические логи

```
09-28 12:19:40.841  5547 E RespondentApp: === CRASH 2026-09-28 12:19:40.817 ===
09-28 12:19:40.841  5547 E RespondentApp: Exception: android.app.RemoteServiceException
09-28 12:19:40.841  5547 E RespondentApp: Message: shell-induced crash
09-28 12:19:40.853  5547 I WatchdogScheduler: Restart alarm in 1000ms
09-28 12:19:45.878   894 I ActivityManager: Start proc 5864:com.respondent.pro/u0a157
                      for broadcast {com.respondent.pro/com.respondent.pro.kiosk.RestartReceiver}
09-28 12:19:47.988  5864 I RestartReceiver: Restarting MainActivity
09-28 12:19:48.347  5864 I WatchdogScheduler: Watchdog armed (EXACT)
```

Все verification-строки видны ⇒ замена `Log.i` (R8) работает.

### Тайминги (сколько занял перезапуск)

| Момент | Время | Δ от крэша |
|---|---|---|
| Крэш (запись в crash_log) | 12:19:40.817 | 0 |
| `Restart alarm in 1000ms` | 12:19:40.853 | +36 мс |
| Плановое срабатывание alarm'а (по расчёту) | ~12:19:41.853 | +1.0 с |
| Фактическая доставка broadcast (старт процесса) | 12:19:45.878 | **+5.1 с** |
| `Restarting MainActivity` | 12:19:47.988 | **+7.2 с** |
| `Watchdog armed (EXACT)` (цепочка перепланирована из onCreate) | 12:19:48.347 | +7.5 с |

**Итог: перезапуск после крэша произошёл, полный цикл ≈ 7.2 с** (из них ~4 с — задержка доставки exact alarm'а + ~2 с — подъём процесса/Hilt).

### Фокус (сравнение, не визуал — как велено)

- Проба сразу после сна (12:19:49.450): `mCurrentFocus=null` — транзиент в момент подъёма активности/крэш-диалога.
- Повторная проба (~12:19:5x) и контрольная в 12:20:13.861:
  `mCurrentFocus=Window{93836d2 u0 com.respondent.pro/com.respondent.pro.MainActivity}` — **приложение на экране, фокус стабилен**.

### Crash-лог

```
$ adb shell run-as com.respondent.pro cat files/crash_log.txt
=== CRASH 2026-09-28 12:19:40.817 ===
Message: shell-induced crash
```
Запись нового крэша в `files/crash_log.txt` присутствует (плюс старая запись от 2026-09-21 — не трогал).

P2 (`am kill`) **не выполнялся** — перенесён в Task 6 (амендмент 4). `force-stop`/`start -S` не использовались (амендмент 2).

---

## 4. Test results

Полный прогон перед коммитом:

```
$ .\gradlew.bat testDebugUnitTest
BUILD SUCCESSFUL in 3s
```

| Suite | tests | failures | errors |
|---|---|---|---|
| `KioskPolicyTest` | 5 | 0 | 0 |
| `ProvisioningQrTest` | 5 | 0 | 0 |
| `WatchdogSchedulerTest` | 3 | 0 | 0 |
| **TOTAL** | **13/13** | **0** | **0** |

Сборка `assembleDebug` — SUCCESS.

---

## 5. Files changed

```
app/src/main/java/com/respondent/pro/kiosk/WatchdogScheduler.kt   (new)
app/src/main/java/com/respondent/pro/kiosk/RestartReceiver.kt     (new, incl. KioskEntryPoint)
app/src/test/java/com/respondent/pro/kiosk/WatchdogSchedulerTest.kt (new)
app/src/main/AndroidManifest.xml                                  (receiver registration)
app/src/main/java/com/respondent/pro/RespondentApp.kt             (scheduleRestart in crash handler)
app/src/main/java/com/respondent/pro/MainActivity.kt              (scheduleNext in onCreate)
```

Один коммит: `e3b2a8e feat(kiosk): crash restart + watchdog alarm chain (TDD)` — 6 files changed, 189 insertions(+). Рабочее дерево чистое.

Не тронуто (по ограничениям): строки интерфейса, БД/DataStore, политики отладки, KioskManager/RestartPolicy (T3).

---

## 6. Self-review

- ✅ RED→GREEN по решалке: выводы выше, фактические.
- ✅ R8: все 4 verification-строки через `Log.i`; grep подтверждает отсутствие живого `Log.d` в новых файлах.
- ✅ Short-circuit guard `SDK_INT < 31 || canScheduleExactAlarms()` сохранён (единственное место вызова API 31+ в не-guard контексте отсутствует).
- ✅ Единый источник правды: receiver использует `KioskManager.shouldRestart()` / `RestartPolicy` — без дублирования логики.
- ✅ Цепочка не рвётся: `ACTION_WATCHDOG` → всегда `scheduleNext()` перед проверкой; плюс `MainActivity.onCreate` перепланирует при каждом старте (подтверждено логом `Watchdog armed (EXACT)` после рестарта).
- ✅ Худший путь (`startActivity` blocked) обёрнут в try/catch с `Log.e` — цепочка переживает отказ (Review Focus №1).
- ✅ Фоновый старт после крэша сработал на устройстве (Device Owner + HOME-лаунчер + Lock Task).
- ✅ Ограничения соблюдены: minSdk/targetSdk не менялись, P2 не выполнялся, один коммит.

## 7. Concerns

1. **Задержка доставки exact alarm'а ~4 с** (срабатывание планировалось на +1.0 с, broadcast пришёл на +5.1 с). Причина не установлена (кандидаты: состояние doze/idle, доставка в умирающий процесс). На функциональности не сказывается — перезапуск произошёл; в худшем случае «окно» восстановления ~7 с вместо ~3 с. Наблюдать в Task 6 при пробе `am kill`.
2. **Транзиент `mCurrentFocus=null`** в первой пробе сразу после рестарта (12:19:49). Стабильный фокус `MainActivity` подтверждён повторными пробами. Если ужатые SLA требуют фокуса мгновенно — стоит проверить отдельно.
3. Процессное: Steps 5-6 выполнены до GREEN-запуска (компиляционная зависимость `WatchdogScheduler` → `RestartReceiver::class.java`) — см. §2.
