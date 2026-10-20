package com.respondent.pro.webview

/** Веб-опрос включается только у привязанного планшета и при известном пути формы. */
fun shouldUseWebSurvey(isBoundToCabinet: Boolean, surveyMode: String?, surveyPath: String?): Boolean =
    isBoundToCabinet && surveyMode == "WEB" && !surveyPath.isNullOrBlank()

/**
 * Логика веб-опроса на нативной стороне: последнее состояние страницы и решение,
 * что сохранять как «неполный» отзыв при таймауте. Без Android-зависимостей — тестируется JUnit-ом.
 */
class WebSurveyState {
    var last: BridgeIncoming.State? = null
        private set

    fun onState(state: BridgeIncoming.State) {
        last = state
    }

    /** Ответ принят нативной очередью: состояние больше не годится для «неполного». */
    fun markSubmitted() {
        last = null
    }

    /** Черновик для сохранения при таймауте; null — сохранять нечего/не включено. */
    fun incompleteToSave(sendIncomplete: Boolean): FeedbackDraft? {
        val state = last ?: return null
        if (!sendIncomplete || state.rating <= 0) return null
        return FeedbackDraft(
            rating = state.rating,
            text = state.comment,
            startedAt = state.startedAt ?: System.currentTimeMillis(),
        )
    }

    data class FeedbackDraft(val rating: Int, val text: String, val startedAt: Long)
}
