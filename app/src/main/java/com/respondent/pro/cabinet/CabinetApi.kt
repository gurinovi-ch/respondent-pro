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

    /** Телеметрия: АКБ/зарядка/сеть. Сервер обновляет lastSeenAt по этому запросу. */
    @POST("tablets/telemetry")
    suspend fun telemetry(@Body body: TelemetryPayload): TelemetryAck

    /** Геолокация планшета — точка на карте в A web. */
    @POST("tablets/location")
    suspend fun postLocation(@Body body: LocationPayload): LocationAck
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
    /** "APK" — нативные шаги, "WEB" — веб-опрос в WebView (срез 4). */
    val surveyMode: String? = null,
    /** Путь веб-опроса (`/survey/form/<code>`); null — формы нет. */
    val surveyPath: String? = null,
    val settings: DeviceParams,
)

data class DeviceParams(
    val orgName: String,
    val greeting: String,
    val callToAction: String,
    val commentHint: String,
    val thankYouText: String,
    val resetTimeout: Int,
    /**
     * Логотип организации: относительный путь `/api/logo/<orgId>?v=<ms>` или null
     * (логотипа нет ИЛИ включатель в кабинете выключен). Nullable: Gson оставляет null,
     * если поля в ответе нет (старый сервер).
     */
    val logoUrl: String? = null,
    /** Ступень размера 1…5 (x1 вдвое меньше x5); нет поля → null → полный размер. */
    val logoSize: Int? = null,
)

/** Тело POST /tablets/telemetry: null-поля Gson не отправляет. */
data class TelemetryPayload(
    val batteryLevel: Int?,
    val isCharging: Boolean,
    val networkType: String?,   // "WIFI" | "CELLULAR"
    val networkName: String?,   // SSID (только Wi-Fi)
    val cellularType: String?,  // "5G" | "4G" | "3G" | "2G"
)

data class TelemetryAck(val success: Boolean)

/** Тело POST /tablets/location: сервер берёт планшет из API-ключа. */
data class LocationPayload(val latitude: Double, val longitude: Double)

data class LocationAck(val id: String)
