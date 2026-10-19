package com.respondent.pro.webview

import com.google.gson.Gson

/**
 * Сообщения веб-опроса из WebView (мост `RespondentBridge`).
 * Всё, что не проходит валидацию, → null: страница получит `false`,
 * в очередь ничего не попадёт.
 */
sealed interface BridgeIncoming {
    data class Feedback(
        val rating: Int,
        val text: String,
        val startedAt: Long,
        val clientKey: String,
    ) : BridgeIncoming

    data class State(
        val rating: Int,
        val comment: String,
        val startedAt: Long?,
    ) : BridgeIncoming

    data class Keyboard(val visible: Boolean) : BridgeIncoming

    /** Страница не смогла получить данные формы (офлайн без кэша, 404, 5xx) — уходим в нативные шаги. */
    data object FormUnavailable : BridgeIncoming
}

private data class RawMessage(val type: String?, val payload: RawPayload?)

private data class RawPayload(
    val rating: Int?,
    val text: String?,
    val comment: String?,
    val startedAt: Long?,
    val clientKey: String?,
    val visible: Boolean?,
)

fun parseBridgeMessage(json: String): BridgeIncoming? {
    val raw = try {
        Gson().fromJson(json, RawMessage::class.java)
    } catch (_: Exception) {
        null
    } ?: return null
    // «error» — страница не смогла получить данные формы; payload не нужен
    if (raw.type == "error") return BridgeIncoming.FormUnavailable
    val payload = raw.payload ?: return null
    return when (raw.type) {
        "feedback" -> {
            val rating = payload.rating
            val text = payload.text ?: ""
            val key = payload.clientKey
            if (rating == null || rating !in 1..5 || text.length > 500 ||
                key.isNullOrBlank() || key.length > 64
            ) {
                null
            } else {
                BridgeIncoming.Feedback(rating, text, payload.startedAt ?: System.currentTimeMillis(), key)
            }
        }
        "state" -> BridgeIncoming.State(
            rating = (payload.rating ?: 0).coerceIn(0, 5),
            comment = payload.comment ?: "",
            startedAt = payload.startedAt,
        )
        "keyboard" -> BridgeIncoming.Keyboard(payload.visible ?: false)
        else -> null
    }
}
