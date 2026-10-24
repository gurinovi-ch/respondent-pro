package com.respondent.pro.cabinet

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApiKeyInterceptorTest {

    private lateinit var server: MockWebServer

    private class FakeStorage(var data: StoredBinding?) : BindingStorage {
        override fun read(): StoredBinding? = data
        override fun write(binding: StoredBinding) { data = binding }
        override fun clear() { data = null }
    }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun callWith(storage: FakeStorage, code: Int = 204): String? {
        // Без заготовленного ответа QueueDispatcher блокирует запрос до таймаута OkHttp.
        server.enqueue(MockResponse().setResponseCode(code))
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(storage))
            .build()
        client.newCall(Request.Builder().url(server.url("/api/feedbacks")).build()).execute().close()
        return server.takeRequest().getHeader("X-API-Key")
    }

    @Test fun `привязанный планшет — заголовок X-API-Key из BindingStorage`() {
        val storage = FakeStorage(StoredBinding("rpro_key", "t1", "o1", "ООО", null))
        assertEquals("rpro_key", callWith(storage))
    }

    @Test fun `без привязки — заголовок не отправляется`() {
        assertNull(callWith(FakeStorage(null)))
    }

    @Test fun `401 — ключ погашен, привязка очищается`() {
        val storage = FakeStorage(StoredBinding("rpro_key", "t1", "o1", "ООО", null))
        callWith(storage, 401)
        assertNull(storage.data)
    }

    @Test fun `403 — привязка сохраняется`() {
        val storage = FakeStorage(StoredBinding("rpro_key", "t1", "o1", "ООО", null))
        callWith(storage, 403)
        assertEquals("rpro_key", storage.data?.apiKey)
    }

    @Test fun `500 — привязка сохраняется`() {
        val storage = FakeStorage(StoredBinding("rpro_key", "t1", "o1", "ООО", null))
        callWith(storage, 500)
        assertEquals("rpro_key", storage.data?.apiKey)
    }
}
