package com.respondent.pro.data.remote

import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Канал C msg для автономного режима: Telegram ИЛИ e-mail по настройке. */
interface LocalChannel : FeedbackChannel

@Singleton
class SettingsLocalChannel @Inject constructor(
    private val telegramSender: TelegramSender,
    private val emailSender: EmailSender,
    private val settingsRepository: SettingsRepository,
) : LocalChannel {

    override suspend fun send(feedback: Feedback): Boolean =
        when (method()) {
            "email" -> emailSender.send(feedback)
            else -> telegramSender.send(feedback)
        }

    override suspend fun sendUnsent(): Int =
        when (method()) {
            "email" -> emailSender.sendUnsent()
            else -> telegramSender.sendUnsent()
        }

    private suspend fun method(): String = settingsRepository.settings.first().sendMethod
}
