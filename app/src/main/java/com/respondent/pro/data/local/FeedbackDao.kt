package com.respondent.pro.data.local

import androidx.room.*
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedbacks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Feedback>>

    /** Очередь: не доставлено ни в C msg, ни в C web. */
    @Query("SELECT * FROM feedbacks WHERE sentToTelegram = 0 AND serverSyncedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getUnsent(): List<Feedback>

    @Query("UPDATE feedbacks SET serverSyncedAt = :at WHERE id = :id")
    suspend fun markServerSynced(id: Long, at: Long)

    @Insert
    suspend fun insert(feedback: Feedback): Long

    @Query("UPDATE feedbacks SET sentToTelegram = 1, errorMessage = NULL WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("UPDATE feedbacks SET errorMessage = :error WHERE id = :id")
    suspend fun markError(id: Long, error: String)

    @Query("DELETE FROM feedbacks WHERE id = :id")
    suspend fun delete(id: Long)
}
