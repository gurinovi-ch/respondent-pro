package com.respondent.pro.standalone

import android.util.Log
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.StoredBinding
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandaloneReporter @Inject constructor(
    private val api: StandaloneApi,
    private val store: StandaloneStore,
    private val bindingStorage: BindingStorage,
    private val companyName: CompanyNameSource,
    private val deviceInfo: DeviceInfoSource,
) {
    companion object {
        private const val TAG = "StandaloneReporter"
        private const val HEARTBEAT_MS = 120_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var heartbeat: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            bindingStorage.observe().collectLatest { handleBinding(it) }
        }
    }

    suspend fun handleBinding(binding: StoredBinding?) {
        if (binding != null) {
            store.saveLastBoundOrganizationId(binding.organizationId)
            store.saveLastBoundTabletId(binding.tabletId)
            heartbeat?.cancel()
            heartbeat = null
            unregister()
        } else {
            heartbeat?.cancel()
            if (started) {
                heartbeat = scope.launch {
                    while (isActive) {
                        runCatching { checkInNow() }
                        delay(HEARTBEAT_MS)
                    }
                }
            }
        }
    }

    suspend fun recordActivity(feedback: Feedback) {
        if (bindingStorage.read() != null) return
        try {
            store.addPending(feedback.toEvent())
            checkInNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "activity report failed", e)
        }
    }

    suspend fun checkInNow() = mutex.withLock {
        if (bindingStorage.read() != null) return@withLock
        val token = ensureToken() ?: return@withLock
        val info = deviceInfo.info()
        val base = CheckInRequest(
            companyName = companyName.companyName(),
            appVersion = info.appVersion,
            lastBoundOrganizationId = store.lastBoundOrganizationId(),
            lastBoundTabletId = store.lastBoundTabletId(),
            manufacturer = info.manufacturer,
            model = info.model,
            osVersion = info.osVersion,
            sdkInt = info.sdkInt,
        )
        val chunks = store.pending().chunked(100).ifEmpty { listOf(emptyList()) }
        for (chunk in chunks) {
            try {
                api.checkIn(token, base.copy(activities = chunk))
                if (chunk.isNotEmpty()) {
                    store.removePending(chunk.map { it.clientKey }.toSet())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 401) store.clearToken()
                Log.i(TAG, "check-in failed: HTTP ${e.code()}")
                return@withLock
            } catch (e: IOException) {
                Log.i(TAG, "check-in network error")
                return@withLock
            } catch (e: Exception) {
                Log.i(TAG, "check-in unexpected error", e)
                return@withLock
            }
        }
    }

    suspend fun unregister() {
        val token = store.token() ?: return
        try {
            api.unregister(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "unregister failed", e)
        } finally {
            store.clearToken()
        }
    }

    private suspend fun ensureToken(): String? {
        store.token()?.let { return it }
        val info = deviceInfo.info()
        return try {
            val token = api.register(
                RegisterRequest(
                    installId = store.installId(),
                    companyName = companyName.companyName(),
                    lastBoundOrganizationId = store.lastBoundOrganizationId(),
                    lastBoundTabletId = store.lastBoundTabletId(),
                    manufacturer = info.manufacturer,
                    model = info.model,
                    osVersion = info.osVersion,
                    sdkInt = info.sdkInt,
                    appVersion = info.appVersion,
                ),
            ).token
            store.saveToken(token)
            token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.i(TAG, "register failed", e)
            null
        }
    }

    private fun Feedback.toEvent(): StandaloneEvent {
        val millis = if (startedAt > 0) startedAt else createdAt
        val occurred = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
        val offset = TimeZone.getDefault().getOffset(millis) / 60_000
        return StandaloneEvent(
            clientKey = clientKey ?: UUID.randomUUID().toString(),
            rating = rating,
            occurredAt = occurred,
            tzOffsetMin = offset,
        )
    }
}
