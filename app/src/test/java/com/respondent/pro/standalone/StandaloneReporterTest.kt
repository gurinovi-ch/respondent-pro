package com.respondent.pro.standalone

import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class StandaloneReporterTest {

    private class FakeStore : StandaloneStore {
        var installId = "11111111-1111-4111-8111-111111111111"
        var token: String? = null
        var lastOrg: String? = null
        val events = mutableListOf<StandaloneEvent>()
        override suspend fun installId() = installId
        override suspend fun token() = token
        override suspend fun saveToken(token: String) { this.token = token }
        override suspend fun clearToken() { token = null }
        override suspend fun lastBoundOrganizationId() = lastOrg
        override suspend fun saveLastBoundOrganizationId(id: String?) { lastOrg = id }
        override suspend fun pending() = events.toList()
        override suspend fun addPending(event: StandaloneEvent) { events += event }
        override suspend fun removePending(keys: Set<String>) { events.removeAll { it.clientKey in keys } }
    }

    private class FakeApi : StandaloneApi {
        var registrations = 0
        var checkIns = 0
        var lastBody: CheckInRequest? = null
        var token: String = "token"
        override suspend fun register(body: RegisterRequest): RegisterResponse {
            registrations++
            return RegisterResponse(token)
        }
        override suspend fun checkIn(token: String, body: CheckInRequest): CheckInResponse {
            checkIns++
            lastBody = body
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
}
