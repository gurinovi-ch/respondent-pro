### Task 8: Экран настроек — статус Device Owner + инструкция ADB

**Files:**
- Modify: `app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt`
- Modify: `app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt`
- Test: `app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt` (create)

**Interfaces:**
- Consumes: `KioskManager.status(): KioskStatus` (Task 3), `KioskConfig.adbCommands()/APK_DOWNLOAD_URL` (Task 7), `SettingsViewModel.kiosk` (Task 6).
- Produces: строки `kioskTitle`, `kioskStatusOwner`, `kioskStatusLock`, `kioskStatusNoOwner`, `kioskAdbSpoiler`, `kioskAdbSteps`, `kioskCmdHint` (ru+en) и состояние `SettingsViewModel.kioskStatus: StateFlow<KioskStatus?>` с `refreshKioskStatus()` — Task 9 добавит к `KioskCard` QR-часть, используя те же `kioskStatus`/раскрытие.

- [ ] **Step 1: Добавить строки в `AppStrings.kt`**

В класс `AppStrings` (после поля `instructionsTitle`):

```kotlin
    // Инфокиоск
    val kioskTitle: String,
    val kioskStatusOwner: String,
    val kioskStatusLock: String,
    val kioskStatusNoOwner: String,
    val kioskAdbSpoiler: String,
    val kioskAdbSteps: String,
    val kioskCmdHint: String,
```

В `ruStrings = AppStrings(…)` (в том же месте):

```kotlin
    kioskTitle = "ИНФОКИОСК",
    kioskStatusOwner = "✅ Device Owner выдан",
    kioskStatusLock = "Lock Task активен",
    kioskStatusNoOwner = "⚠️ Device Owner не выдан — полная блокировка недоступна",
    kioskAdbSpoiler = "Настройка через ADB (с ПК)",
    kioskAdbSteps = "1. Включите USB-отладку: Параметры → О телефоне → 7 касаний по «Номеру сборки».\n" +
        "2. Скачайте APK по ссылке ниже (нажатие копирует).\n" +
        "3. Подключите планшет к ПК и выполните команды по одной (нажатие копирует команду).\n" +
        "4. Вернитесь в приложение — статус должен стать ✅.",
    kioskCmdHint = "Нажмите на текст, чтобы скопировать",
```

В `enStrings = AppStrings(…)`:

```kotlin
    kioskTitle = "KIOSK MODE",
    kioskStatusOwner = "✅ Device Owner granted",
    kioskStatusLock = "Lock Task active",
    kioskStatusNoOwner = "⚠️ Device Owner not granted — full lockdown unavailable",
    kioskAdbSpoiler = "Setup via ADB (from PC)",
    kioskAdbSteps = "1. Enable USB debugging: Settings → About tablet → tap \"Build number\" 7 times.\n" +
        "2. Download the APK via the link below (tap to copy).\n" +
        "3. Connect the tablet to a PC and run the commands one by one (tap to copy).\n" +
        "4. Return to the app — status should become ✅.",
    kioskCmdHint = "Tap text to copy",
```

- [ ] **Step 2: Написать падающий тест локализации**

`app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt`:

```kotlin
package com.respondent.pro.ui.i18n

import org.junit.Assert.assertTrue
import org.junit.Test

class KioskStringsTest {

    private fun kioskValues(s: AppStrings) = listOf(
        s.kioskTitle, s.kioskStatusOwner, s.kioskStatusLock, s.kioskStatusNoOwner,
        s.kioskAdbSpoiler, s.kioskAdbSteps, s.kioskCmdHint
    )

    @Test
    fun `kiosk strings are present and non-blank in ru`() {
        assertTrue(kioskValues(ruStrings).all { it.isNotBlank() })
    }

    @Test
    fun `kiosk strings are present and non-blank in en`() {
        assertTrue(kioskValues(enStrings).all { it.isNotBlank() })
    }
}
```

- [ ] **Step 3: Запустить — упасть (строк ещё нет)**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: FAIL — компиляция: `unresolved reference: kioskTitle`.

- [ ] **Step 4: Статус в `SettingsViewModel.kt`**

Импорты:

```kotlin
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.KioskStatus
import kotlinx.coroutines.flow.MutableStateFlow
```
(`MutableStateFlow` уже импортирован — не дублировать.)

В конструктор добавить (после `telegramApi`):

```kotlin
    private val kioskManager: KioskManager
```

В тело класса (после `chatIdResult`):

```kotlin
    /** Статус инфокиоска — обновляется при каждом открытии экрана настроек */
    private val _kioskStatus = MutableStateFlow<KioskStatus?>(null)
    val kioskStatus: StateFlow<KioskStatus?> = _kioskStatus

    fun refreshKioskStatus() {
        _kioskStatus.value = kioskManager.status()
    }
```

- [ ] **Step 5: Прогнать тест — пройти**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat testDebugUnitTest --tests "*KioskStringsTest*"
```
Expected: PASS (2 теста).

- [ ] **Step 6: `KioskCard` в `SettingsScreen.kt`**

Состояния (рядом с `instructionsExpanded`, строка ~44):

```kotlin
    var kioskExpanded by remember { mutableStateOf(false) }
    var adbExpanded by remember { mutableStateOf(false) }
```

Статус (после существующих `val … by … collectAsState()`; если collectAsState для settings уже есть — добавить рядом):

```kotlin
    val kioskStatus by viewModel.kioskStatus.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshKioskStatus() }
```

Вставка в разметку — **сразу после `InstructionsSpoiler(...)` и перед `Spacer(modifier = Modifier.height(24.dp))`** (последний блок списка, spec §8):

```kotlin
            // 13. Инфокиоск — статус и инструкции
            KioskCard(
                status = kioskStatus,
                expanded = kioskExpanded,
                onToggle = { kioskExpanded = !kioskExpanded },
                adbExpanded = adbExpanded,
                onToggleAdb = { adbExpanded = !adbExpanded }
            )
```

Композабл (в конец файла, после `InstructionsSpoiler`):

```kotlin
@Composable
private fun KioskCard(
    status: KioskStatus?,
    expanded: Boolean,
    onToggle: () -> Unit,
    adbExpanded: Boolean,
    onToggleAdb: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.kioskTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Статус виден всегда, даже свёрнутым
            Text(
                text = when {
                    status == null -> ""
                    status.deviceOwner && status.lockTaskPermitted ->
                        "${strings.kioskStatusOwner}\n${strings.kioskStatusLock}"
                    status.deviceOwner -> strings.kioskStatusOwner
                    else -> strings.kioskStatusNoOwner
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status?.deviceOwner == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // ▶ Настройка через ADB
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleAdb() },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.kioskAdbSpoiler,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(text = if (adbExpanded) "▲" else "▼")
                    }

                    AnimatedVisibility(visible = adbExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = strings.kioskAdbSteps,
                                style = MaterialTheme.typography.bodySmall
                            )
                            // Ссылка на APK — копируется по нажатию
                            CommandText(text = KioskConfig.APK_DOWNLOAD_URL)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.kioskCmdHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            KioskConfig.adbCommands().forEach { cmd ->
                                CommandText(text = cmd)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandText(text: String) {
    val context = LocalContext.current
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("adb", text))
            }
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    )
}
```

Импорты (добавить недостающие — проверить по факту компиляции):

```kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.respondent.pro.kiosk.KioskConfig
import com.respondent.pro.kiosk.KioskStatus
```

(`LocalContext` — если не импортирован: `import androidx.compose.ui.platform.LocalContext`.)

- [ ] **Step 7: Собрать, установить, проверка статуса ✅**

```powershell
$env:JAVA_HOME = "C:\Users\leonb\.jdks\jbr-21.0.11"; .\gradlew.bat assembleDebug
C:\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
На планшете: долгое нажатие на название организации (3 сек) → PIN `0000` → внизу «ИНФОКИОСК» → статус зелёный `✅ Device Owner выдан / Lock Task активен` → раскрыть ADB → 4 команды видны, нажатие копирует (проверить: скопировать команду → вставить в любое поле).
Expected: статус ✅, команды копируются.

- [ ] **Step 8: Проверка статуса ⚠️ без DO (Review Focus №4)**

```powershell
C:\platform-tools\adb.exe shell dpm remove-device-owner
```
На планшете: выйти и заново открыть настройки (PIN `0000`) → статус `⚠️ Device Owner не выдан…` (красный), приложение НЕ упало.
Вернуть DO:
```powershell
C:\platform-tools\adb.exe shell dpm set-device-owner com.respondent.pro/.kiosk.KioskAdminReceiver
```
Expected: `Success: set device owner`; перезайти в настройки → снова ✅.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/respondent/pro/ui/i18n/AppStrings.kt app/src/test/java/com/respondent/pro/ui/i18n/KioskStringsTest.kt app/src/main/java/com/respondent/pro/viewmodel/SettingsViewModel.kt app/src/main/java/com/respondent/pro/ui/screens/SettingsScreen.kt
git commit -m "feat(kiosk): DO status card and ADB instruction in settings (i18n)"
```

---

