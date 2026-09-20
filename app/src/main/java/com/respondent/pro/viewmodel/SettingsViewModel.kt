package com.respondent.pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

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
}
