package com.respondent.pro.data.remote

import com.respondent.pro.data.model.Feedback

/** Канал доставки отзыва: по одному сообщению и досылкой очереди. */
interface FeedbackChannel {
    /** true — доставлено (или уже было доставлено). */
    suspend fun send(feedback: Feedback): Boolean
    /** Дослать накопленное; возвращает число доставленных. */
    suspend fun sendUnsent(): Int
}
