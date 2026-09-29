# Task 10 Report: Финальная приёмка — чек-лист spec §12

**Status:** ✅ COMPLETE (10/10 checklist rows PASS; 0 code fixes → **0 commits**, tree clean at `663848d`)
**Branch:** `kiosk-mode` (worktree `C:\projects\feedback-app\.worktrees\kiosk-mode`)
**Date:** 2026-09-29
**Device:** HVA4H2PY Lenovo TB-X606X, Android 10 (SDK 29), landscape 1920×1200, adb `C:\platform-tools\adb.exe`
**Rules applied:** R8 (Log.i logs, `logcat -d | Select-String`), R8b (no force-stop/-S), R9 (dumpsys activity activities for lock task), R10 (crash-restart timing), R11 (clean 75s window), R12 (QR decode), R13(a) (DO evidence from T8)

---

## Step 1: Юнит-тесты

```
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --rerun
→ BUILD SUCCESSFUL (fresh run: 1 task executed)
```

| Suite | tests | failures | errors |
|---|---|---|---|
| KioskPolicyTest | 5 | 0 | 0 |
| ProvisioningQrEncodeTest | 1 | 0 | 0 |
| ProvisioningQrTest | 5 | 0 | 0 |
| WatchdogSchedulerTest | 3 | 0 | 0 |
| KioskStringsTest | 2 | 0 | 0 |
| **TOTAL** | **16** | **0** | **0** |

## Step 3: Запреты отладки (spec §5, решение 7)

```
Select-String -Path app\src\main\java\com\respondent\pro\kiosk\*.kt -Pattern "DISALLOW_DEBUGGING_FEATURES|ADB_ENABLED"
→ (пусто) — политик отключения отладки нет ✅
```

---

## Step 2: Чек-лист на устройстве (spec §12)

| # | Проверка | Результат | Evidence |
|---|---|---|---|
| 1 | Автозапуск после перезагрузки + HOME по умолчанию | **PASS** | `adb reboot` → 71.7s → `mCurrentFocus=…com.respondent.pro/.MainActivity`; `resolve-activity HOME → com.respondent.pro/.MainActivity (match=0x108000, isDefault=true)`; logs: `12:24:04.078 BootReceiver: BOOT_COMPLETED — launching MainActivity`, `12:24:04.288 KioskManager: Policies applied: 7`, `Watchdog armed (EXACT)`; `mLockTaskModeState=LOCKED`. ⚠️ P4-регресс ВНУТРИ сессии — см. «Наблюдения» №1 |
| 2 | Крэш → перезапуск (spec <2s; R10) | **PASS (по R10)** | `am crash` device 12:24:52.506 → died +0.19s → **`RestartReceiver: Restarting MainActivity` +5.2s** → focus `com.respondent.pro/.MainActivity` набран к +6.9s (≤60s ✓). Лог `Restarting MainActivity` ПРИСУТСТВУЕТ ✓. Spec <2s не выполнено (факт ≈6–7s: ~4s задержка exact-alarm прошивки + cold start) — принято R10. Наблюдение: окно system ResolverActivity (HOME-chooser) ~5.7s, закрылось без участия пользователя — см. №2 |
| 3 | `am kill` → перезапуск ≤60с, окно 75с чистое (R11) | **PASS** | appops overlay deny (ветка без оверлея, T6 P2-B) → «Настройки Android» 12:37:06.398 → focus `com.android.settings/…AppDrawOverlaySettingsActivity` → `am kill` 12:37:08.587 → PID 5623 пуст (+3s) → **75s без единого касания** → focus `com.respondent.pro/.MainActivity`, PID 6900, LOCKED. Logs: `12:37:58.141 Start proc 6900 … {RestartReceiver}` (**+49.6s**), **`12:37:58.501 RestartReceiver: Restarting MainActivity`** ✓ (на этот раз — путь watchdog, а не system-HOME как в T6) |
| 4 | Lock Task: Home/Назад/Недавние не выходят | **PASS** | keyevent HOME → focus app; APP_SWITCH → focus app; BACK → focus app; после всех — `mLockTaskModeState=LOCKED` |
| 5 | Панель уведомлений недоступна; экран не гаснет 3+ мин | **PASS** | 5a: `input swipe 600 0 600 1200 300` → focus остался `com.respondent.pro/…MainActivity`; в `dumpsys window windows` НЕТ окна NotificationShade (всего 9 окон, systemui: только NavigationBar0/StatusBar); `dumpsys statusbar` — панели не expanded; скриншот `t10-shade.png`. 5b: фоновое окно ≥195s без касаний → см. «Итог 5b» ниже |
| 6 | «Настройки Android» → оверлей → возврат → Lock Task | **PASS** | 12:44:59.940 `START android.settings.SETTINGS …SettingsHomepageActivity from uid 10157` → 12:44:59.967 `ExcursionOverlay: Overlay shown` (Log.i) → focus settings; tap оверлея (1770,953) → 12:45:55.773 `START …MainActivity flg=0x34000000 from uid 10157` → 12:45:55.786 `ExcursionOverlay: Overlay hidden` → focus app, `mLockTaskModeState=LOCKED` ✓. Скриншоты `t10-exc1/2.png` |
| 7 | Без DO → деградация → возврат DO (R13a) | **PASS** | Ручной повтор НЕ выполнялся (R13a): `dpm remove-device-owner` на этой прошивке отсутствует, `remove-active-admin` → SecurityException (T8). Доказательства T8: `t8-status-no-do.png` (⚠️) → DO восстановлен → `t8-final-status.png` (✅). **DO state re-verified здесь:** `dumpsys device_policy` → `admin=ComponentInfo{com.respondent.pro/.kiosk.KioskAdminReceiver}`, Enabled Device Admins (User 0, provisioningState: 3); статус-карточка в приложении: **`✅ Device Owner выдан / Lock Task активен`** (dump t10-k2/k6/k8, скриншот `t10-do.png`) |
| 8 | Инструкция ADB: команды копируются | **PASS** | Спойлер ADB раскрыт: 4 команды + URL == `KioskConfig.adbCommands()` (dump t10-k8.txt). `cmd clipboard` на Android 10 отсутствует → paste-тест: tap команды `adb shell cmd package set-home-activity …` → focus SSID-поля → `input keyevent 279` → поле содержит **ровно** `adb shell cmd package set-home-activity com.respondent.pro/.MainActivity` ✓ (после — поле очищено, verified empty) |
| 9 | QR-payload валиден | **PASS** | ProvisioningQrTest: 5/5 (Step 1) |
| 10 | Строки локализованы ru/en | **PASS** | KioskStringsTest: 2/2 (Step 1) |

**Итог 5b (экран 3+ мин):** **PASS** — фоновое окно без единого касания: T0=13:05:59 `mState=ON` → T1=13:09:14 (**195s**) `mState=ON`, `mWakefulness=Awake`, фокус `com.respondent.pro/.MainActivity`; скриншот `t10-screen3min.png` (65108 B — стартовый экран, как baseline). Механизм: `FLAG_KEEP_SCREEN_ON` (MainActivity) при системном `screen_off_timeout=1000` (значение не менялось).

---

## R12 (major T9-review): декод QR с экрана устройства — **MATCH**

Метод: приложение → Настройки → ИНФОКИОСК → QR-спойлер → SSID `TestWifi_Kiosk`, пароль `pass1234` → «Показать QR-код» → `adb shell screencap -p` (`t10-qr.png`) → регион ImageView из uiautomator dump `[576,244][1344,884]` (768×640) → декод zxing `QRCodeReader` (TRY_HARDER) на JVM в **throwaway-тесте** (Robolectric NATIVE BitmapFactory для пикселей) → `assertEquals(ProvisioningQr.buildPayload(KioskConfig.APK_DOWNLOAD_URL, …))`.

```
### DECODED  ={"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":"com.respondent.pro/.kiosk.KioskAdminReceiver","android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":"https://github.com/gurinovi-ch/respondent-pro/releases/download/v1.0-test/RESPONDENT.PRO.apk","android.app.extra.PROVISIONING_WIFI_SSID":"TestWifi_Kiosk","android.app.extra.PROVISIONING_WIFI_PASSWORD":"pass1234","android.app.extra.PROVISIONING_SKIP_ENCRYPTION":true}
### EXPECTED = (идентичная строка)
BUILD SUCCESSFUL — assertEquals прошёл; декод с первого попадания, без marginal-ретраев
```

**Вердикт:** payload полностью совпадает с `buildPayload(...)` (URL — закреплённый тег `v1.0-test`, сверен с константой as-is per R12/T7). **Фикс `size=1024`/`aspectRatio(1f)` НЕ нужен.**
**Судьба теста:** throwaway (абсолютный путь к evidence-файлу сломал бы CI/другие машины) → **удалён**; `git status --short` пуст. Скриншот `t10-qr.png` сохранён в evidence.

---

## P1 / P2 / P5 (итоги)

- **P1 (фоновый старт на Android 10 после перезагрузки):** ✅ — `BOOT_COMPLETED` → BootReceiver → `START MainActivity` (лог + перехват фокуса ≤72s от команды reboot), `Policies applied: 7`, LockTask LOCKED.
- **P2 (kill → восстановление ≤60s):** ✅ — +49.6s (watchdog-путь, `Restarting MainActivity` в логах). В этот прогон system-HOME-«fast path» (+13s из T6) не сработал — восстановление целиком сделал alarm стража.
- **P5 (крэш → восстановление):** ✅ по критерию R10 — фокус ~+6–7s, лог `Restarting MainActivity` +5.2s. Spec `<2s` — нет (≈6–7s, firmware alarm delay + cold start; R10/R6 принято, `CRASH_DELAY_MS=1000` не менялся).
- **P4 (carry-forward):** после reboot — OK; **после крэша (п.2) preferrence HOME слетел** (`resolve-activity → ResolverActivity, isDefault=false, match=0x0`) → повторен `cmd package set-home-activity …` → `Success`, снова `com.respondent.pro/.MainActivity isDefault=true`. Финальная перепроверка — в «Финальном состоянии» ниже. Это ВТОРОЙ зафиксированный слёт (аномалия T4) — PMS-гонка, на работу киоска не влияет (BootReceiver/watchdog от preferrence не зависят; HOME в LockTask блокируется).

---

## Наблюдения (честный учёт, без фиксов)

1. **Слёт HOME-preference после крэша** — см. P4 выше; повторный `set-home-activity` стабильно лечит (T4-паттерн).
2. **ResolverActivity при восстановлении после крэша:** система стартовала HOME-chooser (`START {act=MAIN cat=[HOME] …ResolverActivity} from uid 0` +0.25s), окно висело ~5.7s и закрылось, когда watchdog поднял MainActivity. Пользовательское вмешательство НЕ требовалось, киоск восстановился сам; если бы receiver не сработал — chooser ждал бы выбора. Информационное наблюдение (не дефект чек-листа: фокус вернулся, лог есть).
3. **Восстановление UI после `am kill`:** система сохранила saved-instance-state — после рестарта приложение открылось СРАЗУ в Настройках (с позицией скролла), т.е. **минуя PIN-экран**. Поведение Android saved state; с точки зрения киоска — «мягкое» (LockTask LOCKED). Для spec: сценарий «киоск убит, пока админ был в настройках» возвращает в настройки без PIN — решать партнёру.
4. **Вход в настройки недетерминирован:** T8-рецепт «4000 ms long-press» сегодня диалог НЕ оставлял (release закрывает); рабочим оказался **800ms** press (+dump-verify на каждом шаге, слепых тапов (960,590) не делалось). Long-press открывает диалог неточно — артефакт ввода/прошивки (T6 concern №4), не регрессия.
5. **`cmd clipboard` отсутствует на Android 10** («No shell command implementation») — верификация clipboard сделана paste-тестом (p.8).
6. **Item 3 требует appops deny:** с показанным оверлеем процесс perceptible и `am kill` отказывает (T6 P2-A); использована T6 P2-B последовательность (deny → excursion через экран разрешения оверлея → kill) — **appops восстановлен в исходное `allow`** (проверено `appops get`).
7. **`screen_off_timeout=1000` (1s)** — системное значение на момент старта сессии; экран держит `FLAG_KEEP_SCREEN_ON` в MainActivity (подтверждено пробой 12s и окном 5b). Значение НЕ менялось.
8. **Гигиена ввода:** PIN-диалог dump-верифицировался ДО каждого тапа; **rating=0 на финише** (`selected="true"` count = 0) — страр-тапов и утечек в Telegram в этой сессии НЕ было (в отличие от T9).
9. Мелкие артефакты инструментов: один зависший `adb shell screencap` (60s таймаут, повтор ОК); `exec-out screencap >` через PowerShell портит PNG (UTF-16 BOM) — использован `screencap /sdcard + pull`.
10. Поля QR-спойлера остались с тестовыми значениями `TestWifi_Kiosk` / `pass1234` (как и в T9); пароль виден только внутри PIN-гейта.

---

## Step 4: Фиксы отклонений

**Не потребовались** — все 10 строк чек-листа PASS, R12 без фикса. Коммитов нет; `git status --short` пуст; HEAD `663848d`.

## Step 5: Напоминания партнёру

- Обновление APK на клиентских планшетах — **ручная публикация** нового ассета `RESPONDENT.PRO.apk` в GitHub Releases (автообновление — отдельная задача); сегодня URL закреплён на теге `v1.0-test` (принятый компромисс T7) — при выходе новой версии нужно поднять тег/URL.
- **Factory reset** для реального QR-провижининга — только по явному согласию владельца планшета (в этой приёмке не выполнялся).

---

## Финальное состояние устройства (после всех проверок)

- **Фокус:** `com.respondent.pro/.MainActivity`; `mLockTaskModeState=LOCKED`; PID 6900 (без перезапусков после п.3).
- **P4:** `resolve-activity HOME → com.respondent.pro/.MainActivity` (`match=0x108000, isDefault=true`) — повторный `set-home-activity` после слёта держится.
- **DO:** `admin=ComponentInfo{com.respondent.pro/.kiosk.KioskAdminReceiver}` (User 0, provisioningState 3); в приложении статус ✅ (скриншот `t10-do.png`).
- **appops SYSTEM_ALERT_WINDOW:** `allow` = исходное значение сессии (восстановлено после п.3).
- **Экран:** start screen, **rating = 0** (`selected=true` count 0 в `t10-end.xml`), greeting `Давай уже действуй!`, org `ООО "Планшет"` — всё без повреждений; страр-тапов/отправок в Telegram в сессии не было.
- **Настройки:** PIN `0000`, org/greeting/timeout `10`, switch «Отправлять незавершённый отзыв» не трогался; поля QR-спойлера = `TestWifi_Kiosk`/`pass1234` (наблюдение №10).
- **Системные:** `accelerometer_rotation`, `screen_off_timeout` (1000) — не менялись; factory reset не выполнялся.
- **Код/репозиторий:** дерево чистое, HEAD `663848d`, коммитов не добавлено (`.superpowers/` в .gitignore).

## Evidence (C:\Users\leonb\AppData\Local\Temp\opencode\)

`t10-00.png` (baseline), `t10-ui1.xml` (старт), `t10-pin*.xml` (PIN-диалоги), `t10-settings*.xml/txt`, `t10-s*.xml`, `t10-androidbtn.txt`, `t10-k*.xml/txt` (ИНФОКИОСК/ADB), `t10-do.png` (✅ карточка), `t10-qr1/qr2/qr3/pw/pw2/nk/btn/menu/paste/clear.xml`, `t10-dlg.xml` (QR-диалог), `t10-qr.png` (QR-скриншот для R12), `t10-exc1/2.png` (экскурсия), `t10-shade.png`, `t10-final.xml/txt` (старт, rating=0), `t10-screen3min.png`, полный лог — в настоящем отчёте.

## Deviations от брифа

1. П.7 — вариант (a) R13 (T8 evidence + re-verify DO), а не повторный снос DO.
2. П.2/п.3 — фактические тайминги и путь рестарта отличаются от текста чек-листа (R10/R11 покрывают).
3. П.5 — swipe проверен, пока под приложением был restored-settings UI (см. №3 наблюдений); критерий (нет шторки, фокус не уходит) выполнен глобально.
4. Throwaway decode-тест удалён после прогона (см. R12); постоянных новых тестов не добавлено — дерево без изменений.
