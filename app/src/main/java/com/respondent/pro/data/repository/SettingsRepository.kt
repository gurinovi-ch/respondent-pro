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
    val thankYouText: String = "Спасибо за ваш отзыв!",
    val resetTimeout: Int = 60
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
            thankYouText = prefs[Keys.THANK_YOU_TEXT] ?: "Спасибо за ваш отзыв!",
            resetTimeout = prefs[Keys.RESET_TIMEOUT] ?: 60
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
        }
    }
}
