package com.respondent.pro.cabinet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredBinding(
    val apiKey: String,
    val tabletId: String,
    val organizationId: String,
    val organizationName: String,
    val pointName: String?
)

/** Хранение ключа привязки. Интерфейс — чтобы юнит-тесты подменяли реализацию. */
interface BindingStorage {
    fun read(): StoredBinding?
    fun write(binding: StoredBinding)
    fun clear()
}

/**
 * Реализация поверх EncryptedSharedPreferences (Android Keystore).
 * Room и DataStore не используются (правило проекта).
 *
 * Все методы безопасны на границе: сбой Keystore/SharedPreferences
 * логируется и деградирует (null / no-op), но не роняет приложение.
 */
class EncryptedBindingStorage(private val prefs: SharedPreferences) : BindingStorage {

    override fun read(): StoredBinding? = try {
        val apiKey = prefs.getString(KEY_API, null)
        if (apiKey == null) {
            null
        } else {
            StoredBinding(
                apiKey = apiKey,
                tabletId = prefs.getString(KEY_TABLET, "") ?: "",
                organizationId = prefs.getString(KEY_ORG_ID, "") ?: "",
                organizationName = prefs.getString(KEY_ORG_NAME, "") ?: "",
                pointName = prefs.getString(KEY_POINT, null)
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "binding read failed, treating as unbound", e)
        null
    }

    override fun write(binding: StoredBinding) {
        try {
            prefs.edit()
                .putString(KEY_API, binding.apiKey)
                .putString(KEY_TABLET, binding.tabletId)
                .putString(KEY_ORG_ID, binding.organizationId)
                .putString(KEY_ORG_NAME, binding.organizationName)
                .putString(KEY_POINT, binding.pointName)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "binding write failed", e)
        }
    }

    override fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "binding clear failed", e)
        }
    }

    companion object {
        private const val TAG = "CabinetBinding"
        private const val KEY_API = "api_key"
        private const val KEY_TABLET = "tablet_id"
        private const val KEY_ORG_ID = "org_id"
        private const val KEY_ORG_NAME = "org_name"
        private const val KEY_POINT = "point_name"

        /**
         * Фабрика: шифрованные преференсы, а при сбое Keystore —
         * обычные (лучше потерять шифрование, чем крэш-луп Настроек).
         * Ключ всё равно отзывается в кабинете при компрометации.
         */
        fun create(context: Context): BindingStorage {
            val prefs = try {
                EncryptedSharedPreferences.create(
                    context,
                    "cabinet_binding",
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e)
                context.getSharedPreferences("cabinet_binding_plain", Context.MODE_PRIVATE)
            }
            return EncryptedBindingStorage(prefs)
        }
    }
}
