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
import kotlinx.coroutines.CancellationException
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
        // Дренаж очереди: досылаем то, что не ушло (C web или Telegram/e-mail
        // — в зависимости от режима). 30 с при непустой очереди, до 5 мин в
        // потолке; пустая очередь — опрос раз в 5 минут (спека §3, п.6).
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // прогрев DataStore/Room
            var backoff = 30_000L
            while (true) {
                val sent = try {
                    feedbackSender.sendUnsent()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("FeedbackViewModel", "Retry failed", e)
                    0
                }
                val pending = try {
                    feedbackRepository.getUnsent().size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("FeedbackViewModel", "Queue read failed", e)
                    null // очередь неизвестна — не считаем пустой
                }
                if (pending == 0) {
                    // Подтверждённо пустая очередь: опрос раз в 5 минут
                    backoff = 30_000L
                    kotlinx.coroutines.delay(300_000L)
                } else {
                    // Непустая очередь или размер неизвестен: повтор через текущий
                    // backoff с ростом до потолка 5 минут
                    kotlinx.coroutines.delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(300_000L)
                    Log.d("FeedbackViewModel", "Queue: ${pending ?: "unknown"} pending, sent $sent")
                }
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
                    val stored = feedbackRepository.save(
                        Feedback(
                            rating = currentRating,
                            text = text,
                            startedAt = currentStartedAt,
                            createdAt = System.currentTimeMillis(),
                            isComplete = false,
                        )
                    )
                    // Фоном — чтобы сброс таймера не ждал сети
                    feedbackSender.send(stored)
                } catch (e: Exception) {
                    Log.e("FeedbackViewModel", "Error saving incomplete feedback", e)
                }
            }
        }
        resetAll()
    }

    fun getStartedAt(): Long = startedAt
}
