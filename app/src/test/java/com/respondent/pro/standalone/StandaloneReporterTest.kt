package com.respondent.pro.standalone

import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

class StandaloneReporterTest {

    private class FakeStore : StandaloneStore {
        var installId = "11111111-1111-4111-8111-111111111111"
        var token: String? = null
        var lastOrg: String? = null
        var lastTablet: String? = null
        val events = mutableListOf<StandaloneEvent>()
        override suspend fun installId() = installId
        override suspend fun token() = token
        override suspend fun saveToken(token: String) { this.token = token }
        override suspend fun clearToken() { token = null }
        override suspend fun lastBoundOrganizationId() = lastOrg
        override suspend fun saveLastBoundOrganizationId(id: String?) { lastOrg = id }
        override suspend fun lastBoundTabletId() = lastTablet
        override suspend fun saveLastBoundTabletId(id: String?) { lastTablet = id }
        override suspend fun pending() = events.toList()
        override suspend fun addPending(event: StandaloneEvent) { events += event }
        override suspend fun removePending(keys: Set<String>) { events.removeAll { it.clientKey in keys } }
    }

    private class FakeApi : StandaloneApi {
        var registrations = 0
        var checkIns = 0
        var lastBody: CheckInRequest? = null
        val checkInBodies = mutableListOf<CheckInRequest>()
        var fail: (() -> Exception)? = null
        var token: String = "token"
        override suspend fun register(body: RegisterRequest): RegisterResponse {
            registrations++
            return RegisterResponse(token)
        }
        override suspend fun checkIn(token: String, body: CheckInRequest): CheckInResponse {
            checkIns++
            lastBody = body
            checkInBodies += body
            fail?.let { throw it() }
            return CheckInResponse(true)
        }
        override suspend fun unregister(token: String): UnregisterResponse = UnregisterResponse(true)
    }

    private class FakeBinding(var value: StoredBinding?) : BindingStorage {
        override fun read() = value
        override fun write(binding: StoredBinding) { value = binding }
        override fun clear() { value = null }
    }

    private val device = DeviceInfo("Lenovo", "TB-X606X", "10", 29, "1.2.6")
    private val store = FakeStore()
    private val api = FakeApi()
    private val binding = FakeBinding(null)

    private fun reporter() = StandaloneReporter(
        api = api,
        store = store,
        bindingStorage = binding,
        companyName = object : CompanyNameSource {
            override suspend fun companyName() = "ООО Ромашка"
        },
        deviceInfo = object : DeviceInfoSource { override fun info() = device },
    )

    @Test
    fun `не привязан - регистрирует и шлёт компанию и последнюю организацию`() = runBlocking {
        store.lastOrg = "org-1"
        reporter().checkInNow()

        assertEquals(1, api.registrations)
        assertEquals("token", store.token)
        assertEquals("ООО Ромашка", api.lastBody?.companyName)
        assertEquals("org-1", api.lastBody?.lastBoundOrganizationId)
        assertEquals("Lenovo", api.lastBody?.manufacturer)
        assertEquals("1.2.6", api.lastBody?.appVersion)
    }

    @Test
    fun `привязка - сохраняет организацию, снимает устройство и не шлёт check-in`() = runBlocking {
        store.token = "token"
        val b = StoredBinding("api", "t1", "org-9", "ООО", null)
        reporter().handleBinding(b)

        assertEquals("org-9", store.lastOrg)
        assertNull(store.token)
        assertEquals(0, api.checkIns)
    }

    @Test
    fun `событие с рейтингом ставится в очередь и уходит в batch`() = runBlocking {
        binding.value = null
        val feedback = Feedback(id = 1, rating = 5, text = "текст", clientKey = UUID.randomUUID().toString())
        val r = reporter()
        r.recordActivity(feedback)

        assertEquals(1, api.lastBody?.activities?.size)
        assertEquals(5, api.lastBody?.activities?.first()?.rating)
        assertFalse(store.events.isNotEmpty())
    }

    @Test
    fun `при привязке событие не ставится в очередь`() = runBlocking {
        binding.value = StoredBinding("api", "t1", "org", "ООО", null)
        val feedback = Feedback(id = 2, rating = 5, text = "", clientKey = UUID.randomUUID().toString())
        reporter().recordActivity(feedback)
        assertFalse(store.events.isNotEmpty())
    }

    @Test
    fun `отвязка - снова регистрируется с сохранённой организацией`() = runBlocking {
        store.token = "token"
        store.lastOrg = "org-9"
        binding.value = null
        val r = reporter()
        r.handleBinding(StoredBinding("api", "t1", "org-9", "ООО", null))
        r.handleBinding(null)
        r.checkInNow()

        assertEquals("org-9", api.lastBody?.lastBoundOrganizationId)
        assertEquals(1, api.registrations)
    }

    @Test
    fun `привязка сохраняет tabletId, отвязка отправляет его в чек-ине`() = runBlocking {
        store.token = "token"
        val r = reporter()
        r.handleBinding(StoredBinding("api", "t1", "org-9", "ООО", null))
        r.handleBinding(null)
        r.checkInNow()

        assertEquals("t1", store.lastTablet)
        assertEquals("t1", api.lastBody?.lastBoundTabletId)
        assertEquals("org-9", api.lastBody?.lastBoundOrganizationId)
    }

    @Test
    fun `очередь больше 100 уходит чанками по 100`() = runBlocking {
        store.token = "token"
        repeat(150) {
            store.events += StandaloneEvent("key-$it", 5, "2026-10-21T10:00:00.000Z", 0)
        }
        reporter().checkInNow()

        assertEquals(listOf(100, 50), api.checkInBodies.map { it.activities.size })
        assertEquals(0, store.events.size)
    }

    @Test
    fun `пустое название компании отправляется пустой строкой`() = runBlocking {
        store.token = "token"
        val r = StandaloneReporter(
            api = api,
            store = store,
            bindingStorage = binding,
            companyName = object : CompanyNameSource { override suspend fun companyName() = "" },
            deviceInfo = object : DeviceInfoSource { override fun info() = device },
        )
        r.checkInNow()

        assertEquals("", api.lastBody?.companyName)
    }

    @Test
    fun `401 сбрасывает токен и регистрируется заново`() = runBlocking {
        store.token = "old"
        api.fail = {
            HttpException(retrofit2.Response.error<Any>(401, "".toResponseBody(null)))
        }
        val r = reporter()
        r.checkInNow()
        assertNull(store.token)

        api.fail = null
        r.checkInNow()
        assertEquals(1, api.registrations)
        assertEquals("token", store.token)
    }

    @Test
    fun `сетевая ошибка сохраняет событие в очередь`() = runBlocking {
        store.token = "token"
        api.fail = { IOException("offline") }
        reporter().recordActivity(
            Feedback(id = 3, rating = 4, text = "", clientKey = UUID.randomUUID().toString()),
        )

        assertEquals(1, store.events.size)
    }
}
