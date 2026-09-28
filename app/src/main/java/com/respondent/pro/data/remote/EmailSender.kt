package com.respondent.pro.data.remote

import android.util.Log
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import com.respondent.pro.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@Singleton
class EmailSender @Inject constructor(
    private val feedbackDao: FeedbackDao,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "EmailSender"
    }

    suspend fun send(feedback: Feedback): Boolean {
        val settings = settingsRepository.settings.first()
        val emailFrom = settings.emailFrom
        val emailPassword = settings.emailPassword
        val emailTo = settings.emailTo

        Log.d(TAG, "=== EmailSender.send() START ===")
        Log.d(TAG, "From: $emailFrom, To: $emailTo, Preset: ${settings.emailPreset}")

        if (emailFrom.isBlank() || emailPassword.isBlank() || emailTo.isBlank()) {
            Log.w(TAG, "Email not configured")
            feedbackDao.markError(feedback.id, "Email not configured")
            return false
        }

        val (host, port, ssl) = getSmtpConfig(settings.emailPreset, settings)

        return try {
            withContext(Dispatchers.IO) {
                val props = Properties().apply {
                    put("mail.smtp.host", host)
                    put("mail.smtp.port", port.toString())
                    put("mail.smtp.auth", "true")
                    if (ssl) {
                        put("mail.smtp.ssl.enable", "true")
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.socketFactory.port", port.toString())
                    } else {
                        put("mail.smtp.starttls.enable", "true")
                    }
                    put("mail.smtp.connectiontimeout", "10000")
                    put("mail.smtp.timeout", "10000")
                    put("mail.smtp.writetimeout", "10000")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(emailFrom, emailPassword)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(emailFrom))
                    addRecipient(Message.RecipientType.TO, InternetAddress(emailTo))
                    subject = "Новый отзыв${if (settings.orgName.isNotBlank()) " — ${settings.orgName}" else ""}"
                    setText(formatMessage(feedback, settings.orgName), "UTF-8")
                }

                Log.d(TAG, "Sending email via $host:$port...")
                Transport.send(message)
            }
            feedbackDao.markSent(feedback.id)
            Log.d(TAG, "Feedback ${feedback.id} sent to email ✓")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email for feedback ${feedback.id}", e)
            feedbackDao.markError(feedback.id, e.message ?: "Email error")
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

    private fun getSmtpConfig(preset: String, settings: com.respondent.pro.data.repository.AppSettings): Triple<String, Int, Boolean> {
        return when (preset) {
            "gmail" -> Triple("smtp.gmail.com", 587, false)
            "yandex" -> Triple("smtp.yandex.ru", 465, true)
            "custom" -> Triple(settings.smtpHost, settings.smtpPort, settings.smtpSsl)
            else -> Triple("smtp.gmail.com", 587, false)
        }
    }

    private fun formatMessage(feedback: Feedback, orgName: String): String {
        val stars = "⭐".repeat(feedback.rating)
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(feedback.createdAt))
        val timeTaken = if (feedback.startedAt > 0) {
            val seconds = (feedback.createdAt - feedback.startedAt) / 1000
            "${seconds}сек"
        } else "—"

        return buildString {
            appendLine("Новый отзыв")
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
