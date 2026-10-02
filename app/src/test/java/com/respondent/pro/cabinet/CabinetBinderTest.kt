package com.respondent.pro.cabinet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class CabinetBinderTest {

    private class FakeStorage : BindingStorage {
        var data: StoredBinding? = null
        var clearCalls = 0
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null; clearCalls++ }
    }

    private class FakeApi(private val respond: suspend (PairRequest) -> PairResponse) : CabinetApi {
        var calls = 0
        var lastBody: PairRequest? = null
        override suspend fun pair(body: PairRequest): PairResponse {
            calls++
            lastBody = body
            return respond(body)
        }
    }

    private val ok = PairResponse("rpro_k", "t1", "o1", "ООО Ромашка", "Точка")

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test fun `normalize strips spaces, dashes and case`() {
        assertEquals("ABCDEFGH", normalizePairingCode(" abcd-efgh "))
        assertEquals("23456789", normalizePairingCode("2345-6789"))
    }

    @Test fun `normalize rejects wrong length, forbidden letters and cyrillic`() {
        assertNull(normalizePairingCode("abc"))
        assertNull(normalizePairingCode("abcd1234"))   // цифра 1 запрещена
        assertNull(normalizePairingCode("abcd12345"))  // 9 символов
        assertNull(normalizePairingCode("абвгд"))
    }

    @Test fun `pair success sends normalized pin and stores binding`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { ok }
        val outcome = CabinetBinder(storage, api).pair("abcd-efgh")
        assertEquals(PairOutcome.PAIRED, outcome)
        assertEquals("ABCDEFGH", api.lastBody?.pin)
        assertEquals("rpro_k", storage.data?.apiKey)
        assertEquals("Точка", storage.data?.pointName)
        assertEquals(1, api.calls)
    }

    @Test fun `invalid format fails locally without network call`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { ok }
        assertEquals(PairOutcome.INVALID_CODE, CabinetBinder(storage, api).pair("1"))
        assertEquals(0, api.calls)
        assertNull(storage.data)
    }

    @Test fun `http 400 maps to INVALID_CODE nothing stored`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { throw httpError(400) }
        assertEquals(PairOutcome.INVALID_CODE, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertNull(storage.data)
    }

    @Test fun `http 401 maps to REVOKED and clears storage`() = runBlocking {
        val storage = FakeStorage()
        storage.data = StoredBinding("rpro_old", "t1", "o1", "Org", null)
        val api = FakeApi { throw httpError(401) }
        assertEquals(PairOutcome.REVOKED, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertEquals(1, storage.clearCalls)
    }

    @Test fun `io exception maps to NETWORK_ERROR storage untouched`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { throw IOException("timeout") }
        assertEquals(PairOutcome.NETWORK_ERROR, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertEquals(0, storage.clearCalls)
    }

    @Test fun `unexpected runtime exception maps to NETWORK_ERROR without crash`() = runBlocking {
        val storage = FakeStorage()
        val api = FakeApi { throw IllegalStateException("malformed json response") }
        assertEquals(PairOutcome.NETWORK_ERROR, CabinetBinder(storage, api).pair("abcd-efgh"))
        assertNull(storage.data)
    }

    @Test fun `double submit while first in flight returns BUSY`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val api = FakeApi { gate.await(); ok }
        val binder = CabinetBinder(FakeStorage(), api)
        val first = async { binder.pair("abcd-efgh") }
        yield()
        assertEquals(PairOutcome.BUSY, binder.pair("abcd-efgh"))
        gate.complete(Unit)
        assertEquals(PairOutcome.PAIRED, first.await())
        assertEquals(1, api.calls)
    }

    @Test fun `unbindLocal clears storage`() {
        val storage = FakeStorage()
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        CabinetBinder(storage, FakeApi { ok }).unbindLocal()
        assertNull(storage.data)
    }

    @Test fun `isRevoked clears only on 401 403`() {
        val storage = FakeStorage()
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        val binder = CabinetBinder(storage, FakeApi { ok })
        assertTrue(binder.isRevoked(401))
        assertNull(storage.data)
        storage.data = StoredBinding("k", "t", "o", "Org", null)
        assertEquals(false, binder.isRevoked(500))
        assertNotNull(storage.data)
    }
}
