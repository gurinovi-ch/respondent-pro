package com.respondent.pro.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.remote.TelegramApi
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val telegramApi: TelegramApi
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    /** Результат автоопределения Chat ID */
    private val _chatIdResult = MutableStateFlow<ChatIdResult?>(null)
    val chatIdResult: StateFlow<ChatIdResult?> = _chatIdResult

    /** Идёт ли запрос Chat ID */
    private val _isDetectingChatId = MutableStateFlow(false)
    val isDetectingChatId: StateFlow<Boolean> = _isDetectingChatId

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.save(settings)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.saveLanguage(language)
        }
    }

    fun clearChatIdResult() {
        _chatIdResult.value = null
    }

    fun detectChatId(token: String) {
        if (token.isBlank()) {
            _chatIdResult.value = ChatIdResult.Error("Введите токен бота")
            return
        }

        viewModelScope.launch {
            _isDetectingChatId.value = true
            _chatIdResult.value = null
            try {
                val response = telegramApi.getUpdates(token.trim())
                Log.d("SettingsViewModel", "getUpdates: ok=${response.ok}, result size=${response.result?.size}")

                if (!response.ok) {
                    _chatIdResult.value = ChatIdResult.Error(response.description ?: "Ошибка API")
                    return@launch
                }

                val updates = response.result
                if (updates.isNullOrEmpty()) {
                    _chatIdResult.value = ChatIdResult.NoMessages
                    return@launch
                }

                // Берём chat_id из последнего сообщения
                val chatId = updates
                    .mapNotNull { it.message?.chat?.id }
                    .lastOrNull()

                if (chatId != null) {
                    _chatIdResult.value = ChatIdResult.Success(chatId.toString())
                } else {
                    _chatIdResult.value = ChatIdResult.NoMessages
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "detectChatId failed", e)
                _chatIdResult.value = ChatIdResult.Error(e.message ?: "Network error")
            } finally {
                _isDetectingChatId.value = false
            }
        }
    }
}

sealed class ChatIdResult {
    data class Success(val chatId: String) : ChatIdResult()
    object NoMessages : ChatIdResult()
    data class Error(val message: String) : ChatIdResult()
}
