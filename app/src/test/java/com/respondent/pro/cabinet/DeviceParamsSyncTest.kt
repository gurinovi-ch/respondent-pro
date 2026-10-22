package com.respondent.pro.cabinet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class DeviceParamsSyncTest {

    private class FakeStorage(var data: StoredBinding?) : BindingStorage {
        var clearCalls = 0
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null; clearCalls++ }
    }

    private class FakeApi(private val respond: suspend () -> DeviceParamsResponse) : CabinetApi {
        var calls = 0
        override suspend fun pair(body: PairRequest): PairResponse =
            throw UnsupportedOperationException("not used")
        override suspend fun postFeedback(body: FeedbackPayload): FeedbackAck =
            throw UnsupportedOperationException("not used")
        override suspend fun deviceParams(): DeviceParamsResponse {
            calls++
            return respond()
        }
        override suspend fun telemetry(body: TelemetryPayload): TelemetryAck =
            throw UnsupportedOperationException("not used")
        override suspend fun postLocation(body: LocationPayload): LocationAck =
            throw UnsupportedOperationException("not used")
    }

    private val bound = StoredBinding("rpro_k", "t1", "o1", "ООО", "Точка")
    private val response = DeviceParamsResponse(
        mode = "CUSTOM",
        settings = DeviceParams(
            orgName = "Кафе", greeting = "Добро", callToAction = "Оцените",
            commentHint = "Отзыв", thankYouText = "Спасибо!", resetTimeout = 45,
        ),
    )

    private fun httpError(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun `привязан и успех - применяются ровно 6 полей`() = runBlocking {
        val storage = FakeStorage(bound)
        val api = FakeApi { response }
        var applied: DeviceParams? = null
        val sync = DeviceParamsSync(api, storage) { p, _, _ -> applied = p }

        val ok = sync.fetchAndApply()

        assertTrue(ok)
        assertEquals(response.settings, applied)
        assertEquals(1, api.calls)
        assertEquals(0, storage.clearCalls)
    }

    @Test
    fun `pinCode проходит через sync в применённые параметры`() = runBlocking {
        val api = FakeApi {
            DeviceParamsResponse(
                mode = "DEFAULT",
                settings = DeviceParams(
                    orgName = "О", greeting = "П", callToAction = "О", commentHint = "К",
                    thankYouText = "С", resetTimeout = 60, pinCode = "4321",
                ),
            )
        }
        var applied: DeviceParams? = null
        DeviceParamsSync(api, FakeStorage(bound)) { p, _, _ -> applied = p }.fetchAndApply()
        assertEquals("4321", applied?.pinCode)
    }

    @Test
    fun `нет привязки - запрос не выполняется`() = runBlocking {
        val storage = FakeStorage(null)
        val api = FakeApi { response }
        var applied = false
        val sync = DeviceParamsSync(api, storage) { _, _, _ -> applied = true }

        val ok = sync.fetchAndApply()

        assertFalse(ok)
        assertEquals(0, api.calls)
        assertFalse(applied)
    }

    @Test
    fun `401 не отзывает привязку и не применяет значения`() = runBlocking {
        val storage = FakeStorage(bound)
        val api = FakeApi { throw httpError(401) }
        var applied = false
        val sync = DeviceParamsSync(api, storage) { _, _, _ -> applied = true }

        val ok = sync.fetchAndApply()

        assertFalse(ok)
        assertFalse(applied)
        assertEquals(0, storage.clearCalls)   // НЕ ревкаем: этот GET не про привязку
        assertTrue(storage.read() != null)
    }

    @Test
    fun `сетевая ошибка - тихий отказ, локальные значения целы`() = runBlocking {
        val storage = FakeStorage(bound)
        val api = FakeApi { throw IOException("timeout") }
        var applied = false
        val sync = DeviceParamsSync(api, storage) { _, _, _ -> applied = true }

        val ok = sync.fetchAndApply()

        assertFalse(ok)
        assertFalse(applied)
        assertEquals(0, storage.clearCalls)
    }
}
