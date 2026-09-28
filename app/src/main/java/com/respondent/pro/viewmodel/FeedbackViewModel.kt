package com.respondent.pro.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.remote.FeedbackSender
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
    private val settingsRepository: SettingsRepository,
    private val feedbackSender: FeedbackSender
) : ViewModel() {

    private val _rating = MutableStateFlow(0)
    val rating: StateFlow<Int> = _rating

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private var startedAt: Long = 0L
    private var hasStartedRating = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
        // Retry sending unsent feedbacks on startup (delayed to let DataStore load)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // Wait for DataStore to load
            try {
                val sent = feedbackSender.sendUnsent()
                if (sent > 0) {
                    Log.d("FeedbackViewModel", "Retry: sent $sent unsent feedbacks")
                }
            } catch (e: Exception) {
                Log.e("FeedbackViewModel", "Error retrying unsent feedbacks", e)
            }
        }
    }

    fun setRating(value: Int) {
        _rating.value = value
        if (!hasStartedRating) {
            startedAt = System.currentTimeMillis()
            hasStartedRating = true
        }
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

    fun resetAll() {
        _rating.value = 0
        startedAt = 0L
        hasStartedRating = false
        _showPinDialog.value = false
    }

    fun saveIncompleteFeedback(text: String = "") {
        val currentRating = _rating.value
        // Захватываем startedAt ДО resetAll(), иначе корутина прочитает
        // уже обнулённое значение (время заполнения отображалось как «—»)
        val currentStartedAt = startedAt
        if (currentRating > 0) {
            viewModelScope.launch {
                try {
                    val id = feedbackRepository.save(
                        Feedback(
                            rating = currentRating,
                            text = text,
                            startedAt = currentStartedAt,
                            createdAt = System.currentTimeMillis(),
                            isComplete = false
                        )
                    )
                    // Try to send incomplete feedback too
                    val savedFeedback = Feedback(
                        id = id,
                        rating = currentRating,
                        text = text,
                        startedAt = currentStartedAt,
                        isComplete = false
                    )
                    feedbackSender.send(savedFeedback)
                } catch (e: Exception) {
                    Log.e("FeedbackViewModel", "Error saving incomplete feedback", e)
                }
            }
        }
        resetAll()
    }

    fun getStartedAt(): Long = startedAt
}
