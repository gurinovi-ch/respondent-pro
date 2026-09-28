package com.respondent.pro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val telegramToken: String = "",
    val telegramChatId: String = "",
    val pinCode: String = "0000",
    val orgName: String = "",
    val greeting: String = "",
    val callToAction: String = "",
    val commentHint: String = "",
    // Пусто по умолчанию: на экране показывается локализованный дефолт
    val thankYouText: String = "",
    val resetTimeout: Int = 60,
    val sendIncomplete: Boolean = false,
    /** Код языка интерфейса: "ru" | "en". */
    val language: String = "ru",
    /** Способ отправки: "telegram" | "email" */
    val sendMethod: String = "telegram",
    /** Пресет SMTP: "gmail" | "yandex" | "custom" */
    val emailPreset: String = "gmail",
    /** Email отправителя */
    val emailFrom: String = "",
    /** Пароль приложения */
    val emailPassword: String = "",
    /** Email получателя */
    val emailTo: String = "",
    /** SMTP хост (для custom пресета) */
    val smtpHost: String = "",
    /** SMTP порт */
    val smtpPort: Int = 587,
    /** Использовать SSL */
    val smtpSsl: Boolean = false
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TELEGRAM_TOKEN = stringPreferencesKey("telegram_token")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val PIN_CODE = stringPreferencesKey("pin_code")
        val ORG_NAME = stringPreferencesKey("org_name")
        val GREETING = stringPreferencesKey("greeting")
        val CALL_TO_ACTION = stringPreferencesKey("call_to_action")
        val COMMENT_HINT = stringPreferencesKey("comment_hint")
        val THANK_YOU_TEXT = stringPreferencesKey("thank_you_text")
        val RESET_TIMEOUT = intPreferencesKey("reset_timeout")
        val SEND_INCOMPLETE = booleanPreferencesKey("send_incomplete")
        val LANGUAGE = stringPreferencesKey("language")
        val SEND_METHOD = stringPreferencesKey("send_method")
        val EMAIL_PRESET = stringPreferencesKey("email_preset")
        val EMAIL_FROM = stringPreferencesKey("email_from")
        val EMAIL_PASSWORD = stringPreferencesKey("email_password")
        val EMAIL_TO = stringPreferencesKey("email_to")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SMTP_SSL = booleanPreferencesKey("smtp_ssl")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            telegramToken = prefs[Keys.TELEGRAM_TOKEN] ?: "",
            telegramChatId = prefs[Keys.TELEGRAM_CHAT_ID] ?: "",
            pinCode = prefs[Keys.PIN_CODE] ?: "0000",
            orgName = prefs[Keys.ORG_NAME] ?: "",
            greeting = prefs[Keys.GREETING] ?: "",
            callToAction = prefs[Keys.CALL_TO_ACTION] ?: "",
            commentHint = prefs[Keys.COMMENT_HINT] ?: "",
            thankYouText = prefs[Keys.THANK_YOU_TEXT] ?: "",
            resetTimeout = prefs[Keys.RESET_TIMEOUT] ?: 60,
            sendIncomplete = prefs[Keys.SEND_INCOMPLETE] ?: false,
            language = prefs[Keys.LANGUAGE] ?: "ru",
            sendMethod = prefs[Keys.SEND_METHOD] ?: "telegram",
            emailPreset = prefs[Keys.EMAIL_PRESET] ?: "gmail",
            emailFrom = prefs[Keys.EMAIL_FROM] ?: "",
            emailPassword = prefs[Keys.EMAIL_PASSWORD] ?: "",
            emailTo = prefs[Keys.EMAIL_TO] ?: "",
            smtpHost = prefs[Keys.SMTP_HOST] ?: "",
            smtpPort = prefs[Keys.SMTP_PORT] ?: 587,
            smtpSsl = prefs[Keys.SMTP_SSL] ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TELEGRAM_TOKEN] = settings.telegramToken
            prefs[Keys.TELEGRAM_CHAT_ID] = settings.telegramChatId
            prefs[Keys.PIN_CODE] = settings.pinCode
            prefs[Keys.ORG_NAME] = settings.orgName
            prefs[Keys.GREETING] = settings.greeting
            prefs[Keys.CALL_TO_ACTION] = settings.callToAction
            prefs[Keys.COMMENT_HINT] = settings.commentHint
            prefs[Keys.THANK_YOU_TEXT] = settings.thankYouText
            prefs[Keys.RESET_TIMEOUT] = settings.resetTimeout
            prefs[Keys.SEND_INCOMPLETE] = settings.sendIncomplete
            prefs[Keys.LANGUAGE] = settings.language
            prefs[Keys.SEND_METHOD] = settings.sendMethod
            prefs[Keys.EMAIL_PRESET] = settings.emailPreset
            prefs[Keys.EMAIL_FROM] = settings.emailFrom
            prefs[Keys.EMAIL_PASSWORD] = settings.emailPassword
            prefs[Keys.EMAIL_TO] = settings.emailTo
            prefs[Keys.SMTP_HOST] = settings.smtpHost
            prefs[Keys.SMTP_PORT] = settings.smtpPort
            prefs[Keys.SMTP_SSL] = settings.smtpSsl
        }
    }

    /**
     * Записывает только язык — мгновенно, не дожидаясь «Старт»
     * и не трогая остальные (возможно, несохранённые) поля формы.
     */
    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language
        }
    }
}
