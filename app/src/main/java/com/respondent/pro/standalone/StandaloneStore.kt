package com.respondent.pro.standalone

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StandaloneEvent(
    val clientKey: String,
    val rating: Int,
    val occurredAt: String,
    val tzOffsetMin: Int,
)

private val Context.standaloneDataStore by preferencesDataStore(name = "standalone")

interface StandaloneStore {
    suspend fun installId(): String
    suspend fun token(): String?
    suspend fun saveToken(token: String)
    suspend fun clearToken()
    suspend fun lastBoundOrganizationId(): String?
    suspend fun saveLastBoundOrganizationId(id: String?)
    suspend fun pending(): List<StandaloneEvent>
    suspend fun addPending(event: StandaloneEvent)
    suspend fun removePending(keys: Set<String>)
}

@Singleton
class DataStoreStandaloneStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : StandaloneStore {
    private val gson = Gson()
    private object Keys {
        val INSTALL_ID = stringPreferencesKey("install_id")
        val TOKEN = stringPreferencesKey("token")
        val LAST_ORG = stringPreferencesKey("last_bound_organization_id")
        val PENDING = stringPreferencesKey("pending_events")
    }

    override suspend fun installId(): String {
        val existing = context.standaloneDataStore.data.first()[Keys.INSTALL_ID]
        if (existing != null) return existing
        val id = UUID.randomUUID().toString()
        context.standaloneDataStore.edit { it[Keys.INSTALL_ID] = id }
        return id
    }

    override suspend fun token(): String? =
        context.standaloneDataStore.data.first()[Keys.TOKEN]

    override suspend fun saveToken(token: String) {
        context.standaloneDataStore.edit { it[Keys.TOKEN] = token }
    }

    override suspend fun clearToken() {
        context.standaloneDataStore.edit { it.remove(Keys.TOKEN) }
    }

    override suspend fun lastBoundOrganizationId(): String? =
        context.standaloneDataStore.data.first()[Keys.LAST_ORG]

    override suspend fun saveLastBoundOrganizationId(id: String?) {
        context.standaloneDataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.LAST_ORG) else prefs[Keys.LAST_ORG] = id
        }
    }

    override suspend fun pending(): List<StandaloneEvent> {
        val json = context.standaloneDataStore.data.first()[Keys.PENDING] ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<StandaloneEvent>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    override suspend fun addPending(event: StandaloneEvent) {
        val list = (pending() + event).takeLast(500)
        context.standaloneDataStore.edit { it[Keys.PENDING] = gson.toJson(list) }
    }

    override suspend fun removePending(keys: Set<String>) {
        val left = pending().filterNot { it.clientKey in keys }
        context.standaloneDataStore.edit { it[Keys.PENDING] = gson.toJson(left) }
    }
}
