package com.respondent.pro.data.remote

import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSenderTest {

    private class FakeDao(var pending: List<Feedback>) : FeedbackDao {
        override fun getAll(): Flow<List<Feedback>> = flowOf(pending)
        override suspend fun getUnsent(): List<Feedback> = pending
        override suspend fun insert(feedback: Feedback): Long = 1L
        override suspend fun markSent(id: Long) {}
        override suspend fun markError(id: Long, error: String) {}
        override suspend fun delete(id: Long) {}
        override suspend fun markServerSynced(id: Long, at: Long) {}
    }

    private class FakeStorage(var data: StoredBinding?) : BindingStorage {
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null }
    }

    private open class FakeChannel : FeedbackChannel {
        val sent = mutableListOf<Long>()
        var drainCalls = 0
        override suspend fun send(feedback: Feedback): Boolean { sent += feedback.id; return true }
        override suspend fun sendUnsent(): Int { drainCalls++; return 0 }
    }

    private class FakeServer : FakeChannel(), ServerChannel
    private class FakeLocal : FakeChannel(), LocalChannel

    private val fb = Feedback(id = 7, rating = 5, text = "Ок")
    private val bound = StoredBinding("rpro_key", "t1", "o1", "ООО", null)

    @Test fun `привязан — уходит только в C web, Telegram-e-mail не трогается`() = runBlocking {
        val server = FakeServer(); val local = FakeLocal()
        val sender = FeedbackSender(server, local, FakeStorage(bound), FakeDao(listOf(fb)))

        assertEquals(true, sender.send(fb))
        assertEquals(listOf(7L), server.sent)
        assertTrue(local.sent.isEmpty())
    }

    @Test fun `не привязан — уходит только в Telegram-e-mail, сервер не дёргается`() = runBlocking {
        val server = FakeServer(); val local = FakeLocal()
        val sender = FeedbackSender(server, local, FakeStorage(null), FakeDao(listOf(fb)))

        assertEquals(true, sender.send(fb))
        assertEquals(listOf(7L), local.sent)
        assertTrue(server.sent.isEmpty())
    }

    @Test fun `уже доставленная запись — повторно не отправляется`() = runBlocking {
        val server = FakeServer(); val local = FakeLocal()
        val sender = FeedbackSender(server, local, FakeStorage(bound), FakeDao(pending = emptyList()))

        assertEquals(true, sender.send(fb))   // getUnsent пуст → дубль не создаём
        assertTrue(server.sent.isEmpty())
        assertTrue(local.sent.isEmpty())
    }

    @Test fun `sendUnsent выбирает канал по режиму, включая переключение между вызовами`() = runBlocking {
        val server = FakeServer(); val local = FakeLocal()
        val storage = FakeStorage(bound)
        val sender = FeedbackSender(server, local, storage, FakeDao(listOf(fb)))

        sender.sendUnsent()
        assertEquals(1, server.drainCalls)

        storage.data = null            // отвязка
        sender.sendUnsent()
        assertEquals(1, local.drainCalls)
        assertEquals(1, server.drainCalls)
    }
}
