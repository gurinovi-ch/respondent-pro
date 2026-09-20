package com.respondent.pro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _comment = MutableStateFlow("")
    val comment: StateFlow<String> = _comment

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _sendSuccess = MutableStateFlow(false)
    val sendSuccess: StateFlow<Boolean> = _sendSuccess

    fun updateComment(text: String) {
        if (text.length <= 500) {
            _comment.value = text
        }
    }

    fun sendFeedback(rating: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSending.value = true
            try {
                feedbackRepository.save(
                    Feedback(
                        rating = rating,
                        text = _comment.value
                    )
                )
                _sendSuccess.value = true
                _comment.value = ""
                onComplete()
            } finally {
                _isSending.value = false
            }
        }
    }

    fun reset() {
        _comment.value = ""
        _sendSuccess.value = false
    }
}
