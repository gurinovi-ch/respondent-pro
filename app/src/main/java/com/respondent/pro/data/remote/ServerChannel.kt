package com.respondent.pro.data.remote

import android.util.Log
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.cabinet.CabinetApi
import com.respondent.pro.cabinet.FeedbackPayload
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

enum class UploadOutcome { SYNCED, RETRY, REVOKED }

/** Канал в C web (только для привязанного планшета). */
interface ServerChannel : FeedbackChannel

@Singleton
class CabinetChannel @Inject constructor(
    private val cabinetApi: CabinetApi,
    private val feedbackDao: FeedbackDao,
    private val bindingStorage: BindingStorage,
) : ServerChannel {

    override suspend fun send(feedback: Feedback): Boolean =
        upload(feedback) == UploadOutcome.SYNCED

    override suspend fun sendUnsent(): Int = drain(feedbackDao.getUnsent())

    suspend fun upload(feedback: Feedback): UploadOutcome {
        // Оборонительная проверка: без ключа на сервер не ходим (роутинг и так не пустит)
        if (bindingStorage.read() == null) {
            Log.d(TAG, "Not bound to cabinet — skip upload of ${feedback.id}")
            return UploadOutcome.RETRY
        }
        return try {
            cabinetApi.postFeedback(payload(feedback))
            feedbackDao.markServerSynced(feedback.id, System.currentTimeMillis())
            Log.d(TAG, "Feedback ${feedback.id} uploaded ✓")
            UploadOutcome.SYNCED
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                // Ключ отозван в кабинете: чистим локальное состояние,
                // запись остаётся в очереди до следующей привязки
                Log.w(TAG, "API key revoked (${e.code()}) — unbinding")
                bindingStorage.clear()
                UploadOutcome.REVOKED
            } else {
                Log.w(TAG, "Upload ${feedback.id} failed: HTTP ${e.code()}")
                UploadOutcome.RETRY
            }
        } catch (e: IOException) {
            Log.w(TAG, "Upload ${feedback.id} network error: ${e.message}")
            UploadOutcome.RETRY
        } catch (e: Exception) {
            // Кривой ответ сервера не должен ронять киоск (по образцу CabinetBinder)
            Log.e(TAG, "Upload ${feedback.id} unexpected error", e)
            UploadOutcome.RETRY
        }
    }

    /** Грузит по порядку; на первой ошибке — стоп (следующий цикл дренажа). */
    suspend fun drain(pending: List<Feedback>): Int {
        var sent = 0
        for (feedback in pending) {
            when (upload(feedback)) {
                UploadOutcome.SYNCED -> sent++
                UploadOutcome.RETRY -> break
                UploadOutcome.REVOKED -> break
            }
        }
        Log.d(TAG, "Drain: $sent of ${pending.size} uploaded")
        return sent
    }

    /** Обёртка для тестов: сборка payload из отдельных полей. */
    suspend fun uploadFeedback(
        id: Long = 0L,
        rating: Int = 4,
        text: String = "Отлично",
        startedAt: Long = System.currentTimeMillis(),
        clientKey: String? = "uuid-1",
    ): UploadOutcome = upload(
        Feedback(id = id, rating = rating, text = text, clientKey = clientKey, startedAt = startedAt)
    )

    /** Один объект = оценка + комментарий + время касания + источник (спека §4). */
    private fun payload(feedback: Feedback): FeedbackPayload = FeedbackPayload(
        rating = feedback.rating,
        text = feedback.text,
        startedAt = if (feedback.startedAt > 0) isoUtc(feedback.startedAt) else null,
        source = "APK",
        sourceRef = null,
        clientKey = feedback.clientKey ?: "legacy-${feedback.id}",
    )

    /** ISO-8601 UTC: java.time недоступен на minSdk 24. */
    private fun isoUtc(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))

    companion object { private const val TAG = "CabinetChannel" }
}
