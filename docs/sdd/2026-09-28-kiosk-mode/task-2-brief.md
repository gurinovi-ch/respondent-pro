### Task 2: ProvisioningQr — payload QR-провижининга (TDD)

**Files:**
- Create: `app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt`
- Test: `app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt`

**Interfaces:**
- Consumes: Gson (`com.google.code.gson`, уже в зависимостях).
- Produces: `object ProvisioningQr { const val ADMIN_COMPONENT: String; fun buildPayload(apkUrl: String, wifiSsid: String?, wifiPassword: String?): String }` — `buildPayload` используют Task 9 (UI-генератор); `ADMIN_COMPONENT` также в `KioskConfig` (Task 7). Кодирование QR (`encodeQr`) добавляется в Task 9.

- [ ] **Step 1: Написать падающий тест**

`app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt`:

```kotlin
package com.respondent.pro.kiosk

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningQrTest {

    private val url = "https://example.com/releases/latest/download/RESPONDENT.PRO.apk"

    @Test
    fun `payload contains admin component, download url and skip encryption`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, null)).asJsonObject
        assertEquals(
            "com.respondent.pro/.kiosk.KioskAdminReceiver",
            json["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"].asString
        )
        assertEquals(url, json["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"].asString)
        assertTrue(json["android.app.extra.PROVISIONING_SKIP_ENCRYPTION"].asBoolean)
    }

    @Test
    fun `wifi keys omitted when ssid is null or blank`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, null, "pass")).asJsonObject
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_SSID"))
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))

        val jsonBlank = JsonParser.parseString(ProvisioningQr.buildPayload(url, "  ", null)).asJsonObject
        assertFalse(jsonBlank.has("android.app.extra.PROVISIONING_WIFI_SSID"))
    }

    @Test
    fun `wifi keys present when ssid provided`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "secret1")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertEquals("secret1", json["android.app.extra.PROVISIONING_WIFI_PASSWORD"].asString)
    }

    @Test
    fun `password omitted when ssid given but password empty`() {
        val json = JsonParser.parseString(ProvisioningQr.buildPayload(url, "MyNet", "")).asJsonObject
        assertEquals("MyNet", json["android.app.extra.PROVISIONING_WIFI_SSID"].asString)
        assertFalse(json.has("android.app.extra.PROVISIONING_WIFI_PASSWORD"))
    }

    @Test
    fun `payload is valid json object`() {
        val parsed = JsonParser.parseString(ProvisioningQr.buildPayload(url, "Net", "pw"))
        assertTrue(parsed.isJsonObject)
    }
}
```

- [ ] **Step 2: Запустить — тест должен упасть**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Expected: FAIL — `unresolved reference: ProvisioningQr`.

- [ ] **Step 3: Реализовать `ProvisioningQr.kt`**

```kotlin
package com.respondent.pro.kiosk

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Payload для Android Enterprise QR-провижининга.
 * Формат ключей: android.app.extra.PROVISIONING_* (spec §8).
 */
object ProvisioningQr {

    private const val TAG = "ProvisioningQr"

    const val ADMIN_COMPONENT = "com.respondent.pro/.kiosk.KioskAdminReceiver"

    fun buildPayload(apkUrl: String, wifiSsid: String?, wifiPassword: String?): String {
        val root = JsonObject()
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", ADMIN_COMPONENT)
        root.addProperty("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", apkUrl)
        if (!wifiSsid.isNullOrBlank()) {
            root.addProperty("android.app.extra.PROVISIONING_WIFI_SSID", wifiSsid)
            if (!wifiPassword.isNullOrEmpty()) {
                root.addProperty("android.app.extra.PROVISIONING_WIFI_PASSWORD", wifiPassword)
            }
        }
        root.addProperty("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
        return root.toString()
    }

    /** Кодирует payload в QR-изображение. Возвращает null при ошибке кодирования. */
    fun encodeQr(payload: String, size: Int = 640): Bitmap? = try {
        BarcodeEncoder().encodeAsBitmap(payload, BarcodeFormat.QR_CODE, size, size)
    } catch (e: Exception) {
        Log.e(TAG, "QR encode failed", e)
        null
    }
}
```

- [ ] **Step 4: Добавить зависимость ZXing в `app/build.gradle.kts`**

В `dependencies { … }` после строки `implementation("com.sun.mail:android-activation:1.6.7")`:

```kotlin
    // QR-код для провижининга
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
```

(`encodeQr` использует `BarcodeEncoder`; тест payload от ZXing не зависит, но компиляция класса требует зависимость.)

- [ ] **Step 5: Запустить тест — должен пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*ProvisioningQrTest*"
```
Expected: `BUILD SUCCESSFUL`, 5 тестов PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt app/src/test/java/com/respondent/pro/kiosk/ProvisioningQrTest.kt app/build.gradle.kts
git commit -m "feat(kiosk): provisioning QR payload (TDD) + zxing"
```

---

