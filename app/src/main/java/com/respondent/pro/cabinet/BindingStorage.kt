package com.respondent.pro.cabinet

import android.content.Context
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
 */
class EncryptedBindingStorage(context: Context) : BindingStorage {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cabinet_binding",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): StoredBinding? {
        val apiKey = prefs.getString(KEY_API, null) ?: return null
        return StoredBinding(
            apiKey = apiKey,
            tabletId = prefs.getString(KEY_TABLET, "") ?: "",
            organizationId = prefs.getString(KEY_ORG_ID, "") ?: "",
            organizationName = prefs.getString(KEY_ORG_NAME, "") ?: "",
            pointName = prefs.getString(KEY_POINT, null)
        )
    }

    override fun write(binding: StoredBinding) {
        prefs.edit()
            .putString(KEY_API, binding.apiKey)
            .putString(KEY_TABLET, binding.tabletId)
            .putString(KEY_ORG_ID, binding.organizationId)
            .putString(KEY_ORG_NAME, binding.organizationName)
            .putString(KEY_POINT, binding.pointName)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_TABLET = "tablet_id"
        const val KEY_ORG_ID = "org_id"
        const val KEY_ORG_NAME = "org_name"
        const val KEY_POINT = "point_name"
    }
}
