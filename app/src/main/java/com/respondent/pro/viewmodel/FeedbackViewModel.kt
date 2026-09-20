package com.respondent.pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.FeedbackRepository
import com.respondent.pro.data.repository.SettingsRepository
import com.respondent.pro.data.repository.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _rating = MutableStateFlow(0)
    val rating: StateFlow<Int> = _rating

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
    }

    fun setRating(value: Int) {
        _rating.value = value
    }

    fun showPin() {
        _showPinDialog.value = true
    }

    fun hidePin() {
        _showPinDialog.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val correct = _settings.value.pinCode
        if (pin == correct) {
            _showPinDialog.value = false
            return true
        }
        return false
    }

    fun resetRating() {
        _rating.value = 0
    }
}
