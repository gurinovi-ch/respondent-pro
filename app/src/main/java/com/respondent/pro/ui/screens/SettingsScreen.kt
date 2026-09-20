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
import com.respondent.pro.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onStart: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var localSettings by remember(settings) { mutableStateOf(settings) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineLarge
        )

        OutlinedTextField(
            value = localSettings.telegramToken,
            onValueChange = { localSettings = localSettings.copy(telegramToken = it) },
            label = { Text("Токен Telegram Bot") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = localSettings.telegramChatId,
            onValueChange = { localSettings = localSettings.copy(telegramChatId = it) },
            label = { Text("Chat ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = localSettings.pinCode,
            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) localSettings = localSettings.copy(pinCode = it) },
            label = { Text("PIN-код (4 цифры)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        OutlinedTextField(
            value = localSettings.orgName,
            onValueChange = { localSettings = localSettings.copy(orgName = it) },
            label = { Text("Название организации") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = localSettings.greeting,
            onValueChange = { localSettings = localSettings.copy(greeting = it) },
            label = { Text("Приветствие") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        OutlinedTextField(
            value = localSettings.callToAction,
            onValueChange = { localSettings = localSettings.copy(callToAction = it) },
            label = { Text("Обращение к клиенту") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        OutlinedTextField(
            value = localSettings.commentHint,
            onValueChange = { localSettings = localSettings.copy(commentHint = it) },
            label = { Text("Текст перед полем отзыва") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        OutlinedTextField(
            value = localSettings.thankYouText,
            onValueChange = { localSettings = localSettings.copy(thankYouText = it) },
            label = { Text("Текст благодарности") },
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
            label = { Text("Таймер сброса (сек)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text("Настройки Android")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.saveSettings(localSettings)
                onStart()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Старт")
        }
    }
}
