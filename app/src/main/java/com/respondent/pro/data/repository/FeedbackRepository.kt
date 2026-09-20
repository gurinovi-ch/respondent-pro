package com.respondent.pro.data.repository

import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val dao: FeedbackDao
) {
    val allFeedbacks: Flow<List<Feedback>> = dao.getAll()

    suspend fun save(feedback: Feedback): Long = dao.insert(feedback)

    suspend fun getUnsent(): List<Feedback> = dao.getUnsent()

    suspend fun markSent(id: Long) = dao.markSent(id)

    suspend fun markError(id: Long, error: String) = dao.markError(id, error)
}
