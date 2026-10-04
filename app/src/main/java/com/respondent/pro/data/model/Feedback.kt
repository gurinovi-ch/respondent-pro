package com.respondent.pro.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedbacks")
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rating: Int,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = System.currentTimeMillis(),
    val sentToTelegram: Boolean = false,
    val isComplete: Boolean = true,
    val errorMessage: String? = null,
    /** Момент доставки на сервер C web; null = ещё не доставлен. */
    val serverSyncedAt: Long? = null,
    /** UUID строки — идемпотентность ретраев на сервере. */
    val clientKey: String? = java.util.UUID.randomUUID().toString(),
)
