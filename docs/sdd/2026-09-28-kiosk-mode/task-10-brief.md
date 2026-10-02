### Task 10: Финальная приёмка — чек-лист spec §12

**Files:**
- Нет новых файлов; при отклонениях — точечные правки в созданных ранее.

**Interfaces:**
- Consumes: всё из Tasks 1–9.
- Produces: подтверждённый чек-лист приёмки + (при необходимости) фиксы.

- [ ] **Step 1: Прогнать все юнит-тесты**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, все тесты PASS (`ProvisioningQrTest`, `KioskPolicyTest`, `WatchdogSchedulerTest`, `KioskStringsTest`).

- [ ] **Step 2: Чек-лист на устройстве (spec §12) — выполнить по пунктам**

| # | Проверка | Действие / команда | Ожидание |
|---|----------|--------------------|----------|
| 1 | Автозапуск после перезагрузки + лаунчер по умолчанию | `adb reboot`; `Start-Sleep 60`; `adb shell dumpsys window \| Select-String mCurrentFocus`; `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN` | Фокус — `com.respondent.pro`; HOME резолвится в `.MainActivity` |
| 2 | Крэш → перезапуск < 2 сек | `adb logcat -c`; `adb shell am crash com.respondent.pro`; `Start-Sleep 4`; `adb shell dumpsys window \| Select-String mCurrentFocus` | Фокус — `com.respondent.pro`; лог `Restarting MainActivity` |
| 3 | Убийство процесса → перезапуск ≤ ~60 сек | Экскурсия в настройки (кнопка «Настройки Android») → `adb shell am kill com.respondent.pro` → `Start-Sleep 75` → фокус | Фокус — `com.respondent.pro`; лог `Restarting MainActivity` |
| 4 | Lock Task: Home/Назад/Недавние не выходят | `adb shell input keyevent KEYCODE_HOME`; `adb shell input keyevent KEYCODE_APP_SWITCH`; после каждого — `dumpsys window \| Select-String mCurrentFocus` | Фокус всегда `com.respondent.pro` |
| 5 | Панель уведомлений недоступна; экран не гаснет | На устройстве: провести сверху вниз; оставить экран 3+ мин | Тень/панель не появляются; экран не гаснет |
| 6 | «Настройки Android» → оверлей → возврат → Lock Task | Шаг 6 Task 6 (ручной) | Возврат по кнопке, Lock Task восстановлен |
| 7 | Без DO → деградация → возврат DO | `adb shell dpm remove-device-owner` → перезайти в настройки → `adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver` → перезайти в настройки | Статус ⚠️, приложение живо, политик нет; после возврата DO — ✅ |
| 8 | Инструкция ADB: команды копируются | Настройки → «ИНФОКИОСК» → раскрыть ADB → нажать команду → вставить в поле заметок | Вставляется текст команды |
| 9 | QR-payload валиден | юнит-тест Task 2 (шаг 1) | PASS |
| 10 | Все строки локализованы ru/en | юнит-тест Task 8/9 (`KioskStringsTest`) | PASS |

- [ ] **Step 3: Проверить отсутствие запретов отладки (spec §5, решение 7)**

```powershell
Select-String -Path app\src\main\java\com\respondent\pro\kiosk\*.kt -Pattern "DISALLOW_DEBUGGING_FEATURES|ADB_ENABLED"
```
Expected: пусто (политик отключения отладки нет).

- [ ] **Step 4: Фиксы отклонений (если найдены)**

Любое исправление — минимальное, в задаче-владельце файла, со своим прогоном тестов и commit:
```bash
git add -A
git commit -m "fix(kiosk): <что исправлено по итогам приёмки>"
```

- [ ] **Step 5: Итоговый отчёт партнёру**

Сообщить: результаты чек-листа (таблица Step 2 — выполнено/не выполнено), выводы P1/P2/P5 (включая, сработал ли фоновый старт на Android 10), и напомнить:
- обновление APK на клиентских планшетах — ручная публикация нового ассета `RESPONDENT.PRO.apk` в GitHub Releases (автообновление — отдельная задача);
- factory reset для реального QR-провижининга выполняется только по явному согласию владельца планшета.
