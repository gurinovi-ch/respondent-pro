package com.respondent.pro.cabinet

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CabinetApi {
    /** Обмен одноразового pairing-кода на API-ключ планшета. */
    @POST("tablets/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse

    /** Отзыв в C web: оценка + комментарий одной записью. */
    @POST("feedbacks")
    suspend fun postFeedback(@Body body: FeedbackPayload): FeedbackAck

    /** Режим и разрешённые параметры устройства (спека tablet-params-sync §4). */
    @GET("tablets/settings")
    suspend fun deviceParams(): DeviceParamsResponse
}

data class PairRequest(val pin: String)

data class PairResponse(
    val apiKey: String,
    val tabletId: String,
    val organizationId: String,
    val organizationName: String,
    val pointName: String?
)

data class FeedbackPayload(
    val rating: Int,
    val text: String,
    /** ISO-8601 UTC время первого касания звезды; null — сервер берёт время приёма. */
    val startedAt: String?,
    val source: String = "APK",
    val sourceRef: String? = null,
    /** UUID строки Room — идемпотентность ретраев. */
    val clientKey: String? = null,
)

data class FeedbackAck(
    val id: String,
    val createdAt: String,
    val startedAt: String,
)

data class DeviceParamsResponse(
    val mode: String,
    val settings: DeviceParams,
)

data class DeviceParams(
    val orgName: String,
    val greeting: String,
    val callToAction: String,
    val commentHint: String,
    val thankYouText: String,
    val resetTimeout: Int,
)
