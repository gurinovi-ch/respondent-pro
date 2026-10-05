### Task 9: Генератор QR — ZXing, FileProvider, инструкция QR

**Files:**
- Modify: `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt`
- Modify: `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt`

**Interfaces:**
- Consumes: `ProvisioningQr.buildPayload/encodeQr` (Task 2), `KioskConfig.APK_DOWNLOAD_URL` (Task 7), `KioskCard` и состояния из Task 8.
- Produces: строки `kioskQrSpoiler`, `kioskQrSteps`, `kioskQrSsidLabel`, `kioskQrPasswordLabel`, `kioskQrShowButton`, `kioskQrShareButton` (ru+en); FileProvider-авторитет `${applicationId}.fileprovider`; логика `shareQr(context, payload): Boolean` внутри `ProvisioningQr`.

- [ ] **Step 1: Добавить строки QR — в `AppStrings.kt`**

В класс `AppStrings` (после `kioskCmdHint`):

```kotlin
    val kioskQrSpoiler: String,
    val kioskQrSteps: String,
    val kioskQrSsidLabel: String,
    val kioskQrPasswordLabel: String,
    val kioskQrShowButton: String,
    val kioskQrShareButton: String,
```

В `ruStrings`:

```kotlin
    kioskQrSpoiler = "Настройка через QR-код (без ПК)",
    kioskQrSteps = "1. Введите Wi-Fi (или оставьте пустым — сеть выберут в мастере) и нажмите «Показать QR-код».\n" +
        "2. Сохраните/отправите QR изображение на второй устройство (телефон).\n" +
        "3. Сбросьте планшет до заводского настроек.\n" +
        "4. На приветственном экране: Android 7–9 — 6 касаний по экрану; Android 10+ — иконка доступности → камера.\n" +
        "5. Наведите камеру на QR: планшет скачает APK, станет Device Owner и подключится к Wi-Fi.\n" +
        "6. После запуска: выберите лаунчер (один раз) и выдайте разрешение «Поверх других окон».",
    kioskQrSsidLabel = "Wi-Fi сеть (SSID)",
    kioskQrPasswordLabel = "Пароль Wi-Fi",
    kioskQrShowButton = "Показать QR-код",
    kioskQrShareButton = "Поделиться",
```

В `enStrings`:

```kotlin
    kioskQrSpoiler = "Setup via QR code (no PC)",
    kioskQrSteps = "1. Enter Wi-Fi (or leave empty — network is chosen in the wizard) and tap \"Show QR code\".\n" +
        "2. Save/share the QR image to another device (phone).\n" +
        "3. Factory-reset the tablet.\n" +
        "4. On the welcome screen: Android 7–9 — tap the screen 6 times; Android 10+ — accessibility icon → camera.\n" +
        "5. Point the camera at the QR: the tablet downloads the APK, becomes Device Owner and connects to Wi-Fi.\n" +
        "6. On first launch: choose the launcher (once) and grant \"Display over other apps\".",
    kioskQrSsidLabel = "Wi-Fi network (SSID)",
    kioskQrPasswordLabel = "Wi-Fi password",
    kioskQrShowButton = "Show QR code",
    kioskQrShareButton = "Share",
```

- [ ] **Step 2: Расширить тест строк — упадёт**

В `KioskStringsTest.kt` в обе функции добавить в `kioskValues`:

```kotlin
        s.kioskQrSpoiler, s.kioskQrSteps, s.kioskQrSsidLabel,
        s.kioskQrPasswordLabel, s.kioskQrShowButton, s.kioskQrShareButton
```

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: FAIL — `unresolved reference: kioskQrSpoiler`.

- [ ] **Step 3: FileProvider — `file_paths.xml` и манифест**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="qr_cache" path="." />
</paths>
```

В `AndroidManifest.xml` внутрь `<application>` (после receiver'ов):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Логика сохранения/отправки QR — в `ProvisioningQr.kt`**

Добавить импорты:

```kotlin
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
```

Добавить в `object ProvisioningQr`:

```kotlin
    /** Сохраняет QR в cacheDir и открывает диалог отправки. false — при ошибке. */
    fun shareQr(context: Context, payload: String): Boolean {
        val bitmap = encodeQr(payload) ?: return false
        return try {
            val file = File(context.cacheDir, "provisioning_qr.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "QR"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "shareQr failed", e)
            false
        }
    }
```

- [ ] **Step 5: QR-секция в `KioskCard` (`SettingsScreen.kt`)**

Состояния — добавить к тем, что в `SettingsScreen` из Task 8:

```kotlin
    var qrExpanded by remember { mutableStateOf(false) }
    var qrSsid by remember { mutableStateOf("") }
    var qrPassword by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
```

Вызов — расширить вызов `KioskCard` (Task 8) новыми параметрами:

```kotlin
            KioskCard(
                status = kioskStatus,
                expanded = kioskExpanded,
                onToggle = { kioskExpanded = !kioskExpanded },
                adbExpanded = adbExpanded,
                onToggleAdb = { adbExpanded = !adbExpanded },
                qrExpanded = qrExpanded,
                onToggleQr = { qrExpanded = !qrExpanded },
                qrSsid = qrSsid,
                onSsidChange = { qrSsid = it },
                qrPassword = qrPassword,
                onPasswordChange = { qrPassword = it },
                onShowQr = {
                    val payload = ProvisioningQr.buildPayload(
                        KioskConfig.APK_DOWNLOAD_URL,
                        qrSsid.ifBlank { null },
                        qrPassword.ifBlank { null }
                    )
                    qrBitmap = ProvisioningQr.encodeQr(payload)
                },
                qrBitmap = qrBitmap,
                onDismissQr = { qrBitmap = null },
                onShareQr = {
                    val payload = ProvisioningQr.buildPayload(
                        KioskConfig.APK_DOWNLOAD_URL,
                        qrSsid.ifBlank { null },
                        qrPassword.ifBlank { null }
                    )
                    ProvisioningQr.shareQr(context, payload)
                }
            )
```

Расширить сигнатуру `KioskCard` (Task 8) параметрами:

```kotlin
    qrExpanded: Boolean,
    onToggleQr: () -> Unit,
    qrSsid: String,
    onSsidChange: (String) -> Unit,
    qrPassword: String,
    onPasswordChange: (String) -> Unit,
    onShowQr: () -> Unit,
    qrBitmap: android.graphics.Bitmap?,
    onDismissQr: () -> Unit,
    onShareQr: () -> Unit
```

Внутрь `KioskCard`, **после** блока ADB (после закрывающей `}` внутреннего `AnimatedVisibility(adbExpanded)`), добавить QR-секцию:

```kotlin
                    // ▶ Настройка через QR-код
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleQr() },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.kioskQrSpoiler,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(text = if (qrExpanded) "▲" else "▼")
                    }

                    AnimatedVisibility(visible = qrExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = strings.kioskQrSteps,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = qrSsid,
                                onValueChange = onSsidChange,
                                label = { Text(strings.kioskQrSsidLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = qrPassword,
                                onValueChange = onPasswordChange,
                                label = { Text(strings.kioskQrPasswordLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = onShowQr,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(strings.kioskQrShowButton)
                            }
                        }
                    }
```

Диалог с изображением — **вне** `KioskCard`, в теле `SettingsScreen` рядом с вызовом карточки (у `qrBitmap` состояние на уровне экрана):

```kotlin
            qrBitmap?.let { bmp ->
                AlertDialog(
                    onDismissRequest = onDismissQrBitmap,   // = { qrBitmap = null }
                    title = { Text(strings.kioskQrShowButton) },
                    content = {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = strings.kioskQrShowButton,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { shareQrForCurrentPayload() }) {
                            Text(strings.kioskQrShareButton)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { qrBitmap = null }) {
                            Text(strings.btnCancel)
                        }
                    }
                )
            }
```

`shareQrForCurrentPayload()` — локальная лямбда, повторяющая сборку payload (как `onShareQr` из вызова `KioskCard`); `strings` — уже объявленный в функции `LocalAppStrings.current`. Импорты: `androidx.compose.foundation.Image`, `androidx.compose.ui.graphics.asImageBitmap`, `androidx.compose.material3.AlertDialog`, `androidx.compose.material3.TextButton`, `androidx.compose.material3.OutlinedTextField`, `com.respondent.pro.kiosk.ProvisioningQr`.

Примечание: `strings.btnCancel` уже существует в `AppStrings`.

- [ ] **Step 6: Прогнать тест строк — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: PASS (2 теста, все QR-строки непустые в ru и en).

- [ ] **Step 7: Собрать, установить, проверка на устройстве**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
На планшете: Настройки (PIN `0000`) → «ИНФОКИОСК» → «Настройка через QR-код» → ввести тестовые SSID/пароль → «Показать QR-код» → диалог с QR → «Поделиться» → открывается шеринг-шторка (сохранить/отправить).
Expected: QR отображается, шеринг работает, кнопка «Отмена» закрывает диалог.

Дополнительно (сверка payload с юнит-тестом): QR содержит URL `…/releases/latest/download/RESPONDENT.PRO.apk` — визуально распознать камерой телефона и сверить строку.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt app/src/main/java/com/respondent/pro/kiosk/ProvisioningQr.kt app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(kiosk): QR generator UI with share via FileProvider"
```

---

