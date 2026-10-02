# Task 6 Report: Оверлей-кнопка возврата из настроек Android + пробы P2/P5/excursion

**Status:** DONE_WITH_CONCERNS
**Commit:** `2f4dba7` — feat(kiosk): settings excursion with floating return button; probes P2/P5
**Worktree:** `C:\projects\feedback-app\.worktrees\kiosk-mode` (ветка `kiosk-mode`)
**Устройство:** HVA4H2PY (Lenovo-планшет, 1200x1920), все времена ниже — часы устройства (logcat), смещение к ПК ≈ −2 с.

---

## 1. Что реализовано (с учётом всех amendments)

### 1.1 Создан `app/src/main/java/com/respondent/pro/kiosk/SettingsExcursionOverlay.kt`
Код из брифа Step 1 дословно, с правками по **R8** (прошивка подавляет `Log.d`):
- `show()`: `Log.d("Overlay permission not granted…")` → `Log.i(...)`; `Log.d("Overlay shown")` → `Log.i("Overlay shown")`;
- `hide()`: добавлена verification-строка `Log.i(TAG, "Overlay hidden")` (R8 явно называет и show, и hide);
- `Log.e` не трогал (openPermissionScreen/show/hide failure-обработчики — без изменений).

API: `Settings.canDrawOverlays` (API 23+, minSdk 24 — guard не нужен), `TYPE_APPLICATION_OVERLAY` (SDK ≥ 26) / `TYPE_SYSTEM_ALERT`, `ACTION_MANAGE_OVERLAY_PERMISSION` с package-uri + `FLAG_ACTIVITY_NEW_TASK`, возврат — `Intent(context, MainActivity::class.java)` c `NEW_TASK|CLEAR_TOP|SINGLE_TOP` (MainActivity singleTask).

### 1.2 `ui/screens/SettingsScreen.kt` — кнопка «Настройки Android» (кнопка 10)
- Импорты: `com.respondent.pro.kiosk.KioskManager`, `com.respondent.pro.kiosk.SettingsExcursionOverlay`;
- `val kioskManager = viewModel.kiosk` сразу после `val context = LocalContext.current`;
- `onClick` по брифу, порядок по **R4/M3**: **сначала** `kioskManager.beginExcursion(activity)` (до `startActivity` и до фактического ухода из foreground), **затем** `canDraw` ? `startActivity(ACTION_SETTINGS)` + `Overlay.show` : `Overlay.openPermissionScreen`.

### 1.3 `viewmodel/SettingsViewModel.kt` — **R1**
- Ровно ОДИН раз добавлен параметр конструктора `private val kioskManager: KioskManager` (+ импорт) — Task 8 будет ссылаться, не дублируя;
- Публичный геттер `val kiosk: KioskManager get() = kioskManager`.

### 1.4 `MainActivity.kt` — **R5**
- Импорт `SettingsExcursionOverlay`;
- В `onResume`: `SettingsExcursionOverlay.hide(this)` вставлен **ДО** `kioskManager.onActivityResumed(this)` (порядок из брифа сохранён).

### 1.5 Манифест
`SYSTEM_ALERT_WINDOW` уже был (`AndroidManifest.xml:8`) — правка не потребовалась. Новых строк интерфейса не добавлено (бриф не добавляет). БД/DataStore не тронуты. Политики отключения отладки нет.

### 1.6 Сборка/установка
- `$env:JAVA_HOME=…jbr-21.0.11; .\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** (предупреждения только предсуществующие, `menuAnchor` deprecation);
- `adb install -r …\app-debug.apk` → **Success**.

---

## 2. Устройственные пробы (фактические выводы и тайминги)

### P4-повтор / Excursion-круг (полный) — ВЫПОЛНЕН
Цикл проверен дважды (полный цикл — 12:34):

| Момент (logcat) | Событие |
|---|---|
| 12:34:03.612 | `ActivityTaskManager: START {act=android.settings.SETTINGS cmp=com.android.settings/.homepage.SettingsHomepageActivity} from uid 10157` (наш uid — кнопка 10) |
| 12:34:03.636 | `I ExcursionOverlay: Overlay shown` (Log.i, R8) |
| — | `mCurrentFocus = com.android.settings/SettingsHomepageActivity`; на скриншоте — кнопка «◀ В RESPONDENT.PRO» справа внизу поверх системных настроек; статус-бар вернулся (Lock Task покинут) |
| 12:34:29.135 | `START {flg=0x34000000 cmp=com.respondent.pro/.MainActivity} from uid 10157` (тап по оверлею) |
| 12:34:29.149 | `I ExcursionOverlay: Overlay hidden` |
| — | `mCurrentFocus = com.respondent.pro/MainActivity`; `mResumedActivity = MainActivity`; **`mLockTaskModeState=LOCKED`** (Lock Task восстановлен) |

Ранее (12:31:51–12:31:57 и 12:32:13–12:32:17) — два дополнительных круга с теми же логами (плюс внешние тапы, см. concerns №3). Первое нажатие при `appops=default` ушло сразу в системные настройки с оверлеем (разрешение на этом устройстве granted по умолчанию); ветка `openPermissionScreen` проверена отдельно (см. P2-B): открыла `com.android.settings/.Settings$AppDrawOverlaySettingsActivity` корректно.

### P2 — «am kill» + watchdog (Review Focus №1 и №2)

**P2-A: порядок из брифа (настройки через кнопку 10, оверлей виден) → `am kill` ОТКАЗАН.**
- 12:35:50.205 `ADB_SERVICES: … raw:am kill com.respondent.pro`;
- PID 6189 **выжил**; причина в `dumpsys activity processes`:
  `Proc # 1: prcp F/ /IMPF trm: 0 6189:com.respondent.pro/u0a157 (has-overlay-ui)` —
  видимое окно `TYPE_APPLICATION_OVERLAY` поднимает oom_adj до perceptible, и `am kill` (убивает только background) отказывается его убивать;
- прямой `shell kill -0` → `Operation not permitted` (shell не может сигнализировать app-процессы).
- **Вывод (finding):** пока оверлей показан, процесс неуязвим для `am kill` — P2 в буквальной последовательности брифа невыполним; это НЕ дефект (процесс жив = поднимать нечего). Поэтому ниже — P2-B.

**P2-B: тот же порядок (сначала переключились в настройки, потом kill), оверлей не показан.**
Временно `appops set com.respondent.pro SYSTEM_ALERT_WINDOW deny` (после пробы восстановлено в `default` = исходное состояние):
1. Тап «Настройки Android» → 12:43:49.276 `START {act=…MANAGE_OVERLAY_PERMISSION dat=package:com.respondent.pro … AppDrawOverlaySettingsActivity} from uid 10157` (beginExcursion отработал до startActivity; `show()` не вызывался — ветка else ✓);
   `mCurrentFocus = com.android.settings/.Settings$AppDrawOverlaySettingsActivity`, PID 6189 в фоне;
2. `am kill` в 12:44:07.322 → через 3 с PID пуст — **процесс мёртв** ✓;
3. Ожидание 75 с:
   - 12:44:20.290 `START {act=MAIN cat=LAUNCHER pkg=com.respondent.pro cmp=…MainActivity} from uid 1000` — **система** перезапустила убитый HOME-процесс: **+13.0 с после kill** (ожидание ≤60 с ✓);
   - 12:44:22.857 `WatchdogScheduler: Watchdog armed (EXACT)` в новом PID 7282 — цепочка стража пересоздана;
   - 12:45:22.880 `RestartReceiver: Skip restart (foreground=true, excursion=false)` — обычный тик;
   - `mCurrentFocus = com.respondent.pro/MainActivity` ✓, фона-старта не было (нет `startActivity failed`).
- **Расхождение с ожиданием брифа:** строки `Restarting MainActivity` НЕТ — после `am kill` приложение подняла система (uid 1000, +13 с) раньше, чем сработал alarm стража (≤60 с); `onCreate` новой инстанции перепланировал тот же request-id будильника, поэтому receiver'овский skip тоже не печатался. Критерий брифа «фокус наш ≤ ~60 с, без background-restriction» выполнен → эскалация по §10 не требуется; если партнёру нужен именно лог `Restarting MainActivity`, это свойство конфигурации HOME-лаунчера — см. concerns №2.

**Review Focus №2 («страж не вытаскивает из настроек»):** приложение находилось в системных настройках с активным excursion; за окно 75 с:
- 12:38:23.177 `RestartReceiver: Skip restart (foreground=false, excursion=true)` — тик стража **не** вытащил приложение из настроек ✓;
- `mCurrentFocus` всё это время — `com.android.settings` (окно прервано на +50 с внешним тапом по оверлею, см. concerns №3).

`am force-stop` / `am start -S` не использовались (R8b); логи — через `adb logcat -c` → триггер → `logcat -d | Select-String` (R7).

### P5 — тайминг крэш-перезапуска (замер, R6)

| Момент (logcat) | Событие | Δ от крэша |
|---|---|---|
| 12:46:54.193 | `raw:am crash com.respondent.pro` | 0 |
| 12:46:54.336 | `E AndroidRuntime: FATAL EXCEPTION: main` | +0.14 с |
| 12:46:54.447 | `WatchdogScheduler: Restart alarm in 1000ms` (крэш-хук) | +0.25 с |
| 12:46:54.507 | `Process com.respondent.pro (pid 7282) has died: fore TOP` | +0.31 с |
| 12:46:59.514 | `Start proc 7513 … for broadcast {…RestartReceiver}` | **+5.32 с** (alarm назначен на +1.0 с, доставлен ≈ **+4.3 с** — согласуется с ~4 s из T5) |
| 12:47:02.456 | `RestartReceiver: Restarting MainActivity` | **+8.26 с** (≈2.9 с — холодный старт процесса) |
| 12:47:02.461 | `START {flg=0x34000000 …MainActivity} from uid 10157` | +8.27 с |
| 12:47:02.945 | `Watchdog armed (EXACT)` — цепочка жива | +8.75 с |

- Фокус после рестарта — `com.respondent.pro` ✓.
- **Ожидание брифа <2 с НЕ выполнено: фактически 8.3–8.7 с** (T5 фиксировал ~7.2 с). Разбивка: ≈4.3 с — задержка доставки exact-alarm прошивкой + ≈2.9 с — cold start процесса. Согласно R6 зафиксировано как факт, **не** дефект; `CRASH_DELAY_MS=1000` / `PERIOD_MS=60000` **не менялись** (см. concerns №1).

### Тесты (регресс, перед коммитом)
```
KioskPolicyTest:      tests=5 failures=0 errors=0
ProvisioningQrTest:   tests=5 failures=0 errors=0
WatchdogSchedulerTest: tests=3 failures=0 errors=0
Итого: 13/13, 0 failures (BUILD SUCCESSFUL)
```

---

## 3. Изменённые файлы
| Файл | Изменение |
|---|---|
| `app/src/main/java/com/respondent/pro/kiosk/SettingsExcursionOverlay.kt` | **создан** (по брифу + R8: Log.i вместо Log.d, добавлен `Log.i("Overlay hidden")`) |
| `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt` | импорты, `kioskManager = viewModel.kiosk`, onClick кнопки 10 (R4: beginExcursion → startActivity) |
| `app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt` | + ctor `kioskManager: KioskManager` (R1, ровно один раз), + геттер `kiosk` |
| `app/src/main/java/com/respondent/pro/MainActivity.kt` | + импорт, `hide(this)` до `onActivityResumed(this)` (R5) |

Манифест, БД, DataStore, строки UI — без изменений. Один коммит: `2f4dba7`.

---

## 4. Self-review
- Бриф Step 1–4 выполнен дословно; отклонения только по явным amendments R1/R4/R5/R8 (+Log.i в hide, комментарий M3 в onClick).
- `kioskManager` добавлен в VM **один раз** (R1) — Task 8 подключается через `viewModel.kiosk` без дублирования.
- Порядок `beginExcursion` → `startActivity` (R4) — подтверждён логически и на устройстве: флаг excursion поднят до ухода (проверено косвенно: тик 12:38:23 дал `excursion=true`, пока процесс был жив).
- Порядок в `onResume`: `hide` **до** `onActivityResumed` (R5) ✓.
- R8: все verification-строки нового кода — `Log.i`; `Log.e` не тронуты. R8b: только `am kill`/`am crash` (никаких force-stop/-S). R7: `logcat -c` → триггер → `logcat -d | Select-String`.
- Предупреждений компиляции новых нет (только предсуществующие `menuAnchor`); Hilt-сборка прошла (новая зависимость KioskManager в @HiltViewModel).
- Тесты 13/13 до и без изменений после коммита (кода после тестов не менял).

## 5. Concerns
1. **P5: рестарт 8.3–8.7 с вместо ожидаемых <2 с** — ≈4.3 с задержка доставки exact-alarm прошивкой + ≈2.9 с cold start. По R6 — не подгонял, `CRASH_DELAY_MS`/`PERIOD_MS` не трогал. Отклонение системное (firmware), не кодовое.
2. **P2: ожидание брифа «в логах Restarting MainActivity» не выполнено буквально** — после `am kill` процесс подняла система как HOME-лаунчер (+13.0 с, uid 1000) раньше alarm'а стража; лог `Restarting MainActivity` в этом сценарии на данном устройстве не наблюдался. Критерий «фокус наш ≤60 с, без background-restriction» — выполнен. Если spec требует, чтобы после kill приложение поднимал именно watchdog, — это вопрос к партнёру (наблюдение T5 показало, что при `am crash` из foreground watchdog срабатывает: +8.3 с).
3. **Нештатные входные события на планшете:** в окне 12:31–12:39 зафиксированы тапы, которые я не посылал (возвраты с оверлея в 12:31:57/12:32:17/12:39:03, переход кнопки10 в 12:32:13, изменения рейтинга звёзд). Позднее 8-секундный сниффинг `getevent -l` (физические события видны только там) показал **ноль** физических тачей. Гипотеза: оператор присутствовал в раннем окне; изоляция проб не гарантирована (из-за этого окно «75 с без kill» прервано на +50 с; сам критический skip-tick 12:38:23 внутри окна пойман).
4. **Вход в настройки по synthetic input нестабилен:** диалог PIN открывается при long-press недетерминированно, закрывается гонкой «release outside-tap» и авто-сбросом через `resetTimeout=10 с`; `input text` в поле PIN не регистрируется (работают tap'ы по цифровой клавиатуре / `input keyevent 7`). Это артефакт инъекции, а не регрессия: excursion-круг и все пробы выше уже выполнены; бриф Step 6 изначально предписывает ручной ввод на планшете. Побочный эффект инъекций: на стартовом экране остался рейтинг «2 звезды» (сбрасывается таймером/вручную).
5. **Overlay-разрешение:** на этом устройстве `canDrawOverlays=true` уже при `appops=default` (первое нажатие сразу показало оверлей — экран выдачи разрешения в «счастливом пути» не понадобился). Экран разрешения проверен через `appops deny` (открылся корректно). Финальное состояние устройства: `SYSTEM_ALERT_WINDOW: default` (= исходное состояние сессии), приложение установлено (2f4dba7), на переднем плане, цепочка стража активна.
