# Task 3 Report: KioskManager — политики DO и Lock Task (TDD + устройство)

**Status: DONE_WITH_CONCERNS** (все шаги выполнены и подтверждены; concerns — особенности стенда, код по брифу не менялся)

**Commit:** `ee6719c` — `feat(kiosk): KioskManager with DO policies and lock task (TDD)` (ветка `kiosk-mode`, worktree `C:\projects\feedback-app\.worktrees\kiosk-mode`)

---

## Что реализовано

| Файл | Действие |
|---|---|
| `app/src/test/java/com/respondent/pro/kiosk/KioskPolicyTest.kt` | создан — 5 тестов (verbatim из брифа) |
| `app/src/main/java/com/respondent/pro/kiosk/KioskPolicy.kt` | создан — `PolicyAction` (sealed interface, 7 вариантов), `KioskPolicy.actions()`, `RestartPolicy.shouldRestart()`, `MAX_LOCK_TIME_MS` (verbatim) |
| `app/src/main/java/com/respondent/pro/kiosk/KioskManager.kt` | создан — `@Singleton` Hilt-исполнитель: `isDeviceOwner()`, `status()`, `applyPolicies()`, `onActivityResumed/Paused`, `beginExcursion()`, `shouldRestart()`, флаги `isForeground`/`excursionActive`, `KioskStatus` (verbatim; импорт `dagger.hilt.android.qualifiers.ApplicationContext` по примечанию брифа) |
| `app/src/main/java/com/respondent/pro/MainActivity.kt` | изменён — импорты `WindowManager`/`KioskManager`, поле `@Inject lateinit var kioskManager`, в `onCreate`: `FLAG_KEEP_SCREEN_ON` + `applyPolicies()`, хуки `onResume`/`onPause` |

API ровно тот, что в «Interfaces» брифа (Tasks 5/6/8 могут потреблять). Строк интерфейса не добавлено, БД/DataStore не тронуты, политики `DISALLOW_DEBUGGING_FEATURES`/`ADB_ENABLED` не добавлялись.

---

## TDD Evidence

### RED (Step 1–2)

Команда:
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskPolicyTest*"
```

Вывод (фактический):
```
> Task :app:kspDebugUnitTestKotlin
e: .../KioskPolicyTest.kt:13:20 Unresolved reference 'KioskPolicy'.
e: .../KioskPolicyTest.kt:18:23 Unresolved reference 'KioskPolicy'.
e: .../KioskPolicyTest.kt:19:37 Unresolved reference 'PolicyAction'.
e: .../KioskPolicyTest.kt:24:37 Unresolved reference 'PolicyAction'.
e: .../KioskPolicyTest.kt:30:20 Unresolved reference 'RestartPolicy'.
e: .../KioskPolicyTest.kt:35:21 Unresolved reference 'RestartPolicy'.
e: .../KioskPolicyTest.kt:41:21 Unresolved reference 'RestartPolicy'.
> Task :app:compileDebugUnitTestKotlin FAILED
FAILURE: Build failed with an exception.
BUILD FAILED in 4s
```

Почему ожидаемо: тесты ссылаются на `KioskPolicy`/`PolicyAction`/`RestartPolicy`, которых ещё нет в исходниках — точный провал, предсказанный брифом (`unresolved reference: KioskPolicy`). Падение на компиляции, а не на assertions, — корректный первый RED для чистых решалок.

### GREEN (Step 4)

После создания `KioskPolicy.kt` — та же команда:
```
> Task :app:compileDebugKotlin
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 8s
```

Факт из test-results XML (после зелёного прогона):
```xml
<testsuite name="com.respondent.pro.kiosk.KioskPolicyTest" tests="5" skipped="0" failures="0" errors="0" ...>
```
**5 тестов, 0 падений** — как ожидалось (Step 4).

Полный набор перед коммитом (`.\gradlew.bat testDebugUnitTest`): `BUILD SUCCESSFUL`, агрегат по XML: **suites=2, tests=10, failures=0** (KioskPolicyTest 5 + ProvisioningQrTest 5).

---

## Устройственная проверка (Step 7)

### Сборка и установка

```
$ .\gradlew.bat assembleDebug
BUILD SUCCESSFUL in 13s

$ adb install -r app\build\outputs\apk\debug\app-debug.apk
Performing Streamed Install
Success

$ adb shell am start -n com.respondent.pro/.MainActivity
Starting: Intent { cmp=com.respondent.pro/.MainActivity }
```
`dumpsys package com.respondent.pro` → `lastUpdateTime=2026-09-28 11:44:55` (установлен именно свежий APK).

### Проверка 1: Home не выводит из приложения

```
$ adb shell input keyevent KEYCODE_HOME
$ adb shell dumpsys window | Select-String "mCurrentFocus"
  mCurrentFocus=Window{bafa7f1 u0 com.respondent.pro/com.respondent.pro.MainActivity}
```
Повторно в конце проверки (после всех манипуляций):
```
  mCurrentFocus=Window{ea424f u0 com.respondent.pro/com.respondent.pro.MainActivity}
```
**Ожидание брифа выполнено:** фокус остался на `com.respondent.pro` — Lock Task активен, Home не выводит.

### Проверка 2: whitelist Lock Task

Команда брифа:
```
$ adb shell dumpsys device_policy | Select-String "Lock task"
(пусто — строк нет)
```
На этом стенде (Android 10, OEM-сборка) `dumpsys device_policy` печатает всего 66 строк и **не содержит секции lock task вообще** (греп по `task|whitelist|permitted` — 0 совпадений). Это не «политика отклонена»: полноценный `dumpsys device_policy` при этом показывает применённые нашей политикой данные:

```
Device Owner:
  admin=ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}
  package=com.respondent.pro
      userRestrictions:
        no_add_managed_profile          <- дефолт из defaultEnabledRestrictionsAlreadySet={no_add_managed_profile}
        no_factory_reset                <- наш BlockFactoryReset
        no_safe_boot                    <- наш BlockSafeBoot
      maximumTimeToUnlock=4611686018427387903   <- = Long.MAX_VALUE/2 = KioskPolicy.MAX_LOCK_TIME_MS
```

Авторитетный источник whitelist для этой ОС — `dumpsys activity activities`:
```
$ adb shell dumpsys activity activities | Select-String "mLockTaskModeState|mLockTaskPackages|mLockTaskAuth"
  rootWasReset=... mLockTaskAuth=LOCK_TASK_AUTH_WHITELISTED      <- наша task (t63, com.respondent.pro/.MainActivity)
  rootWasReset=... mLockTaskAuth=LOCK_TASK_AUTH_PINNABLE         <- лаунчер (контраст: whitelisted именно мы)
  LockTaskController
    mLockTaskModeState=LOCKED
    mLockTaskPackages (userId:packages)=
      u0:[com.respondent.pro]
```
**Whitelist подтверждён:** `u0:[com.respondent.pro]`, режим `LOCKED`.

### Атрибуция (что именно наш код)

- `grep` по всему `app/src`: `setLockTaskPackages` / `addUserRestriction` / `setMaximumTimeToLock` встречаются **только** в `KioskManager.kt`.
- Проба P1 (Task 1) только выдавала DO и смотрела `dumpsys` — она не ставила restrictions/whitelist (подтверждено reading `task-1-report.md`: `dpm set-device-owner` + просмотр политик `device_admin.xml`).
- `defaultEnabledRestrictionsAlreadySet={no_add_managed_profile}` в дампе доказывает, что `no_factory_reset`/`no_safe_boot` добавлены в рантайме (нашим `applyPolicies`), а не являются дефолтами.
- Loop выполняется в обе стороны от `WhitelistLockTask` (последний экшен) — если бы `setLockTaskPackages` упал с `SecurityException`, whitelist не был бы установлен.

### Диагностика логов KioskManager (почему нет строки «Policies applied: 7»)

Лог-строки не поймать на этом стенде — три независимые причины, все проверены экспериментально:
1. **Устройство подавляет Debug-уровень logcat.** Доказано контрольным входом: `adb shell log -p d -t KioskManager "debug-level-test"` — не появился; `adb shell log -p i -t KioskManager "info-level-test"` — появился (`I KioskManager: info-level-test`). Логи `applyPolicies` в коде — `Log.d`, поэтому невидимы.
2. **Буфер 256 KiB** закольцован спамом SurfaceFlinger (окно ~300 строк на несколько секунд) — стартовые строки к моменту проверки уже вытеснены.
3. Перехват через перезапуск процесса не удался (см. concern №2), а пересоздание Activity (`font_scale` 1.0→1.15→1.0 — `configChanges` его не перехватывает) повторно отдало `onCreate`, но Debug-строки всё равно фильтруются.

Положительный вывод из той же диагностики: **Error-уровень работает** (в буфере видны `E GraphicExt`), а строк `KioskManager` уровня E (`applyPolicies failed`, `startLockTask failed`) **не возникло ни разу** за два прогона `applyPolicies` (11:52:38 и 11:52:42) — значит `SecurityException` не было.

### Побочные эффекты на стенде (восстановлены/нейтральны)

- `settings put system font_scale 1.15` → **возвращён `1.0`** (проверено `settings get system font_scale` → `1.0`).
- `cmd uimode night yes` — не применился (`Night mode: no`), ничего не изменил.
- Буфер logcat очищен (косметика).
- DO **не снимался**, `remove-device-owner` не выполнялся. Приложение намеренно оставлено в Lock Task — это целевое состояние.

---

## Self-review

- **Completeness:** Steps 1–8 выполнены. API `KioskManager`/`KioskStatus`/`KioskPolicy`/`RestartPolicy`/`PolicyAction` — ровно по «Interfaces» брифа, без отклонений. `setLockTaskFeatures` — под `SDK_INT >= 28` (стенд Android 10 = API 29 → ветка выполняется).
- **Quality:** код решалок чистый, без Android-зависимостей (тесты гоняются на JVM); `KioskManager` тонкий, вся логика решений — в `KioskPolicy`/`RestartPolicy` (`@Volatile` на флагах — корректно для главного потока + watchdog).
- **YAGNI:** ничего сверх брифа не добавлено; изменён ровно 1 существующий файл (`MainActivity`, +19 строк) и создано 3 новых.
- **Tests real:** RED — реальная ошибка компиляции с точными ссылками; GREEN — 5/5 по XML; полный набор 10/10; тесты проверяют поведение (список политик, 3 ветки рестарта), не реализацию.
- **Pristine output:** рабочее дерево чистое после коммита (`git status --short` пуст), изменённые файлы совпадают со списком Step 8.

## Concerns (для контроллера)

1. **`dumpsys device_policy | Select-String "Lock task"` на этом стенде всегда пуст** — ОС не печатает секцию lock task в device_policy-дампе (проверено на полном выводе, 66 строк). Ожидание брифа (Step 7) на данной прошивке невыполнимо буквально; эквивалентное подтверждение получено через `dumpsys activity activities` (`mLockTaskPackages u0:[com.respondent.pro]`, `mLockTaskModeState=LOCKED`). Код и политики **не менял** (по инструкции — не «чинить» на свой вкус).
2. **`adb shell am force-stop com.respondent.pro` НЕ убивает процесс** (pid 8267 одинаков до/через 3 с после; то же с `am start -S`, который штатно force-stops первым) — аварийная команда из брифа на этом планшете не работает (гипотеза: блокировка в режиме Lock Task/DO или OEM-фича; причина не установлена). Если планшет «залипнет», у контроллера остаётся тяжёлый рычаг `dpm remove-device-owner` (НЕ применялся — деструктивен, необходимости не было).
3. **`Log.d` в logcat этого устройства не выводится** (доказано контрольным `log -p d`) — оператор/скрипты не увидят «Policies applied: N». Если для последующих задач нужен лог-трейс, стоит писать в `Log.i` (но по брифу код verbatim — не менял).
4. Погрешность атрибуции: строка `maximumTimeToUnlock=4611686018427387903` численно равна `MAX_LOCK_TIME_MS`, но я не могу на 100% исключить, что это дефолтный сентинел AOSP в этой секции дампа (секция рядом с password-полями). Косвенно факт покрыт: `setMaximumTimeToLock` вызывается в том же цикле, что и доказанные `no_factory_reset`/`no_safe_boot`/whitelist, и исключений не было.
