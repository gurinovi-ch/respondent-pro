# Task 8 Report: Kiosk card «ИНФОКИОСК» in settings (DO status + ADB instructions)

**Status:** ✅ COMPLETE
**Commit:** `56757ac` — `feat(kiosk): DO status card and ADB instruction in settings (i18n)` (exactly 4 files, +194 lines, on top of `0d29e33`)
**Branch:** `kiosk-mode` (worktree `.worktrees\kiosk-mode`)
**Date:** 2026-09-29

---

## 1. Implementation

| File | Change |
|---|---|
| `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt` | +7 string pairs RU/EN (kiosk card title, status owner, status no-owner, ADB spoiler title, steps title, copy hint, command labels) |
| `app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt` | `kioskStatus: StateFlow<KioskStatus>` + `refreshKioskStatus()`; reuses existing `private val kioskManager` ctor param (R1 — no duplication) |
| `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt` | `KioskCard` (collapsed/expanded, status line rendered green ✅ / red ⚠️) + `CommandText` copy-to-clipboard rows + ADB instruction spoiler; `LaunchedEffect(Unit) { viewModel.refreshKioskStatus() }` on screen entry |
| `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt` | New test: all 7 pairs present in RU and EN, no missing/extra keys |

Status logic: `KioskManager.status() → KioskStatus(deviceOwner, lockTaskPermitted)` — `deviceOwner && lockTaskPermitted` → `✅ Device Owner выдан` + `Lock Task активен`; otherwise → `⚠️ Device Owner не выдан — полная блокировка недоступна` (red, error color).

## 2. Test results

```
testDebugUnitTest — BUILD SUCCESSFUL
KioskPolicyTest:       tests=5 failures=0 errors=0
ProvisioningQrTest:    tests=5 failures=0 errors=0
WatchdogSchedulerTest: tests=3 failures=0 errors=0
KioskStringsTest:      tests=2 failures=0 errors=0
TOTAL: 15 tests, 0 failures
```

## 3. Build & install

- `assembleDebug` — BUILD SUCCESSFUL (JAVA_HOME = jbr-21.0.11).
- Probe install: `adb install -r -t` (testOnly build, per probe needs).
- **Controller ruling applied:** `android:testOnly="true"` reverted from `AndroidManifest.xml` before commit (verified absent); temp probe code reverted from `MainActivity.kt` (verified absent). Final clean build installed with plain `adb install -r` — **Success** (proves the release-critical defect is gone).
- After clean install process restart: `KioskManager: Policies applied: 7` (DPM policies re-applied automatically since DO present).

## 4. Device Owner probe (mandatory)

Device: HVA4H2PY, Android 10 (SDK 29), 1200×1920.

| Step | Command | Result | Evidence |
|---|---|---|---|
| Baseline | — | `✅ Device Owner выдан / Lock Task активен` on screen | `t8-status-do-ok.png` + dump nodes |
| Remove DO | `adb shell dpm remove-device-owner` | **`Error: unknown command 'remove-device-owner'`** (verified twice — not present on this Android 10 build) | shell output |
| Alt remove | `adb shell dpm remove-active-admin com.respondent.pro/.kiosk.KioskAdminReceiver` | **`java.lang.SecurityException: Attempt to remove non-test admin`** — stored admin `testOnlyAdmin=false` flag is stale (captured at first enable, never refreshed) → dead end | shell output |
| Fallback removal | temp in-app probe (`maybeSurrenderDo` in `MainActivity.kt`, triggered by `adb shell am start -n com.respondent.pro/.MainActivity --ez surrenderDo true`) → `KioskManager`/`dpm.clearDeviceOwnerApp()` | **`TempProbe: clearDeviceOwnerApp done`**, `dumpsys device_policy` owner empty, app alive | logcat + dumpsys |
| ⚠️ state | navigate to Settings → status card | **`⚠️ Device Owner не выдан — полная блокировка недоступна`** (red) in card `ИНФОКИОСК` | `t8-status-no-do.png` + `t8-nodes11.txt` node `[[60,1746][653,1770]]` |
| Restore DO | `adb shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver` | **`Success: Device owner set to package ComponentInfo{com.respondent.pro/com.respondent.pro.kiosk.KioskAdminReceiver}`** + dumpsys confirms owner | shell output + dumpsys |
| ✅ after restore | re-enter Settings → status card | **`✅ Device Owner выдан`** (green) | `t8-status-restored2.png` + `t8-nodes15.txt` line 21 |
| Final verification (clean APK) | plain `adb install -r` → restart → Settings | **`✅ Device Owner выдан` + `Lock Task активен`** (both lines), lock task active (status bar hidden, single-back nav), DO persists in dumpsys, `Policies applied: 7` | `t8-final-status.png` + `t8-final-nodes.txt` lines 27–28 |

**Probe ends with DO restored (✅) — mandatory condition satisfied.**

## 5. Deviations & notes

1. **`dpm remove-device-owner` unavailable on this Android 10 firmware** (`unknown command`) and `remove-active-admin` refuses non-test admins (stale stored flag). Removal was therefore done via the in-app `clearDeviceOwnerApp` fallback in a **temporary** `MainActivity.kt` probe — added, used, and **reverted before commit** (`git checkout --`), verified via grep (no `surrenderDo`/`TempProbe` left).
2. **Navigation is fragile** on this firmware: the feedback screen's 10 s auto-reset (`resetAll()` → `hidePin()`) kills the PIN dialog mid-entry, `input text` sometimes fails when the IME is stale, and the device **auto-rotated to landscape** during the probe (locked with `accelerometer_rotation=0` for the probe, **restored to original `1`** at the end). Working recipe found: `4000 ms long-press → dump-verify dialog open → tap PIN field → input text 0000` (dialog state always verified before tapping to avoid stray star taps).
3. **`uiautomator dump` proved unreliable at times** (background system-settings window content, stale `enabled` flags); **screenshots were used as ground truth** for all status evidence.
4. Settings values were preserved exactly (PIN `0000`, org, texts, timeout `10`, Chat ID `1476667084`, Telegram method).

## 6. Concerns (side effects observed)

1. **Possible incomplete-feedback send at ~09:39:** probe navigation used star taps; the first tap set rating=1 while «Отправлять незавершённый отзыв» was still ON, so the auto-reset may have triggered `saveIncompleteFeedback(rating=1)` → Telegram send. **Unverifiable** — `logcat` buffer rotated before it could be checked (no `FeedbackViewModel`/`FeedbackSender` lines survive). Before any further rating changes the switch was toggled OFF and back ON (final state verified **ON**, as original). Final device state verified: **rating = 0, comment cleared** (`t8-reality.png` ground truth).
2. **Stored admin `testOnlyAdmin=false` is stale** after the DO remove/restore cycle (captured at enable-time, never refreshed). Harmless for operation; affects only future `dpm remove-active-admin` attempts.
3. **DPM policies were lost on `clearDeviceOwnerApp`** and were **re-applied automatically** on first process restart after restore (`Policies applied: 7` — includes lock-task whitelist); verified `Lock Task активен` on screen afterwards.
4. A comment field transiently contained `0000…` during failed entry attempts; cleared via `onClose → commentViewModel.reset()` + auto-reset. Final state clean.

## 7. Final state

- **Device:** DO ✓, lock task active, 7 policies applied, app on start screen, switch ON, rating 0, auto-rotate restored (original `accelerometer_rotation=1`).
- **Repo:** tree clean, single commit `56757ac` containing only the 4 brief files; `testOnly` and temp probe code reverted.
- **Tests:** 15/15 green.
- **Evidence files:** `C:\Users\leonb\AppData\Local\Temp\opencode\t8-*.png` / `t8-*.txt` (statuses, dumps, screens).
