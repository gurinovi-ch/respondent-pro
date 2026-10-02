package com.respondent.pro.cabinet

import retrofit2.http.Body
import retrofit2.http.POST

interface CabinetApi {
    /** Обмен одноразового pairing-кода на API-ключ планшета. */
    @POST("tablets/pair")
    suspend fun pair(@Body body: PairRequest): PairResponse
}

data class PairRequest(val pin: String)

data class PairResponse(
    val apiKey: String,
    val tabletId: String,
    val organizationId: String,
    val organizationName: String,
    val pointName: String?
)
