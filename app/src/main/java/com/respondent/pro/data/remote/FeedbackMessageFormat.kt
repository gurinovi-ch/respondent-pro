package com.respondent.pro.data.remote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единый формат нативного отзыва (Telegram/e-mail):
 *
 * ```
 * ⭐️⭐️⭐️⭐️⭐️ (5/5)
 * текст
 *
 * 22.10.2026 12:22
 * ```
 *
 * Пустой текст заменяется на «Без комментария». Для Telegram текст экранируется под parse_mode=HTML.
 */
fun formatFeedbackMessage(
    rating: Int,
    text: String,
    createdAt: Long,
    html: Boolean = false,
): String {
    val safeRating = rating.coerceIn(1, 5)
    val body = text.trim().ifEmpty { "Без комментария" }
        .let { if (html) escapeHtml(it) else it }
    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(createdAt))
    return buildString {
        append("⭐️".repeat(safeRating)).append(" (").append(safeRating).append("/5)")
        append('\n').append(body)
        append('\n').append('\n').append(date)
    }
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
