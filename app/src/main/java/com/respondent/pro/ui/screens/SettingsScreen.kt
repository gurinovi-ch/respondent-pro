package com.respondent.pro.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onStart: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    // Сброс формы при изменении сохранённых настроек, но не при смене одного только языка
    var localSettings by remember(settings.copy(language = "")) { mutableStateOf(settings) }
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var langExpanded by remember { mutableStateOf(false) }

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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                strings.settingsTitle,
                style = MaterialTheme.typography.headlineLarge
            )

            // 1. Язык интерфейса — первый пункт, применяется сразу при выборе
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

            OutlinedTextField(
                value = localSettings.telegramToken,
                onValueChange = { localSettings = localSettings.copy(telegramToken = it) },
                label = { Text(strings.telegramTokenLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = localSettings.telegramChatId,
                onValueChange = { localSettings = localSettings.copy(telegramChatId = it) },
                label = { Text(strings.chatIdLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = localSettings.pinCode,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) localSettings = localSettings.copy(pinCode = it) },
                label = { Text(strings.pinLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            OutlinedTextField(
                value = localSettings.orgName,
                onValueChange = { localSettings = localSettings.copy(orgName = it) },
                label = { Text(strings.orgNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = localSettings.greeting,
                onValueChange = { localSettings = localSettings.copy(greeting = it) },
                label = { Text(strings.greetingLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            OutlinedTextField(
                value = localSettings.callToAction,
                onValueChange = { localSettings = localSettings.copy(callToAction = it) },
                label = { Text(strings.callToActionLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            OutlinedTextField(
                value = localSettings.commentHint,
                onValueChange = { localSettings = localSettings.copy(commentHint = it) },
                label = { Text(strings.commentHintLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            OutlinedTextField(
                value = localSettings.thankYouText,
                onValueChange = { localSettings = localSettings.copy(thankYouText = it) },
                label = { Text(strings.thankYouLabel) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

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

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text(strings.androidSettingsLabel)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
