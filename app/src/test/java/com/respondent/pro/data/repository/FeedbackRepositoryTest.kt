package com.respondent.pro.data.repository

import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FeedbackRepositoryTest {

    private class FakeDao : FeedbackDao {
        var nextId = 100L
        var stored: Feedback? = null
        override fun getAll(): Flow<List<Feedback>> = flowOf(listOfNotNull(stored))
        override suspend fun getUnsent(): List<Feedback> = listOfNotNull(stored)
        override suspend fun insert(feedback: Feedback): Long {
            stored = feedback.copy(id = nextId)
            return nextId
        }
        override suspend fun markSent(id: Long) {}
        override suspend fun markError(id: Long, error: String) {}
        override suspend fun delete(id: Long) {}
        override suspend fun markServerSynced(id: Long, at: Long) {}
    }

    @Test fun `save возвращает ту же запись — id заполнен, clientKey не изменился`() = runBlocking {
        val repo = FeedbackRepository(FakeDao())
        val original = Feedback(rating = 4, text = "Отлично", startedAt = 123L)

        val saved = repo.save(original)

        assertEquals(100L, saved.id)
        assertNotNull(saved.clientKey)
        assertEquals(original.clientKey, saved.clientKey) // ключ один на все ретраи
        assertEquals(4, saved.rating)
        assertEquals("Отлично", saved.text)               // связка оценки и комментария
        assertEquals(123L, saved.startedAt)
    }

    @Test fun `save не мутирует входной объект — copy, а не in-place`() = runBlocking {
        val repo = FeedbackRepository(FakeDao())
        val original = Feedback(rating = 2, text = "так себе")
        val saved = repo.save(original)
        assertEquals(0L, original.id)                     // copy, не in-place
        assertEquals(original.clientKey, saved.clientKey)
    }
}
