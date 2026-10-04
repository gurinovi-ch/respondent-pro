package com.respondent.pro.data.remote

import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.FeedbackAck
import com.respondent.pro.cabinet.FeedbackPayload
import com.respondent.pro.cabinet.PairRequest
import com.respondent.pro.cabinet.PairResponse
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.Calendar
import java.util.Date

class CabinetChannelTest {

    private class FakeDao(var pending: List<Feedback> = emptyList()) : FeedbackDao {
        val synced = mutableListOf<Pair<Long, Long>>()
        override fun getAll(): Flow<List<Feedback>> = flowOf(pending)
        override suspend fun getUnsent(): List<Feedback> = pending
        override suspend fun insert(feedback: Feedback): Long = 1L
        override suspend fun markSent(id: Long) {}
        override suspend fun markError(id: Long, error: String) {}
        override suspend fun delete(id: Long) {}
        override suspend fun markServerSynced(id: Long, at: Long) { synced += id to at }
    }

    private class FakeStorage(var data: StoredBinding?) : BindingStorage {
        var clearCalls = 0
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null; clearCalls++ }
    }

    private class FakeApi(
        private val respond: suspend (FeedbackPayload) -> FeedbackAck =
            { FeedbackAck("srv-1", "2026-10-04T10:00:00.000Z", "2026-10-04T09:31:05.120Z") },
    ) : CabinetApi {
        var bodies = mutableListOf<FeedbackPayload>()
        override suspend fun postFeedback(body: FeedbackPayload): FeedbackAck {
            bodies += body
            return respond(body)
        }
        override suspend fun pair(body: PairRequest): PairResponse = throw NotImplementedError()
    }

    private val bound = StoredBinding("rpro_key", "t1", "o1", "ООО", null)

    private fun feedback(
        id: Long = 1L,
        startedAt: Long = Date.UTC(2026 - 1900, Calendar.OCTOBER, 4, 9, 31, 5) + 120, // UTC, мс отдельно (Date.UTC без millis)
        clientKey: String? = "uuid-1",
        rating: Int = 4,
        text: String = "Отлично",
    ) = Feedback(
        id = id, rating = rating, text = text,
        createdAt = startedAt + 5000, startedAt = startedAt, clientKey = clientKey,
    )

    private fun httpError(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    private fun channel(api: CabinetApi, dao: FeedbackDao, storage: FakeStorage) =
        CabinetChannel(api, dao, storage)

    @Test fun `успех — payload полный, запись помечена синхронизированной`() = runBlocking {
        val api = FakeApi(); val dao = FakeDao(); val storage = FakeStorage(bound)
        val outcome = channel(api, dao, storage).upload(feedback())

        assertEquals(UploadOutcome.SYNCED, outcome)
        val body = api.bodies.single()
        assertEquals(4, body.rating)
        assertEquals("Отлично", body.text)
        assertEquals("APK", body.source)
        assertNull(body.sourceRef)
        assertEquals("uuid-1", body.clientKey)
        assertEquals("2026-10-04T09:31:05.120Z", body.startedAt) // UTC, без сдвига на TZ планшета
        assertEquals(1L, dao.synced.single().first)
    }

    @Test fun `startedAt = 0 (нет касания) — поле не отправляем, сервер фолбэчит`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(bound)
        channel(api, FakeDao(), storage).uploadFeedback(startedAt = 0L)
        assertNull(api.bodies.single().startedAt)
    }

    @Test fun `clientKey отсутствует (legacy-строка) — ключ выводится из id, стабилен между ретраями`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(bound)
        val ch = channel(api, FakeDao(), storage)
        ch.uploadFeedback(id = 42L, clientKey = null)
        ch.uploadFeedback(id = 42L, clientKey = null)
        assertEquals("legacy-42", api.bodies[0].clientKey)
        assertEquals(api.bodies[0].clientKey, api.bodies[1].clientKey)
    }

    @Test fun `без привязки — сервер не дёргаем`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(null)
        val outcome = channel(api, FakeDao(), storage).upload(feedback())
        assertEquals(UploadOutcome.RETRY, outcome)
        assertTrue(api.bodies.isEmpty())
    }

    @Test fun `401 и 403 — ключ отозван (REVOKED), storage чистится, запись не помечена`() = runBlocking {
        listOf(401, 403).forEach { code ->
            val api = FakeApi { throw httpError(code) }
            val dao = FakeDao(); val storage = FakeStorage(bound)
            val outcome = channel(api, dao, storage).upload(feedback())
            assertEquals(UploadOutcome.REVOKED, outcome)
            assertEquals(1, storage.clearCalls)
            assertNull(storage.data)
            assertTrue(dao.synced.isEmpty())
        }
    }

    @Test fun `сетевая ошибка — RETRY, запись остаётся в очереди`() = runBlocking {
        val api = FakeApi { throw IOException("timeout") }
        val dao = FakeDao(); val storage = FakeStorage(bound)
        val outcome = channel(api, dao, storage).upload(feedback())
        assertEquals(UploadOutcome.RETRY, outcome)
        assertTrue(dao.synced.isEmpty())
    }

    @Test fun `drain — грузит по очереди, прерывается на первой ошибке`() = runBlocking {
        var calls = 0
        val api = FakeApi {
            calls++
            if (calls == 2) throw IOException("down") else FeedbackAck("s$calls", "t", "t")
        }
        val dao = FakeDao(
            pending = listOf(feedback(id = 1), feedback(id = 2, clientKey = "uuid-2"),
                feedback(id = 3, clientKey = "uuid-3"))
        )
        val sent = channel(api, dao, FakeStorage(bound)).drain(dao.getUnsent())

        assertEquals(1, sent)
        assertEquals(listOf(1L), dao.synced.map { it.first }) // вторая не помечена, третья не пробовалась
        assertEquals(2, calls)
    }

    @Test fun `send() без привязки возвращает false и не вызывает API`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(null)
        val ok = channel(api, FakeDao(), storage).send(feedback())
        assertEquals(false, ok)
        assertTrue(api.bodies.isEmpty())
    }
}
