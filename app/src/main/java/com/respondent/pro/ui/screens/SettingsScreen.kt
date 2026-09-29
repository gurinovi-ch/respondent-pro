package com.respondent.pro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.kiosk.KioskConfig
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.KioskStatus
import com.respondent.pro.kiosk.ProvisioningQr
import com.respondent.pro.kiosk.SettingsExcursionOverlay
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.viewmodel.ChatIdResult
import com.respondent.pro.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onStart: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var localSettings by remember(settings.copy(language = "")) { mutableStateOf(settings) }
    val context = LocalContext.current
    val kioskManager = viewModel.kiosk
    val strings = LocalAppStrings.current
    var langExpanded by remember { mutableStateOf(false) }
    var sendMethodExpanded by remember { mutableStateOf(false) }
    var emailPresetExpanded by remember { mutableStateOf(false) }

    val chatIdResult by viewModel.chatIdResult.collectAsState()
    val isDetectingChatId by viewModel.isDetectingChatId.collectAsState()

    var instructionsExpanded by remember { mutableStateOf(false) }
    var kioskExpanded by remember { mutableStateOf(false) }
    var adbExpanded by remember { mutableStateOf(false) }
    var qrExpanded by remember { mutableStateOf(false) }
    var qrSsid by remember { mutableStateOf("") }
    var qrPassword by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Payload QR-провижининга собирается одинаково для показа и отправки
    val buildQrPayload = {
        ProvisioningQr.buildPayload(
            KioskConfig.APK_DOWNLOAD_URL,
            qrSsid.ifBlank { null },
            qrPassword.ifBlank { null }
        )
    }

    val kioskStatus by viewModel.kioskStatus.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshKioskStatus() }

    // При входе в настройки системная клавиатура скрывается (оставалась
    // после ввода PIN); снова появляется только по тапу на текстовое поле
    val imeView = LocalView.current
    LaunchedEffect(Unit) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imeView.post {
            imeView.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        }
    }

    // Auto-fill Chat ID when detected
    LaunchedEffect(chatIdResult) {
        when (val result = chatIdResult) {
            is ChatIdResult.Success -> {
                localSettings = localSettings.copy(telegramChatId = result.chatId)
            }
            else -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Старт — закреплён сверху, всегда виден
        Button(
            onClick = {
                viewModel.saveSettings(localSettings)
                onStart()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(strings.btnStart)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.settingsTitle,
                style = MaterialTheme.typography.headlineLarge
            )

            // 1. Язык интерфейса
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (localSettings.language == "en") "English" else "Русский",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strings.languageLabel) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Русский") },
                        onClick = {
                            langExpanded = false
                            localSettings = localSettings.copy(language = "ru")
                            viewModel.setLanguage("ru")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("English") },
                        onClick = {
                            langExpanded = false
                            localSettings = localSettings.copy(language = "en")
                            viewModel.setLanguage("en")
                        }
                    )
                }
            }

            // 2. PIN-код
            OutlinedTextField(
                value = localSettings.pinCode,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) localSettings = localSettings.copy(pinCode = it) },
                label = { Text(strings.pinLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            // 3. Название организации
            OutlinedTextField(
                value = localSettings.orgName,
                onValueChange = { localSettings = localSettings.copy(orgName = it) },
                label = { Text(strings.orgNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 4. Приветствие
            OutlinedTextField(
                value = localSettings.greeting,
                onValueChange = { localSettings = localSettings.copy(greeting = it) },
                label = { Text(strings.greetingLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // 5. Обращение к клиенту
            OutlinedTextField(
                value = localSettings.callToAction,
                onValueChange = { localSettings = localSettings.copy(callToAction = it) },
                label = { Text(strings.callToActionLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // 6. Текст перед полем отзыва
            OutlinedTextField(
                value = localSettings.commentHint,
                onValueChange = { localSettings = localSettings.copy(commentHint = it) },
                label = { Text(strings.commentHintLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // 7. Текст благодарности
            OutlinedTextField(
                value = localSettings.thankYouText,
                onValueChange = { localSettings = localSettings.copy(thankYouText = it) },
                label = { Text(strings.thankYouLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // 8. Таймер сброса
            OutlinedTextField(
                value = localSettings.resetTimeout.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { timeout ->
                        localSettings = localSettings.copy(resetTimeout = timeout)
                    }
                },
                label = { Text(strings.resetTimeoutLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // 9. Отправлять незавершённый отзыв
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.sendIncompleteLabel)
                Switch(
                    checked = localSettings.sendIncomplete,
                    onCheckedChange = { localSettings = localSettings.copy(sendIncomplete = it) }
                )
            }

            // 10. Настройки Android
            Button(
                onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        // Флаг excursion поднимаем ДО ухода из foreground (контракт M3)
                        kioskManager.beginExcursion(activity)
                    }
                    if (SettingsExcursionOverlay.canDraw(context)) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        SettingsExcursionOverlay.show(context)
                    } else {
                        // Разрешения нет — один раз просим его выдать (spec §6)
                        SettingsExcursionOverlay.openPermissionScreen(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text(strings.androidSettingsLabel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 11. Способ отправки — выделенный блок внизу
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = CardDefaults.outlinedCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.sendMethodLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Выбор способа отправки
                    ExposedDropdownMenuBox(
                        expanded = sendMethodExpanded,
                        onExpandedChange = { sendMethodExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (localSettings.sendMethod == "email") strings.sendMethodEmail else strings.sendMethodTelegram,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.sendMethodLabel) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = sendMethodExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = sendMethodExpanded,
                            onDismissRequest = { sendMethodExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(strings.sendMethodTelegram) },
                                onClick = {
                                    sendMethodExpanded = false
                                    localSettings = localSettings.copy(sendMethod = "telegram")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(strings.sendMethodEmail) },
                                onClick = {
                                    sendMethodExpanded = false
                                    localSettings = localSettings.copy(sendMethod = "email")
                                }
                            )
                        }
                    }

                    // Поля в зависимости от способа отправки
                    if (localSettings.sendMethod == "telegram") {
                        // Telegram: токен
                        OutlinedTextField(
                            value = localSettings.telegramToken,
                            onValueChange = {
                                localSettings = localSettings.copy(telegramToken = it)
                                viewModel.clearChatIdResult()
                            },
                            label = { Text(strings.telegramTokenLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Кнопка определения Chat ID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.detectChatId(localSettings.telegramToken) },
                                enabled = !isDetectingChatId && localSettings.telegramToken.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isDetectingChatId) strings.detectingChatId else strings.detectChatIdBtn)
                            }
                        }

                        // Результат определения Chat ID
                        when (val result = chatIdResult) {
                            is ChatIdResult.Success -> {
                                Text(
                                    text = strings.chatIdDetected.format(result.chatId),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is ChatIdResult.NoMessages -> {
                                Text(
                                    text = strings.chatIdNoMessages,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is ChatIdResult.Error -> {
                                Text(
                                    text = strings.chatIdError.format(result.message),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            null -> {}
                        }

                        // Chat ID (ручной ввод)
                        OutlinedTextField(
                            value = localSettings.telegramChatId,
                            onValueChange = { localSettings = localSettings.copy(telegramChatId = it) },
                            label = { Text(strings.chatIdLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    } else {
                        // Email: пресет SMTP
                        ExposedDropdownMenuBox(
                            expanded = emailPresetExpanded,
                            onExpandedChange = { emailPresetExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (localSettings.emailPreset) {
                                    "gmail" -> strings.emailPresetGmail
                                    "yandex" -> strings.emailPresetYandex
                                    else -> strings.emailPresetCustom
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strings.emailPresetLabel) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = emailPresetExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = emailPresetExpanded,
                                onDismissRequest = { emailPresetExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.emailPresetGmail) },
                                    onClick = {
                                        emailPresetExpanded = false
                                        localSettings = localSettings.copy(emailPreset = "gmail", smtpPort = 587, smtpSsl = false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.emailPresetYandex) },
                                    onClick = {
                                        emailPresetExpanded = false
                                        localSettings = localSettings.copy(emailPreset = "yandex", smtpPort = 465, smtpSsl = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.emailPresetCustom) },
                                    onClick = {
                                        emailPresetExpanded = false
                                        localSettings = localSettings.copy(emailPreset = "custom")
                                    }
                                )
                            }
                        }

                        // Email отправителя
                        OutlinedTextField(
                            value = localSettings.emailFrom,
                            onValueChange = { localSettings = localSettings.copy(emailFrom = it) },
                            label = { Text(strings.emailFromLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        // Пароль приложения
                        OutlinedTextField(
                            value = localSettings.emailPassword,
                            onValueChange = { localSettings = localSettings.copy(emailPassword = it) },
                            label = { Text(strings.emailPasswordLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Email получателя
                        OutlinedTextField(
                            value = localSettings.emailTo,
                            onValueChange = { localSettings = localSettings.copy(emailTo = it) },
                            label = { Text(strings.emailToLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        // SMTP хост/порт — только для ручного ввода
                        if (localSettings.emailPreset == "custom") {
                            OutlinedTextField(
                                value = localSettings.smtpHost,
                                onValueChange = { localSettings = localSettings.copy(smtpHost = it) },
                                label = { Text(strings.smtpHostLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = localSettings.smtpPort.toString(),
                                    onValueChange = { value ->
                                        value.toIntOrNull()?.let { port ->
                                            localSettings = localSettings.copy(smtpPort = port)
                                        }
                                    },
                                    label = { Text(strings.smtpPortLabel) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Checkbox(
                                        checked = localSettings.smtpSsl,
                                        onCheckedChange = { localSettings = localSettings.copy(smtpSsl = it) }
                                    )
                                    Text(strings.smtpSslLabel)
                                }
                            }
                        }
                    }
                }
            }

            // 12. Инструкции — спойлер
            InstructionsSpoiler(
                expanded = instructionsExpanded,
                onToggle = { instructionsExpanded = !instructionsExpanded },
                sendMethod = localSettings.sendMethod
            )

            // 13. Инфокиоск — статус и инструкции
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
                onShowQr = { qrBitmap = ProvisioningQr.encodeQr(buildQrPayload()) },
                qrBitmap = qrBitmap,
                onDismissQr = { qrBitmap = null },
                onShareQr = { ProvisioningQr.shareQr(context, buildQrPayload()) }
            )

            // Диалог с QR-изображением
            qrBitmap?.let { bmp ->
                AlertDialog(
                    onDismissRequest = { qrBitmap = null },
                    title = { Text(strings.kioskQrShowButton) },
                    text = {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = strings.kioskQrShowButton,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { ProvisioningQr.shareQr(context, buildQrPayload()) }) {
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InstructionsSpoiler(
    expanded: Boolean,
    onToggle: () -> Unit,
    sendMethod: String
) {
    val strings = LocalAppStrings.current

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
                    text = strings.instructionsTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (sendMethod == "telegram") {
                        TelegramInstructions()
                    } else {
                        EmailInstructions()
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramInstructions() {
    val strings = LocalAppStrings.current

    if (strings.sendMethodTelegram == "Telegram") {
        // Russian
        Text(
            text = "Как создать бота Telegram:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val steps = listOf(
            "1. Откройте Telegram, найдите @BotFather и начните чат",
            "2. Отправьте команду /newbot",
            "3. Придумайте имя бота (например: «Отзывы компании»)",
            "4. Придумайте username бота (латиницей, заканчивается на bot)",
            "5. BotFather пришлёт вам токен — скопируйте его",
            "6. Нажмите на созданного бота и отправьте ему /start",
            "7. Вернитесь в приложение и вставьте токен в поле",
            "8. Нажмите «Определить Chat ID» — приложение подставит ID автоматически",
            "",
            "Если ID не определился — напишите боту любое сообщение и нажмите кнопку ещё раз."
        )
        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    } else {
        // English
        Text(
            text = "How to create a Telegram bot:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val steps = listOf(
            "1. Open Telegram, find @BotFather and start a chat",
            "2. Send the command /newbot",
            "3. Choose a bot name (e.g. \"Company Reviews\")",
            "4. Choose a username (in Latin, ends with bot)",
            "5. BotFather will send you a token — copy it",
            "6. Tap the created bot and send /start",
            "7. Return to the app and paste the token",
            "8. Tap \"Detect Chat ID\" — the app will fill it automatically",
            "",
            "If the ID was not detected — send any message to the bot and tap the button again."
        )
        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun EmailInstructions() {
    val strings = LocalAppStrings.current

    if (strings.sendMethodEmail == "E-mail") {
        // Russian
        Text(
            text = "Как создать пароль приложения:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Gmail:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val gmailSteps = listOf(
            "1. Откройте в браузере: myaccount.google.com/apppasswords",
            "2. Если потребуется — войдите в аккаунт и подтвердите вход",
            "3. Введите название (например: «Отзывы») → «Создать»",
            "4. Скопируйте пароль (формат: abcd efgh ijkl mnop)",
            "5. Вставьте его в приложение в поле «Пароль»",
            "",
            "⚠️ Требуется включённая двухэтапная аутентификация.",
            "⚠️ Обычный пароль от Gmail не подойдёт — нужен именно пароль приложения."
        )
        gmailSteps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Яндекс:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val yandexSteps = listOf(
            "1. Откройте Яндекс ID → Безопасность",
            "2. Найдите «Пароли приложений» → выбрать «Почта» → ввести «Имя пароля»",
            "3. Скопируйте пароль и вставьте в приложение",
            "",
            "⚠️ Обычный пароль от Яндекса не подойдёт."
        )
        yandexSteps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    } else {
        // English
        Text(
            text = "How to create an app password:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Gmail:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val gmailSteps = listOf(
            "1. Open in browser: myaccount.google.com/apppasswords",
            "2. Sign in to your account if prompted",
            "3. Enter a name (e.g. \"Reviews\") → \"Create\"",
            "4. Copy the password (format: abcd efgh ijkl mnop)",
            "5. Paste it into the app \"Password\" field",
            "",
            "⚠️ Two-factor authentication must be enabled.",
            "⚠️ Your regular Gmail password will not work — you need an app password."
        )
        gmailSteps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Yandex:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val yandexSteps = listOf(
            "1. Open Yandex ID → Security",
            "2. Find \"App passwords\" → select \"Mail\" → enter \"Password name\"",
            "3. Copy the password and paste it into the app",
            "",
            "⚠️ Your regular Yandex password will not work."
        )
        yandexSteps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun KioskCard(
    status: KioskStatus?,
    expanded: Boolean,
    onToggle: () -> Unit,
    adbExpanded: Boolean,
    onToggleAdb: () -> Unit,
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
