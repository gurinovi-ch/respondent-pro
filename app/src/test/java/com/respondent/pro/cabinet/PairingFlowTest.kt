package com.respondent.pro.cabinet

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PairingFlowTest {

    private class FakeStorage : BindingStorage {
        var data: StoredBinding? = null
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null }
    }

    private class FakeApi(private val fail: Boolean = false) : CabinetApi {
        override suspend fun pair(body: PairRequest): PairResponse {
            if (fail) throw IOException("offline")
            return PairResponse("rpro_k", "t1", "o1", "ООО", "Точка")
        }
        override suspend fun postFeedback(body: FeedbackPayload): FeedbackAck =
            throw UnsupportedOperationException("not used")
        override suspend fun deviceParams(): DeviceParamsResponse =
            throw UnsupportedOperationException("not used")
        override suspend fun telemetry(body: TelemetryPayload): TelemetryAck =
            throw UnsupportedOperationException("not used")
        override suspend fun postLocation(body: LocationPayload): LocationAck =
            throw UnsupportedOperationException("not used")
    }

    @Test
    fun `после успешной привязки обновляет параметры`() = runBlocking {
        var refreshed = 0
        val flow = PairingFlow(CabinetBinder(FakeStorage(), FakeApi())) { refreshed++ }

        assertEquals(PairOutcome.PAIRED, flow.pair("abcd-efgh"))
        assertEquals(1, refreshed)
    }

    @Test
    fun `ошибка обновления параметров не отменяет привязку`() = runBlocking {
        val flow = PairingFlow(CabinetBinder(FakeStorage(), FakeApi())) {
            throw IOException("network")
        }

        assertEquals(PairOutcome.PAIRED, flow.pair("abcd-efgh"))
    }

    @Test
    fun `при ошибке привязки параметры не обновляются`() = runBlocking {
        var refreshed = 0
        val flow = PairingFlow(CabinetBinder(FakeStorage(), FakeApi(fail = true))) { refreshed++ }

        assertEquals(PairOutcome.NETWORK_ERROR, flow.pair("abcd-efgh"))
        assertEquals(0, refreshed)
    }
}
