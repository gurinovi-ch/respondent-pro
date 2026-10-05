# Task 9 Report: QR provisioning generator (i18n + FileProvider share + settings UI)

**Status:** ✅ COMPLETE
**Commit:** `663848d` — `feat(kiosk): QR generator UI with share via FileProvider` (8 files, +221/−3, on top of `56757ac`)
**Branch:** `kiosk-mode` (worktree `.worktrees\kiosk-mode`)
**Date:** 2026-09-29

---

## 1. Implementation

| File | Change |
|---|---|
| `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt` | +6 string pairs RU/EN after `kioskCmdHint`: `kioskQrSpoiler`, `kioskQrSteps` (6-step instruction), `kioskQrSsidLabel`, `kioskQrPasswordLabel`, `kioskQrShowButton`, `kioskQrShareButton`. Cancel uses existing `btnCancel` — no hardcoded user-facing text in composables |
| `app/src/main/AndroidManifest.xml` | `FileProvider` only: `${applicationId}.fileprovider` + `meta-data @xml/file_paths`, added after the receivers. CAMERA `tools:node="remove"` untouched (R6), no `testOnly` |
| `app/src/main/res/xml/file_paths.xml` | **new** — `cache-path` for `provisioning_qr.png` |
| `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt` | `+shareQr(context, payload): Boolean` — `encodeQr` → PNG in `cacheDir/provisioning_qr.png` → `FileProvider.getUriForFile` → `ACTION_SEND` (`image/png`), returns false + `Log.e` on failure |
| `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt` | States `qrExpanded/qrSsid/qrPassword/qrBitmap`, `buildQrPayload()` lambda, `KioskCard` extended (QR spoiler: steps text, SSID/password `OutlinedTextField`s, «Показать QR-код» button), QR `AlertDialog` (title/image/`Поделиться`/`Отмена`) rendered when `qrBitmap != null` |
| `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt` | Extended: all 6 new pairs present in RU and EN, no missing/extra keys |
| `app/build.gradle.kts` | `testImplementation("org.robolectric:robolectric:4.14.1")` + `testOptions.unitTests.isIncludeAndroidResources = true` — needed for the mandatory R7 encode test (see Deviations) |
| `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrEncodeTest.kt` | **new** — R7 smoke test: `@RunWith(RobolectricTestRunner) @Config(sdk = [29])`, asserts `ProvisioningQr.encodeQr(payload) != null` and bitmap has expected size |

TDD order per brief: extended `KioskStringsTest` first → **RED** (`Unresolved reference 'kioskQrSpoiler'`) → added strings → **GREEN**, then implementation.

`SettingsViewModel` untouched (R1: `kioskManager` ctor param already existed — not re-added). Room DB and DataStore untouched in code.

## 2. Test results

```
testDebugUnitTest — BUILD SUCCESSFUL (fresh pre-commit run, JUnit XML parsed)
KioskPolicyTest:        tests=5 failures=0 errors=0
ProvisioningQrEncodeTest: tests=1 failures=0 errors=0   ← R7 smoke test: PASS
ProvisioningQrTest:     tests=5 failures=0 errors=0
WatchdogSchedulerTest:  tests=3 failures=0 errors=0
KioskStringsTest:       tests=2 failures=0 errors=0
TOTAL: 16 tests, 0 failures, 0 errors (baseline was 15)
```

**R7 verdict: PASS** — non-null bitmap asserted on JVM via Robolectric, and independently confirmed rendering on the real device (probe step 5, `t9-21.png`).

## 3. Build & install

- `assembleDebug` — BUILD SUCCESSFUL (`JAVA_HOME = jbr-21.0.11`).
- Merged manifest verified: `com.respondent.pro.fileprovider` + `@xml/file_paths` present; **no CAMERA permission** (R6 — `tools:node="remove"` kept); **no `android:testOnly`**.
- `adb install -r` (plain, no `-t`) → **Success**.

## 4. Device probe (step 7)

Device: HVA4H2PY, Android 10 (SDK 29), landscape 1920×1200 logical. All logs `Log.i`; no `force-stop`/`am start -S` used.

| Step | Action | Result | Evidence |
|---|---|---|---|
| 1 | Enter Settings (long-press + PIN `0000`) | Settings opened; greeting field incidentally corrupted (`input text 0000` landed there) → fixed: `MOVE_END` + 60×`DEL`, verified **`Давай уже действуй!`**, PIN **`0000`**, timeout **`10`**, switch «Отправлять незавершённый отзыв» **ON** | `t9-19.png`, dump `t9-ui21.xml` |
| 2 | Scroll → tap «ИНФОКИОСК» | Card expanded (`▲`), `✅ Device Owner выдан / Lock Task активен` | dump `t9-ui23.xml` |
| 3 | Tap «Настройка через QR-код (без ПК)» | Spoiler expanded: 6-step instructions + SSID/password fields + «Показать QR-код» button — all strings from `AppStrings` (RU) | `t9-20.png`, dumps `t9-ui24/25/26.xml` |
| 4 | Enter SSID `TestWifi_Kiosk`, password `pass1234` | Both fields hold values, keyboard closed cleanly | dumps `t9-ui27/28.xml` |
| 5 | Tap «Показать QR-код» | **Dialog «Показать QR-код» with a fully rendered QR** (ImageView 768×640, dense provisioning payload incl. Wi-Fi), buttons «Отмена»/«Поделиться» | **`t9-21.png`**, dump `t9-ui29.xml` |
| 6 | Tap «Поделиться» | **System share sheet «Отправка данных»** (Files, Диск, Сообщения, …) — FileProvider URI + `ACTION_SEND` works | **`t9-22.png`**, dump `t9-ui30.xml` |
| 7 | `keyevent 4` → «Отмена» → «Старт» | Share sheet dismissed, dialog closed, back to start screen; greeting intact, **rating = 0** (no timer, nothing to send) | `t9-23.png`, dump `t9-ui32.xml` |

## 5. Deviations & notes

1. **AlertDialog slot:** brief's `AlertDialog(content = { … })` does not exist on the material3 overload taking `confirmButton` — minimal fix: renamed the slot to `text = { … }` (`SettingsScreen.kt:536`). Behavior identical.
2. **Files not in the brief's commit list:** `app/build.gradle.kts` (Robolectric 4.14.1 + `isIncludeAndroidResources`) and the new `ProvisioningQrEncodeTest.kt` were **required to make the mandatory R7 `encodeQr` smoke test runnable** (no Android framework on plain JVM). Both included in the single commit.
3. **Brief-literal `KioskCard` signature:** `qrBitmap`/`onDismissQr`/`onShareQr` params are passed into `KioskCard` but unused inside it — the QR image + `Поделиться` live in the `AlertDialog` **outside** the card (as the brief's own code does). No Share button was added to the spoiler body.
4. Robolectric test pinned to `@Config(sdk = [29])` to mirror the probe device.
5. `shareQr(context, payload)` encodes internally (returns `Boolean`) instead of taking a pre-encoded bitmap — matches the two call sites (dialog re-shares via `buildQrPayload()`).

## 6. Side effects observed (honest accounting)

1. **Three incomplete rating-3 feedbacks were auto-sent to Telegram during this probe session** (before the final clean run): DB rows **ids 89, 90, 91** at **11:29:44, 11:34:18, 11:50:46** local, all `sentToTelegram=1`, `errorMessage=null`. Cause: `send_incomplete=true` + probe navigation used star taps, and the 10 s auto-reset fired. Forensics done on the pulled DB (`t9-app*.db`) + preferences (`t9-settings.pb`); original greeting recovered from DataStore the same way.
2. **Greeting corruption** (step 1 above) was fully restored and screenshot/dump-verified; all other settings (PIN, timeout, switch) verified unchanged.
3. Hazard noted for future probes: a blind tap at (960,590) on the start screen selects star #3 → auto-send; every probe step this run was dump-verified before tapping.

## 7. Final state

- **Device:** on start screen, rating 0, greeting `Давай уже действуй!`, PIN `0000`, timeout 10, send-incomplete ON — no pending timers/sends; DO + lock task untouched by this task.
- **Repo:** tree clean, single commit `663848d` with exactly the 8 implementation files (brief's 6 + `build.gradle.kts` + `ProvisioningQrEncodeTest.kt`).
- **Tests:** 16/16 green (R7 encode smoke test included).
- **Evidence files:** `C:\Users\leonb\AppData\Local\Temp\opencode\t9-20.png` … `t9-23.png` (spoiler, QR dialog, share sheet, final screen), `t9-ui20.xml` … `t9-ui32.xml` (UI dumps), `t9-settings.pb` / `t9-app*.db` (forensics).
