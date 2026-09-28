package com.respondent.pro.data.remote

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TelegramApi {

    @FormUrlEncoded
    @POST("/bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String,
        @Field("parse_mode") parseMode: String = "HTML"
    ): TelegramResponse

    @GET("/bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String
    ): GetUpdatesResponse
}

data class TelegramResponse(
    val ok: Boolean,
    val description: String? = null
)

data class GetUpdatesResponse(
    val ok: Boolean,
    val result: List<UpdateItem>? = null,
    val description: String? = null
)

data class UpdateItem(
    val message: MessageItem? = null
)

data class MessageItem(
    val chat: ChatItem? = null
)

data class ChatItem(
    val id: Long? = null,
    val type: String? = null
)
