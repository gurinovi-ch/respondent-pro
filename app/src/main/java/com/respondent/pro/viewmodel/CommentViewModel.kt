package com.respondent.pro.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.remote.FeedbackSender
import com.respondent.pro.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ошибка отправки без текста: тип хранится в ViewModel,
 * перевод подбирается на экране по текущему языку интерфейса.
 */
sealed interface SendError {
    data class SaveFailed(val detail: String?) : SendError
    data class NavigationFailed(val detail: String?) : SendError
}

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackSender: FeedbackSender
) : ViewModel() {

    private val _comment = MutableStateFlow("")
    val comment: StateFlow<String> = _comment

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _sendSuccess = MutableStateFlow(false)
    val sendSuccess: StateFlow<Boolean> = _sendSuccess

    private val _sendError = MutableStateFlow<SendError?>(null)
    val sendError: StateFlow<SendError?> = _sendError

    fun updateComment(text: String) {
        if (text.length <= 500) {
            _comment.value = text
        }
    }

    fun sendFeedback(rating: Int, startedAt: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            try {
                val id = feedbackRepository.save(
                    Feedback(
                        rating = rating,
                        text = _comment.value,
                        startedAt = startedAt,
                        isComplete = true
                    )
                )
                Log.d("CommentViewModel", "Feedback saved to DB, id=$id")

                // Try to send to Telegram immediately
                val savedFeedback = Feedback(
                    id = id,
                    rating = rating,
                    text = _comment.value,
                    startedAt = startedAt,
                    isComplete = true
                )
                val sent = feedbackSender.send(savedFeedback)
                if (!sent) {
                    Log.w("CommentViewModel", "Feedback saved but NOT sent")
                } else {
                    Log.d("CommentViewModel", "Feedback saved AND sent ✓")
                }

                _sendSuccess.value = true
                _comment.value = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CommentViewModel", "Error saving feedback", e)
                _sendError.value = SendError.SaveFailed(e.message)
                _isSending.value = false
                return@launch
            } finally {
                _isSending.value = false
            }
            // Call onComplete OUTSIDE try-catch so navigation exceptions don't interfere
            try {
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CommentViewModel", "Error in onComplete navigation", e)
                _sendError.value = SendError.NavigationFailed(e.message)
            }
        }
    }

    fun getComment(): String = _comment.value

    fun reset() {
        _comment.value = ""
        _sendSuccess.value = false
        _sendError.value = null
    }
}
