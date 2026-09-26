package com.respondent.pro.data.remote

import android.util.Log
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramSender @Inject constructor(
    private val telegramApi: TelegramApi,
    private val feedbackDao: FeedbackDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "TelegramSender"
        private const val BASE_URL = "https://api.telegram.org/"
    }

    suspend fun send(feedback: Feedback): Boolean {
        val settings = settingsRepository.settings.first()
        val token = settings.telegramToken
        val chatId = settings.telegramChatId

        Log.d(TAG, "=== TelegramSender.send() START ===")
        Log.d(TAG, "Token length: ${token.length}, blank: ${token.isBlank()}")
        Log.d(TAG, "ChatID: '$chatId', blank: ${chatId.isBlank()}")

        if (token.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Telegram token or chat ID not configured")
            feedbackDao.markError(feedback.id, "Telegram not configured")
            return false
        }

        val message = formatMessage(feedback, settings.orgName)
        Log.d(TAG, "Message preview: ${message.take(100)}...")

        return try {
            Log.d(TAG, "Calling Telegram API: bot<token>/sendMessage")
            val response = telegramApi.sendMessage(token, chatId, message)
            Log.d(TAG, "Telegram API response: ok=${response.ok}, desc=${response.description}")
            if (response.ok) {
                feedbackDao.markSent(feedback.id)
                Log.d(TAG, "Feedback ${feedback.id} sent to Telegram ✓")
                true
            } else {
                val error = response.description ?: "Unknown error"
                feedbackDao.markError(feedback.id, error)
                Log.e(TAG, "Telegram API error: $error")
                false
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "no body"
            Log.e(TAG, "HTTP ${e.code()} error: $errorBody")
            feedbackDao.markError(feedback.id, "HTTP ${e.code()}: $errorBody")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send feedback ${feedback.id}", e)
            feedbackDao.markError(feedback.id, e.message ?: "Network error")
            false
        }
    }

    suspend fun sendUnsent(): Int {
        val unsent = feedbackDao.getUnsent()
        var sentCount = 0
        for (feedback in unsent) {
            if (send(feedback)) {
                sentCount++
            }
        }
        Log.d(TAG, "Retry: sent $sentCount of ${unsent.size} unsent feedbacks")
        return sentCount
    }

    private fun formatMessage(feedback: Feedback, orgName: String): String {
        val stars = "⭐".repeat(feedback.rating)
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(feedback.createdAt))
        val timeTaken = if (feedback.startedAt > 0) {
            val seconds = (feedback.createdAt - feedback.startedAt) / 1000
            "${seconds}сек"
        } else "—"

        return buildString {
            appendLine("<b>Новый отзыв</b>")
            appendLine()
            appendLine("Оценка: $stars (${feedback.rating}/5)")
            if (feedback.text.isNotBlank()) {
                appendLine("Комментарий: ${feedback.text}")
            }
            appendLine()
            if (orgName.isNotBlank()) {
                appendLine("Организация: $orgName")
            }
            appendLine("Дата: $date")
            appendLine("Время заполнения: $timeTaken")
            appendLine("Полный: ${if (feedback.isComplete) "да" else "нет (таймаут)"}")
        }
    }
}
