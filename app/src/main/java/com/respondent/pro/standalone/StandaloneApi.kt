package com.respondent.pro.standalone

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface StandaloneApi {
    @POST("standalone/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("standalone/check-in")
    suspend fun checkIn(
        @Header("X-Standalone-Token") token: String,
        @Body body: CheckInRequest,
    ): CheckInResponse

    @POST("standalone/unregister")
    suspend fun unregister(@Header("X-Standalone-Token") token: String): UnregisterResponse
}

data class RegisterRequest(
    val installId: String,
    val companyName: String? = null,
    val lastBoundOrganizationId: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val osVersion: String? = null,
    val sdkInt: Int? = null,
    val appVersion: String? = null,
)

data class RegisterResponse(val token: String)

data class CheckInRequest(
    val companyName: String? = null,
    val appVersion: String? = null,
    val lastBoundOrganizationId: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val osVersion: String? = null,
    val sdkInt: Int? = null,
    val activities: List<StandaloneEvent> = emptyList(),
)

data class CheckInResponse(val ok: Boolean)
data class UnregisterResponse(val ok: Boolean)
