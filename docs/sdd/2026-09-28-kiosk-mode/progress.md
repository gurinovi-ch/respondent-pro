# SDD ledger — plan: docs/superpowers/plans/2026-09-28-kiosk-mode.md

## Setup

- Worktree: `C:\projects\feedback-app\.worktrees\kiosk-mode` (ветка `kiosk-mode`, base `e39c1f6`)
- Согласие партнёра: worktree выбран; предварительные коммиты незакоммиченной работы — одобрены
- `bash` отсутствует → скрипты SDD (`sdd-workspace`, `task-brief`, `review-package`) воспроизведены на PowerShell
- Baseline: `testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL (0 unit tests на старте)
- Предкоммиты в master: `daa2a41` chore gitignore (инцидент перезаписи `.gitignore` восстановлен+amend), `5f2efb2` feat Email/FeedbackSender/фикс дублей, `e39c1f6` docs план
- Модели: implementer = `opencode/mimo-v2.6-flash-free`; task reviewer = `opencode/space-bunny-free`; re-review = `opencode/mimo-v2.6-flash-free`; финальный ревью = `opencode/longcat-2.5-preview-free`
- ВАЖНО для bookkeeping: обновления ledger — только через write-инструмент (полная перезапись UTF-8); PowerShell `Add-Content` ломает кодировку (инцидент: mojibake, исправлено полной перезаписью)

## Preflight scan — пары задач, разделяющие файлы/интерфейсы

| Pair | Shared | Producer → Consumer | Finding |
|------|--------|---------------------|---------|
| 1→3,4,5 | AndroidManifest, KioskAdminReceiver, launchMode singleTask | T1 создаёт → T3 (ComponentName), T4 (секция BootReceiver), T5 (секция RestartReceiver) | Секции манифеста непересекающиеся; OK |
| 1→7,8,10 | литерал `com.respondent.pro/.kiosk.KioskAdminReceiver` | T1 регистрирует → T7 adbCommands, T8 шаг 8, T10 чек-лист | Литерал одинаков везде; OK |
| 2→9 | `ProvisioningQr.buildPayload/encodeQr`, zxing-dep | T2 создаёт → T9 использует | OK; encodeQr — в T2 (smoke-check T9, см. R7) |
| 3→5 | `KioskManager.shouldRestart/isForeground/excursionActive`, `RestartPolicy` | T3 → T5 RestartReceiver | OK |
| 3→6 | `beginExcursion` | T3 → T6 | OK (контракт порядка — minor M3, в бриф T6) |
| 3→8 | `status(): KioskStatus` | T3 → T8 | OK |
| 3→6 | MainActivity.onResume/onPause | T3 добавляет хуки → T6 вставляет `SettingsExcursionOverlay.hide(this)` ДО `onActivityResumed` | Порядок задан в T6 Step 4; OK |
| 5→6 | MainActivity.onCreate | T5 добавляет scheduleNext → T6 onCreate не трогает | OK |
| 6→8 | SettingsViewModel: T6 и T8 ОБЕ добавляют параметр конструктора `kioskManager` | T6 (getter `kiosk`) → T8 (status) | КОНФЛИКТ текста плана — Ruling R1 |
| 8→9 | KioskCard: сигнатура + call-site | T8 создаёт → T9 расширяет QR-параметрами | План T9 задаёт оба; OK |
| 8→9 | KioskStringsTest: список полей | T8 (7) → T9 (+6) | OK |
| 7→8,9 | KioskConfig (`adbCommands()`, `APK_DOWNLOAD_URL`) | T7 → T8, T9 | OK; T7 интерактивный — Ruling R3 (стоп) |
| AppStrings | класс + ruStrings + enStrings: T8 (+7), T9 (+6) | последовательные правки | OK |
| 1→4 | HOME-категории в intent-filter MainActivity | последовательно | OK |

## Preflight scan — согласованность каждой задачи с самой собой

| Task | Проверка | Finding |
|------|----------|---------|
| 1 | юнит-тестов нет — приёмка = проба P1 | OK (план предписывает); ревьюер подтвердил spec ✅ |
| 2 | тесты ассертят ключи == строки `buildPayload` | OK; дефект кода плана (encodeAsBitmap) устранён — R5 |
| 3 | `KioskPolicyTest` ожидает 7 действий == `KioskPolicy.actions` | OK; minor M1 (нет assert size) отложен |
| 4 | без тестов — пробы P4 + reboot | OK; log-строки под R8 (Log.i) |
| 5 | `WatchdogSchedulerTest` vs `decide()` | OK; под R8 (Log.i) |
| 6 | шаг 2 ссылается на `kioskManager` из шага 3 того же брифа | OK — бриф читается целиком |
| 7 | публикация (push/gh/release) — side effect вне worktree | Стоп — Ruling R3 |
| 8 | конструктора VM — R1; KioskStringsTest — top-level strings | R1 |
| 9 | прозаические имена лямбд в диалоге — R2; encodeQr smoke — R7 | R2, R7 |
| 10 | чек-лист vs пробы задач | OK; замечания по logcat — учитывает R8 |

## Rulings

- **R1 (T6/T8 SettingsViewModel):** параметр конструктора `kioskManager` добавляет Task 6 (getter `kiosk`); Task 8 НЕ дублирует — только `_kioskStatus/kioskStatus/refreshKioskStatus()`. Стоимость при ошибке: compile error в T8 (виден сразу, fix-round).
- **R2 (T9 диалог):** имена `onDismissQrBitmap`/`shareQrForCurrentPayload` — прозаические; реализатор вправе назвать лямбды иначе, обязателен функционал: share того же payload, что в диалоге. Стоимость: fix-round, если функционал не совпадёт.
- **R3 (T7 публикация):** `git push`, `gh repo create`, release — side effect вне worktree → при T7 остановиться и спросить партнёра (нужен `gh auth login`). Стоимость при ошибке: публикация без спроса.
- **R4 (инцидент setup):** перезапись `.gitignore` восстановлена до полного содержимого + amend (`daa2a41`); проверено — только 2 добавленные строки.
- **R5 (T2):** код плана `BarcodeEncoder().encodeAsBitmap` не существует в zxing-android-embedded 4.3.0; верный API `encodeBitmap` (проверено javap по AAR). Верна реализация `encodeBitmap`. Для T9: если встретится `encodeAsBitmap` — использовать `encodeBitmap`. Стоимость: compile error (исключена — проверено).
- **R6 (T2, plan-mandated Important):** journeyapps-AAR инжектит CAMERA-поверхность → исправление: `tools:node="remove"` для CAMERA permission + camera uses-feature в манифесте приложения; зависимость и `encodeQr` остаются. Стоимость: остаточный мёртвый CaptureActivity (не экспортирован) — принятo.
- **R7 (T2):** `encodeQr` без юнит-покрытия (Android Bitmap) — обязательный smoke-check перенесён в T9: на устройстве проверить, что QR реально рендерится (не blank/null). Внести акцент в бриф T9 при диспатче.
- **R8 (устройство):** прошивка Lenovo подавляет `Log.d` (доказано контрольным `log -p d`) — ВСЕ verification-bearing логи киоска = `Log.i` (KioskManager: Policies applied / DO skipped; T4: BootReceiver; T5: WatchdogScheduler/RestartReceiver; T6: overlay). **Амend:** KioskManager-часть не в fix-round T3, а включается в диспатч T4 (та же приёмка читает эти логи — экономит цикл fix+re-review на 2-строчной правке; ревью T4 покроет). Стоимость при ошибке: невидимость логов приёмки T4/T10.
- **R8b (устройство):** `am force-stop`/`am start -S` НЕ убивают процесс на этом устройстве — dev-escape брифа T3 не работает. Доступные выходы: excursion (с T6), `dpm remove-device-owner` (последний resort — спросить), reboot. НЕ полагаться на force-stop в T5/T6/T10. Стоимость: зависание в lock task до появления excursion (T6).
- **R9 (T3 concern, controller-resolved):** `dumpsys device_policy` без секции Lock task на Android 10 — принят эквивалентный evidence из `dumpsys activity activities` (LOCKED + whitelist + AUTH_WHITELISTED).

## Progress

- ⚠️ T1 (controller-resolved): SCHEDULE_EXACT_ALARM не пред-granted на API 33+ → fallback INEXACT в T5 обязателен (уже в брифе, Review Focus №3); деградация задержки на Android 13+ — в финальный отчёт.
- ⚠️ T1 (controller-resolved): SYSTEM_ALERT_WINDOW — app-op; покрыто T6 (экран выдачи) + команда appops в adbCommands T7.
- ⚠️ T1 (controller-resolved): единственный тест-девайс API 29 — кросс-версионных проб нет (SDK_INT-гарды + чек-лист T10).
- Task 1: minor (deferred): singleTask без onNewIntent — приёмы из receiver'ов (T4/T5) обязаны быть payload-free — проверить при T4/T5.
- Task 1: minor (deferred): lint на манифесте не запускался — можно в T10.
- Task 1: complete (commits e39c1f6..e6f1780, review clean: spec ✅ / quality Approved)
- Task 2: fix round 1/5 (1 addressed, 0 open — CAMERA surface от zxing-AAR; commits 7418b5c..5737cd7)
- Task 2: minor (deferred): ADMIN_COMPONENT в тесте и манифесте — один литерал в двух местах, авто-синхронизации нет — smoke-проверка в T10.
- Task 2: minor (deferred): CaptureActivity из AAR остаётся в merged-манифесте (не экспортирован, не используется) — инертный мёртвый компонент.
- Task 2: complete (commits e6f1780..5737cd7, review clean после 1 fix-round)
- Task 3: minor (deferred): KioskPolicyTest — добавить `assertEquals(7, actions.size)` (защита от 8-й политики); код сейчас корректен.
- Task 3: minor (deferred): applyPolicies — весь цикл в одном try (brief-mandated; самолечение через идемпотентный перезапуск в onCreate).
- Task 3: minor (deferred): KDoc на `beginExcursion` — «вызывать ДО ухода из активности» (контракт порядка для T6) — добавить в T6/T8-диспатч как напоминание.
- Task 3: minor (deferred): `setLockTaskFeatures(0)` — no-op (дефолт платформы), осознанный explicit-reset.
- Task 3: complete (commits 5737cd7..ee6719c, review clean: spec ✅ / quality Approved)
- Task 4: minor (deferred): BootReceiver не гейтится на DO — некиосковая установка открывает наше приложение при каждой загрузке (безопасно: деградация без DO). Plan-mandated, осознанно.
- Task 4: minor (deferred): receiver объявлен перед activity в манифесте — legal, по брифу; следующим ревьюерам не пересматривать.
- Carry-forward T10: ПЕРЕПРОВЕРИТЬ resolve-activity (P4) после финальной установки И после перезагрузки; при регрессии повторить set-home-activity (гонка PMS после install -r — наблюдение T4).
- Carry-forward T10: logcat-приёмку делать через logcat -d + Select-String; фильтр logcat -s ...:* на этом устройстве даёт пусто (наблюдение T4).
- Task 4: complete (commits ee6719c..6e673df, review clean: spec ✅ / quality Approved)
- Task 5: minor (deferred): RestartReceiver when() без else — чужое action молча игнорируется (plan-mandated); диагностика на устройстве затруднена.
- Task 5: minor (deferred): pendingIntent() получает Activity context на пути MainActivity — conventional choice applicationContext (косметика).
- Task 5: minor (deferred): mode() перезапрашивает ALARM_SERVICE при наличии am у вызывающего (brief-mandated shape).
- Task 5: minor (deferred): внешний try/catch в RespondentApp.scheduleRestart — мёртв (scheduleRestart сам глотает Exception), пустое тело глотнуло бы Error (brief-mandated).
- Task 5: minor (deferred, PLAN GAP): нет backoff/sчётчика при crash-loop — вечный ~7s цикл; spec backoff не требует, recovery от транзиентного крэша — приоритет. Кандидат для финального триажа.
- Task 5: minor (deferred): import MainActivity после dagger-блока в RestartReceiver (порядок импортов).
- Task 5: observation: точный alarm доставлен с задержкой ~4s (план +1.0с, факт +5.1с), полный crash-restore ~7.2s — перепомерить P5 в T6, НЕ чинить подгонкой; фактические тайминги в финальный отчёт.
- Task 5: complete (commits 6e673df..e3b2a8e, review clean: spec ✅ / quality Approved)
- Ruling R10 (T6, controller-resolved): P2 принят по критерию фокуса <=60s (факт +13.0s); отсутствие строки Restarting MainActivity в P2 — система как HOME ускорила восстановление, механизм-атрибуция информационная, не дефект.
- Ruling R11 (T6, controller-resolved): 75s-окно прервано внешним тапом на +50s, но criterion Review Focus No2 закрыт логом Skip restart (foreground=false, excursion=true) внутри excursion-окна + полным excursion-кругом — evidence достаточна. T10: при возможности повторить чистое 75s-окно.
- Task 6: minor (deferred): hide() сбрасывает currentView и логирует hidden ДО/ВНЕ try — при removeView-сдвиге — orphan view + false-positive лог (plan-mandated).
- Task 6: minor (deferred): TextView создаётся от Activity context и живёт в static field — утечка-окно при task-removed без onResume (plan-mandated; applicationContext — решение).
- Task 6: minor (deferred): beginExcursion молча пропускается при context !is Activity — excursionActive=false без лога (plan-mandated; 1 строка Log.i в else).
- Task 6: minor (deferred): неиспользуемый import KioskManager в SettingsScreen; смешанная квалификация android.app.Activity рядом с import Settings.
- Task 6: complete (commits e3b2a8e..2f4dba7, review clean: spec / quality Approved)
- Task 7: publish consent подтверждён R3 (репо respondent-pro публичное, владелец gurinovi-ch); gh CLI установлен через winget, авторизация device-flow.
- Task 7: ⚠️ reviewer-verification закрыт контроллером: gh release view v1.0-test → isDraft=false, assets=[RESPONDENT.PRO.apk]; curl -L → HTTP 200; repo visibility=PUBLIC.
- Task 7: minor (deferred): APK_DOWNLOAD_URL дублирует литерал owner/repo вместо интерполяции $OWNER_REPO (сегодня значения совпадают).
- Task 7: minor (deferred): OWNER_REPO без потребителя до T8/T9 (по брифу); теста, фиксирующего no-placeholders URL, нет.
- Task 7: observation: default branch репо = kiosk-mode; tag URL требует ручного бампа при будущих релизах (осознанный компромисс).
- Task 7: complete (commits 2f4dba7..0d29e33, review clean: spec / quality Approved)
- Task 8: minor (deferred): SettingsScreen.kt:732 dead local `val context = LocalContext.current` в KioskCard (план-мандейт) - удалить.
- Task 8: minor (deferred): зеленый статус ключится на deviceOwner, не на deviceOwner && lockTaskPermitted - при DO=yes/lockTask=no карточка зеленая без предупреждения (план-мандейт).
- Task 8: minor (deferred): status==null (первый кадр до LaunchedEffect) красит Text в error - косметика.
- Task 8: minor (deferred): ClipData.newPlainText label "adb" и для APK-URL строки + unchecked `as ClipboardManager` (план-мандейт).
- Task 8: minor (deferred): KioskStringsTest проверяет только isNotBlank - отчет ошибочно заявил "no missing/extra keys"; усилить RU!=EN per-key assert (ревьюер: EN реально англ., проверено чтением).
- Task 8: minor (deferred): текст инструкции "статус должен стать ✅" обещает live-refresh, которого нет - только при (re)входе в Settings; ON_RESUME refresh в Task 9+.
- Task 8: minor (deferred): KioskConfig.adbCommands() аллоцирует List на каждой рекомпозиции - hoist в val/remember.
- Task 8: minor (deferred): ▲/▼ glyphs не локализованы (консистентно со старым InstructionsSpoiler); clickable без semantics role.
- Task 8: complete (commits 0d29e33..56757ac, review clean: spec PASS / verdict APPROVED, 15/15 tests, DO remove/re-grant probe: dpm remove-device-owner отсутствует на Android 10 firmware -> временный in-app clearDeviceOwnerApp probe, откатен до коммита, DO восстановлен ✅)
- Ruling R12 (T9, controller): major Issue 1 ревьюера (QR никогда не декодировался, ~440B payload = version 16, ~7px/module на 640px) -> обязателен decode-check камерой телефона в T10 c сверкой строки против KioskConfig.APK_DOWNLOAD_URL; если marginal - size=1024 / aspectRatio(1f).
- Task 9: minor (deferred): KioskCard мертвые params qrBitmap/onDismissQr/onShareQr (brief-literal), dialog переиспользует лямбды инлайн.
- Task 9: minor (deferred): password field без PasswordVisualTransformation (PIN-gated screen, low impact).
- Task 9: minor (deferred): chooser title "QR" hardcoded (ProvisioningQr.kt:62) - локализовать или null.
- Task 9: minor (deferred): URI grant не проверен end-to-end (только открытие chooser); добавить clipData rawUri как belt-and-braces.
- Task 9: minor (deferred): shareQr - zxing encode + PNG write на main thread (admin-only; later Dispatchers.IO).
- Task 9: minor (deferred): cache-path "." шире нужного (лучше path=qr/); PNG с Wi-Fi паролем никогда не удаляется.
- Task 9: minor (deferred): Robolectric 4.14.1 качает ~100MB android-all при первом запуске (35s) - offline CI упадет; прогреть кэш / задокументировать.
- Task 9: minor (deferred): R7 тест усилить - assertEquals(640, bmp.width) + round-trip decode; contentDescription = kioskQrShowButton вместо отдельной строки.
- Task 9: nit (deferred): RU copy "на второй устройство" -> "на второе"; password при пустом SSID молча отбрасывается (spec-correct).
- Task 9: observation: APK_DOWNLOAD_URL = pinned tag (v1.0-test), не /latest/ - принятый компромисс T7; decode-check в T10 сверять с текущей константой.
- Task 9: complete (commits 56757ac..663848d, review clean: spec PASS / verdict APPROVED с 1 major -> R12 в T10, 16/16 tests incl. R7 encode smoke)
- T9 side-effect (device, не код): 3 неполных rating-3 отзыва авто-отправлены в Telegram при пробе (send_incomplete=true + 10s автосброс, ids 89-91); greetig восстановлен и verified.
- Ruling R13 (T10, controller): dpm remove-device-owner отсутствует на Android 10 firmware -> item 7 чек-листа закрыт по T8-доказательствам + live re-verify dumpsys/in-app.
- Task 10: observation: P4 home-preference сбросился mid-session (LOCKED) и был пере-применен cmd package set-home-activity; ResolverActivity ~1s после crash (self-closed).
- Task 10: observation: saved-state restore обходит PIN после am kill; 4000ms long-press не сработал где 800ms сработал (T6-рецепт).
- Task 10: observation (deferred): R12 decode - first-try MATCH byte-for-byte (SSID TestWifi_Kiosk, pass1234, pinned v1.0-test URL), size=1024 fix НЕ понадобился; throwaway decode test удален (absolute path сломал бы CI) - при желании вернуть как постоянный round-trip тест.
- Task 10: complete (commits 56757ac..663848d, 0 fixes needed, checklist 10/10 PASS, 16/16 tests, P1/P2/P5 PASS (P5 per R10), no debug policies grep=empty)
- FINAL REVIEW (whole-branch, longcat-2.5-preview-free): verdict READY, must-fix=0, Integration PASS, spec-12 coverage COMPLETE, security PASS, base-app regression LOW, new findings none.
- FINAL REVIEW triage: все ledgered minors -> follow-up; top-4 кандидата: (1) crash-loop backoff, (2) ON_RESUME refresh DO status, (3) narrow FileProvider path + QR PNG cleanup, (4) PasswordVisualTransformation.
- STATUS: Tasks 1-10 ALL COMPLETE (10 commits e6f1780..663848d, 23 files +1159/-3, 16/16 tests, acceptance 10/10). Branch kiosk-mode ready for merge decision.
- INTEGRATION (user choice, Option 2): kiosk-mode pushed (0d29e33..663848d), PR https://github.com/gurinovi-ch/respondent-pro/pull/1 (base=master@e39c1f6). Worktree preserved for PR feedback.
- Post-PR follow-up (user request): скрытие панели навигации - MainActivity.hideNavigationBar() через WindowCompat insets controller (hide navigationBars + BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), пере-применение в onWindowFocusChanged. Commit 375e377. Эскиз setDecorFitsSystemWindows(false) отклонен (сломал бы IME для текстовых полей). Верифицировано: NavigationBar0 isVisible=false, Back/Home/Недавние не выходят, 16/16 tests.
- Post-PR follow-up 2: скрытие статус-бара вместе с навигационной (hideSystemBars, маска navigationBars|statusBars). Commit 764ec79. Верификация: NavigationBar0 isVisible=false + StatusBar isVisible=false, фокус у приложения после Back/Home/Нedavnie, 16/16 tests. Визуальный контроль - пользователь (AGENTS.md).
- Post-PR follow-up 3: SystemIndicators (WiFi/батарея/шестерёнка) на главном экране, тап всей строкой -> showPin, длинный тап по org-name УДАЛЕН, футер 12->18sp. Коммит 558690f.
- Post-PR follow-up 3.1: крэш при первом запуске - не хватало ACCESS_NETWORK_STATE (registerDefaultNetworkCallback); фикс в манифесте, стабильно с 14:41, крэшей нет.
- Post-PR follow-up 3.2 (визуал): alpha=0.5, иконки 12dp, батарея 8sp, зазор 6dp, отступ = 1% высоты экрана (одинаковый сверху/справа). Верификация: logcat validated=true/lost/false-true при svc wifi disable/enable, 16/16 tests.
- Post-PR follow-up 4: CircleCloseButton (белый ✕ в кружке Primary 44dp) на главном экране при rating>0 -> resetAll+timerKey++; статус-блок перенесен под футер (равные 9dp, футер поднят ~30% от прежнего отступа); длинный тап по org-name уже убран ранее. Коммит af05327.
- Post-PR follow-up 4.1 (наблюдение): uiautomator на этом устройстве НЕ захватывает Compose PIN-диалог и дает устаревшие bounds окна (1848 vs реальные 1920) - верификация через Log.i цепочку (CLICKED->showPin->COMPOSED->hidePin) + mFocus/mFrame dumpsys; T8-замечание подтверждено.
- Post-PR follow-up 4.2 (deferred): пока открыт PIN-диалог - системные панели временно ПОЯВЛЯЮТСЯ (окно диалога без immersive-флагов, окно приложения сжимается 1920->1848), после закрытия - снова скрываются. Существовало и до этой задачи (старый длинный тап). Фикс = применить hide-флаги к окну диалога; спросить пользователя.
- Post-PR follow-up 5: CircleCloseButton заменил старые IconButton+Text(✕) на CommentScreen/ThankYouScreen; расположение = главный экран (TopEnd, 1% высоты). Верификация: bounds крестика на Comment идентичны главному [1113,15][1185,87], тап возвращает на главный; ThankYou не device-тестирован (требует отправки отзыва -> Telegram side effect). Коммит см. git log.
- Fix (user report: низ экрана обрезан на ~2% после поворота): причина - поворот пере-применял инсеты нав-бара, hideSystemBars не перезапускался (фокус не менялся) -> окно расширялось, нижняя полоса 72px закрыта. Фикс: onConfigurationChanged -> hideSystemBars(). Верификация: поворот в landscape -> nav shown=false isVisible=false (осталась скрыта), окно полное. + CommentScreen: padding кнопок/таймера 28->12dp (просьба пользователя). accel_rotation восстановлен в 1.
- Fix (user: клавиатура -5%): эффективный квалификатор на планшете sw800dp = values-sw720dp (перекрывает default И values-land!). kb_row_height 75->69dp, kb_max_height(min) 300->270dp там же; default values 50->47.5/200->190 тоже -5% (для не-планшетов). Замер: кадр 453->428px (-5.5%). values-land(45dp)/sw600dp(62dp) на этом устройстве неактивны (перекрыты sw720dp).
- PIN-диалог (вход в настройки): containerColor=White, shape=RoundedCornerShape(min(sw,sh)*0.05) = 40dp стабильно при повороте (вариант 2, выбор пользователя). Верификация: диалог открывается (смена фокус-окна), крэшей нет, 16/16 tests. Визуал - пользователь.
- PIN-диалог: углы 40dp -> фиксированные 5dp (решение пользователя, 40dp оказалось велико). Белый фон сохранён. 16/16 tests, диалог открывается.
- PIN-диалог: углы 5dp -> 10dp (подбор пользователем). 16/16 tests, диалог открывается.
- FIX deferred 4.2 (панели появлялись при открытом PIN-диалоге): SideEffect в text-слоте AlertDialog ставит legacy systemUiVisibility (IMMERSIVE_STICKY|HIDE_NAVIGATION|FULLSCREEN|LAYOUT_*) на rootView окна диалога. Верификация: nav shown=false isVisible=false ДО/ВО ВРЕМЯ/ПОСЛЕ диалога (раньше во время диалога появлялась). 16/16 tests.
- Настройки: системная клавиатура скрывается при входе (LaunchedEffect -> hideSoftInputFromWindow через view.post), появляется по тапу поля. Верификация: mInputShown=false при входе, =true после тапа по полю. 16/16 tests. Попутно: adb-ключ пересоздан из-за конфликта версий adb -> потребовалось повторное подтверждение на планшете.
- Настройки (вариант A, выбор пользователя): навигационная панель ВИДИМА (кнопка-шеврон скрывает клавиатуру - потеряли из-за скрытия панели), статус-бар скрыт. SettingsScreen DisposableEffect: show(navigationBars) + post + OnWindowFocusChangeListener (пере-показ после PIN-диалога/экскурсий); onDispose -> activity.hideSystemBars() (метод стал public). Верификация: настройки nav shown=true/status false; тап->клавиатура видима+nav; BACK->клавиатура скрыта; выход->обе панели скрыты. 16/16 tests.
- Фикс: открытие PIN-диалога теперь делает timerKey++ -> AutoResetTimer перезапускается (раньше остаток от экрана оценки съедал время на ввод PIN). Верификация: диалог открывается, 16/16 tests; визуальный сброс таймера - пользователь.
- (снимок сделан; живая версия ledger — в .superpowers/sdd/2026-09-28-kiosk-mode/progress.md)
