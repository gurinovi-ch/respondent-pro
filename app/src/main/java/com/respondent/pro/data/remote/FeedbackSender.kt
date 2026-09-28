package com.respondent.pro.data.remote

import android.util.Log
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackSender @Inject constructor(
    private val telegramSender: TelegramSender,
    private val emailSender: EmailSender,
    private val settingsRepository: SettingsRepository,
    private val feedbackDao: FeedbackDao
) {
    companion object {
        private const val TAG = "FeedbackSender"
    }

    // Mutex to prevent concurrent sends (race condition causing duplicates)
    private val sendMutex = Mutex()

    suspend fun send(feedback: Feedback): Boolean {
        return sendMutex.withLock {
            // Double-check: skip if already sent
            val unsent = feedbackDao.getUnsent()
            if (unsent.none { it.id == feedback.id }) {
                Log.d(TAG, "Feedback ${feedback.id} already sent, skipping")
                return@withLock true
            }

            val settings = settingsRepository.settings.first()
            when (settings.sendMethod) {
                "email" -> emailSender.send(feedback)
                else -> telegramSender.send(feedback)
            }
        }
    }

    suspend fun sendUnsent(): Int {
        return sendMutex.withLock {
            val settings = settingsRepository.settings.first()
            val unsent = feedbackDao.getUnsent()
            var sentCount = 0

            for (feedback in unsent) {
                val sent = when (settings.sendMethod) {
                    "email" -> emailSender.send(feedback)
                    else -> telegramSender.send(feedback)
                }
                if (sent) sentCount++
            }

            Log.d(TAG, "Retry via ${settings.sendMethod}: sent $sentCount of ${unsent.size}")
            sentCount
        }
    }
}
