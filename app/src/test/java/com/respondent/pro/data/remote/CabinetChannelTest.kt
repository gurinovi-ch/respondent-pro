package com.respondent.pro.data.remote

import com.respondent.pro.cabinet.BillingPauseSignal
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.DeviceParamsResponse
import com.respondent.pro.cabinet.FeedbackAck
import com.respondent.pro.cabinet.FeedbackPayload
import com.respondent.pro.cabinet.LocationAck
import com.respondent.pro.cabinet.LocationPayload
import com.respondent.pro.cabinet.PairRequest
import com.respondent.pro.cabinet.PairResponse
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.cabinet.TelemetryAck
import com.respondent.pro.cabinet.TelemetryPayload
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.Calendar
import java.util.Date
import java.util.UUID

class CabinetChannelTest {

    private class FakeDao(var pending: List<Feedback> = emptyList()) : FeedbackDao {
        val synced = mutableListOf<Pair<Long, Long>>()
        val assignedKeys = mutableMapOf<Long, String>()
        val errors = mutableMapOf<Long, String>()
        var assignCalls = 0
        override fun getAll(): Flow<List<Feedback>> = flowOf(pending)
        override suspend fun getUnsent(): List<Feedback> = pending
        override suspend fun insert(feedback: Feedback): Long = 1L
        override suspend fun markSent(id: Long) {}
        override suspend fun markError(id: Long, error: String) { errors[id] = error }
        override suspend fun delete(id: Long) {}
        override suspend fun markServerSynced(id: Long, at: Long) { synced += id to at }
        override suspend fun assignClientKey(id: Long, key: String) {
            assignCalls++
            assignedKeys[id] = key
            pending = pending.map { if (it.id == id && it.clientKey == null) it.copy(clientKey = key) else it }
        }
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
        override suspend fun deviceParams(): DeviceParamsResponse = throw NotImplementedError()
        override suspend fun telemetry(body: TelemetryPayload): TelemetryAck = throw NotImplementedError()
        override suspend fun postLocation(body: LocationPayload): LocationAck = throw NotImplementedError()
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

    private fun httpError(code: Int, body: String = "") =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    private fun channel(api: CabinetApi, dao: FeedbackDao, storage: FakeStorage) =
        CabinetChannel(api, dao, storage, BillingPauseSignal())

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

    @Test fun `legacy-строка (clientKey = null) — генерируется UUID, персистится в строку, уходит в payload`() = runBlocking {
        val api = FakeApi(); val dao = FakeDao(); val storage = FakeStorage(bound)
        val outcome = channel(api, dao, storage).upload(feedback(id = 42L, clientKey = null))

        assertEquals(UploadOutcome.SYNCED, outcome)
        val key = dao.assignedKeys[42L]
        assertNotNull(key) // assignClientKey(id, <UUID>) вызван
        UUID.fromString(key!!) // сгенерирован именно UUID, не legacy-<id>
        assertEquals(key, api.bodies.single().clientKey) // payload уже с персистнутым ключом
    }

    @Test fun `повторная отправка legacy-строки — тот же clientKey (стабильность через персист)`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(bound)
        val dao = FakeDao(pending = listOf(feedback(id = 42L, clientKey = null))) // строка в очереди без ключа
        val ch = channel(api, dao, storage)
        ch.upload(dao.pending.single()) // первая отправка: ключ сгенерирован и персистнут
        ch.upload(dao.pending.single()) // повтор: строка, как её видит getUnsent после персиста

        assertEquals(api.bodies[0].clientKey, api.bodies[1].clientKey) // тот же ключ
        assertEquals(1, dao.assignCalls) // ключ не перегенерируется между ретраями
    }

    @Test fun `без привязки — сервер не дёргаем`() = runBlocking {
        val api = FakeApi(); val storage = FakeStorage(null)
        val outcome = channel(api, FakeDao(), storage).upload(feedback())
        assertEquals(UploadOutcome.RETRY, outcome)
        assertTrue(api.bodies.isEmpty())
    }

    @Test fun `401 — REVOKED, storage чистится`() = runBlocking {
        val api = FakeApi { throw httpError(401) }
        val storage = FakeStorage(bound)
        val outcome = channel(api, FakeDao(), storage).upload(feedback())
        assertEquals(UploadOutcome.REVOKED, outcome)
        assertEquals(1, storage.clearCalls)
    }

    @Test fun `401 — сигнал паузы гасится`() = runBlocking {
        val api = FakeApi { throw httpError(401) }
        val storage = FakeStorage(bound)
        val signal = BillingPauseSignal()
        signal.set(true)
        val ch = CabinetChannel(api, FakeDao(), storage, signal)
        val outcome = ch.upload(feedback())
        assertEquals(UploadOutcome.REVOKED, outcome)
        assertFalse(signal.paused.value)
    }

    @Test fun `403 BILLING_PAUSED — PAUSED, storage сохраняется`() = runBlocking {
        val api = FakeApi { throw httpError(403, """{"code":"BILLING_PAUSED"}""") }
        val storage = FakeStorage(bound)
        val outcome = channel(api, FakeDao(), storage).upload(feedback())
        assertEquals(UploadOutcome.PAUSED, outcome)
        assertEquals(0, storage.clearCalls)
        assertNotNull(storage.data)
    }

    @Test fun `403 без BILLING_PAUSED — RETRY, storage сохраняется`() = runBlocking {
        val api = FakeApi { throw httpError(403, """{"message":"forbidden"}""") }
        val storage = FakeStorage(bound)
        val outcome = channel(api, FakeDao(), storage).upload(feedback())
        assertEquals(UploadOutcome.RETRY, outcome)
        assertEquals(0, storage.clearCalls)
    }

    @Test fun `сетевая ошибка — RETRY, запись остаётся в очереди`() = runBlocking {
        val api = FakeApi { throw IOException("timeout") }
        val dao = FakeDao(); val storage = FakeStorage(bound)
        val outcome = channel(api, dao, storage).upload(feedback())
        assertEquals(UploadOutcome.RETRY, outcome)
        assertTrue(dao.synced.isEmpty())
    }

    @Test fun `400 и 422 — REJECTED (markError с HTTP-контекстом), запись не синхронизирована`() = runBlocking {
        listOf(400, 422).forEach { code ->
            val api = FakeApi { throw httpError(code) }
            val dao = FakeDao(); val storage = FakeStorage(bound)
            val outcome = channel(api, dao, storage).upload(feedback())

            assertEquals(UploadOutcome.REJECTED, outcome)
            assertTrue(dao.errors.getValue(1L).startsWith("HTTP $code")) // видимость в UI
            assertTrue(dao.synced.isEmpty()) // данные не теряем: запись остаётся в очереди
        }
    }

    @Test fun `drain — ядовитая строка (400) пропускается, остальные уходят (continue, не break)`() = runBlocking {
        var calls = 0
        val api = FakeApi {
            calls++
            if (calls == 1) throw httpError(400) else FeedbackAck("s$calls", "t", "t")
        }
        val dao = FakeDao(pending = listOf(feedback(id = 1), feedback(id = 2, clientKey = "uuid-2")))
        val sent = channel(api, dao, FakeStorage(bound)).drain(dao.getUnsent())

        assertEquals(1, sent) // возврат = 1
        assertEquals(listOf(2L), dao.synced.map { it.first }) // вторая СИНХРОНИЗИРОВАНА
        assertTrue(dao.errors.getValue(1L).startsWith("HTTP 400")) // первой — markError
        assertEquals(2, calls) // вторая отправка не пропущена
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
