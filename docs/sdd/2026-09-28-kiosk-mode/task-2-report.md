# Task 2 Report: ProvisioningQr — payload QR-провижининга (TDD)

**Status:** DONE_WITH_CONCERNS (minor: one-line deviation from the brief's snippet, see Concerns)
**Commit:** `7418b5c` — feat(kiosk): provisioning QR payload (TDD) + zxing
**Branch:** kiosk-mode (worktree: `C:\projects\feedback-app\.worktrees\kiosk-mode`)

## What was implemented

- `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt` — `object ProvisioningQr` with:
  - `const val ADMIN_COMPONENT = "com.respondent.pro/.kiosk.KioskAdminReceiver"` (literal matches Task 1's `AndroidManifest.xml` receiver `.kiosk.KioskAdminReceiver` + applicationId `com.respondent.pro`; verified by grep, and asserted verbatim by the test);
  - `fun buildPayload(apkUrl, wifiSsid?, wifiPassword?): String` — Gson `JsonObject` with `android.app.extra.PROVISIONING_*` keys; wifi keys only when SSID non-blank, password only when non-empty; `PROVISIONING_SKIP_ENCRYPTION = true`;
  - `fun encodeQr(payload, size = 640): Bitmap?` — ZXing `BarcodeEncoder`, logs and returns null on failure (per brief Step 3; **not** unit-tested by design — requires Android `Bitmap`).
- `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt` — the brief's 5 tests, verbatim (Step 1).
- `app/build.gradle.kts` — `implementation("com.journeyapps:zxing-android-embedded:4.3.0")` + comment, inserted after `com.sun.mail:android-activation:1.6.7` (Step 4).

## TDD Evidence

### RED (Step 2)

Command:
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Output (exit code 1):
```
> Task :app:compileDebugUnitTestKotlin FAILED
e: .../ProvisioningQrTest.kt:15:43 Unresolved reference 'ProvisioningQr'.
e: .../ProvisioningQrTest.kt:26:43 Unresolved reference 'ProvisioningQr'.
e: .../ProvisioningQrTest.kt:30:48 Unresolved reference 'ProvisioningQr'.
e: .../ProvisioningQrTest.kt:36:43 Unresolved reference 'ProvisioningQr'.
e: .../ProvisioningQrTest.kt:43:43 Unresolved reference 'ProvisioningQr'.
e: .../ProvisioningQrTest.kt:50:45 Unresolved reference 'ProvisioningQr'.
FAILURE: Build failed with an exception. ... BUILD FAILED
```
Why expected: test written first against a not-yet-existing class → exactly the brief's expected `unresolved reference: ProvisioningQr`.

### GREEN (Steps 3–5)

First GREEN attempt (brief's snippet verbatim) failed on compilation:
```
> Task :app:compileDebugKotlin FAILED
e: .../ProvisioningQr.kt:35:26 Unresolved reference 'encodeAsBitmap'.
```
Investigation: resolved the dependency (`:app:dependencies --configuration debugCompileClasspath` shows `com.journeyapps:zxing-android-embedded:4.3.0 → com.google.zxing:core:3.4.1`), then ran `javap -p` on `classes.jar` inside the cached 4.3.0 AAR. The actual API is `encodeBitmap(String, BarcodeFormat, int, int)`; **`encodeAsBitmap` does not exist** in 4.3.0 (only one `BarcodeEncoder.class` in the jar; method absent even with `-p`). The brief's snippet has an API-name error against the pinned version. Changed that one call to `encodeBitmap(...)` — same signature/semantics (BarcodeEncoder, QR_CODE, WxH → `Bitmap?`, `WriterException` caught by `catch (e: Exception)` → null + `Log.e`).

Then:
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Output: `BUILD SUCCESSFUL in 13s`. Test XML evidence: `com.respondent.pro.kiosk.ProvisioningQrTest: tests=5 failures=0 errors=0 skipped=0`.

### Pre-commit verification

- Full suite: `$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest` → `BUILD SUCCESSFUL` (project contains only this test class; 5/5 pass).
- Build: `.\gradlew.bat assembleDebug` → `BUILD SUCCESSFUL` (APK packages ZXing AAR; no duplicate-class or manifest merger issues).

## Files changed (commit 7418b5c, 3 files, +96)

- `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt` (new)
- `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt` (new)
- `app/build.gradle.kts` (+3: comment + zxing-android-embedded:4.3.0)

## Self-review findings

- **Completeness:** all brief steps done; interface matches spec (`ADMIN_COMPONENT`, `buildPayload`); test file verbatim from brief; dependency pinned at brief's 4.3.0.
- **Quality:** `ADMIN_COMPONENT` consistent with manifest/Task 1 (verified); SSID/password gating logic covered by 4 distinct test cases; no dead code.
- **YAGNI:** no speculative fields/APIs beyond the brief; `encodeQr` is specified for Task 9 consumption, not invented here.
- **Tests verify real behavior:** tests parse actual `buildPayload` output as JSON and assert exact key presence/absence/values — no mocks, no tautologies.
- **Pristine output:** no UI strings, no DB/DataStore changes, `KioskAdminReceiver` untouched; build artifacts not tracked; working tree clean after commit (`git status --porcelain` empty).

## Concerns

1. **Brief snippet error (deviation):** brief Step 3 uses `BarcodeEncoder().encodeAsBitmap(...)`, which does not compile against zxing-android-embedded 4.3.0; actual method is `encodeBitmap(...)`. Verified empirically via `javap` on the downloaded AAR. One-line change; everything else verbatim. Later tasks reusing the brief's snippet (Task 9 UI-generator) will hit the same thing if the snippet is re-copied — worth fixing in the plan/brief.
2. `encodeQr` remains untested (intentional per task instructions — requires Android `Bitmap` runtime).
3. Environment caused no test failures; RED/GREEN transitions were purely code-driven.

---

# Fix Report — Round 1: camera permission surface injected by zxing AAR

**Status:** DONE
**Commit:** `5737cd7` — fix(kiosk): remove camera permission surface injected by zxing AAR (1 file, +10/−1)

## What changed

Only `app/src/main/AndroidManifest.xml` (per controller ruling R6 — dependency and `encodeQr` code untouched):

1. Added `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` root.
2. Added `tools:node="remove"` entries (with a comment explaining why) for the camera surface the zxing-android-embedded AAR injects.

## Camera entries found in the merged manifest (before fix)

From `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`:
- `<uses-permission android:name="android.permission.CAMERA" />`
- `<uses-feature android:name="android.hardware.camera" required="false" />`
- `<uses-feature android:name="android.hardware.camera.front" required="false" />`
- `<uses-feature android:name="android.hardware.camera.autofocus" required="false" />`
- `<uses-feature android:name="android.hardware.camera.flash" required="false" />`

**Removed (5 total):** permission `CAMERA` + features `camera`, `camera.front`, `camera.autofocus`, `camera.flash`.

**Not removed:** `android.hardware.camera.any` — it appears only inside an XML **comment** in the merged manifest (`<!-- <uses-feature .../> -->`), so it is not part of the declared surface; a `tools:node="remove"` for it would be dead config (YAGNI). Other non-camera `uses-feature` entries (`android.hardware.screen.landscape`, etc.) left untouched.

## Verification (commands + outputs)

1. Rebuild:
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
```
→ `BUILD SUCCESSFUL in 3s` (40 actionable tasks: 5 executed, 35 up-to-date); `:app:processDebugManifest` re-ran.

2. Removal check in regenerated merged manifest:
```powershell
Select-String -Path app\build\intermediates\merged_manifests\debug\processDebugManifest\AndroidManifest.xml -Pattern "android.permission.CAMERA|android.hardware.camera"
```
→ **no output (no matches)** — camera surface fully gone.

Sanity check (other entries intact), remaining in merged manifest:
```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="com.respondent.pro.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
<uses-feature ... />  (2 — non-camera: screen.landscape / screen.portrait)
```

3. Covering tests (full suite; first run was `UP-TO-DATE`, so re-ran fresh with `--rerun`):
```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --rerun
```
→ `BUILD SUCCESSFUL in 3s` (testDebugUnitTest executed) + XML evidence:
`com.respondent.pro.kiosk.ProvisioningQrTest: tests=5 failures=0 errors=0 skipped=0` — **5/5 green**.

## Concerns

None. Manifest-only change; unit tests (which don't read the manifest) stay green, APK assembles, and the merged manifest no longer declares any camera permission or feature.
