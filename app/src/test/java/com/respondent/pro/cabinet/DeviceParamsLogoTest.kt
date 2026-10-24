package com.respondent.pro.cabinet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Логотип идёт тем же путём, что тексты: сервер → DeviceParamsSync → применение. */
class DeviceParamsLogoTest {

    private class FakeStorage(var data: StoredBinding?) : BindingStorage {
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null }
    }

    private class FakeApi(private val response: DeviceParamsResponse) : CabinetApi {
        override suspend fun pair(body: PairRequest): PairResponse =
            throw UnsupportedOperationException("not used")
        override suspend fun postFeedback(body: FeedbackPayload): FeedbackAck =
            throw UnsupportedOperationException("not used")
        override suspend fun deviceParams(): DeviceParamsResponse = response
        override suspend fun telemetry(body: TelemetryPayload): TelemetryAck =
            throw UnsupportedOperationException("not used")
        override suspend fun postLocation(body: LocationPayload): LocationAck =
            throw UnsupportedOperationException("not used")
    }

    private val bound = StoredBinding("rpro_k", "t1", "o1", "ООО", "Точка")

    @Test
    fun `логотип и ступень доходят до применения`() = runBlocking {
        val response = DeviceParamsResponse(
            mode = "CUSTOM",
            settings = DeviceParams(
                orgName = "Кафе", greeting = "", callToAction = "Оцените", commentHint = "",
                thankYouText = "Спасибо", resetTimeout = 45,
                logoUrl = "/api/logo/o1?v=7", logoSize = 3,
            ),
        )
        var applied: DeviceParams? = null
        val sync = DeviceParamsSync(FakeApi(response), FakeStorage(bound)) { p, _, _, _ -> applied = p }

        assertTrue(sync.fetchAndApply())
        assertEquals("/api/logo/o1?v=7", applied?.logoUrl)
        assertEquals(3, applied?.logoSize)
    }

    @Test
    fun `логотип выключен на сервере - null приходит как null`() = runBlocking {
        val response = DeviceParamsResponse(
            mode = "DEFAULT",
            settings = DeviceParams(
                orgName = "Кафе", greeting = "", callToAction = "Оцените", commentHint = "",
                thankYouText = "Спасибо", resetTimeout = 45,
                logoUrl = null, logoSize = null,
            ),
        )
        var applied: DeviceParams? = null
        val sync = DeviceParamsSync(FakeApi(response), FakeStorage(bound)) { p, _, _, _ -> applied = p }

        assertTrue(sync.fetchAndApply())
        assertNull(applied?.logoUrl)
        assertNull(applied?.logoSize)
    }
}
