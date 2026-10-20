package com.respondent.pro.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSurveyModeTest {

    @Test
    fun `web-опрос только у привязанного планшета с WEB и непустым путём`() {
        assertTrue(shouldUseWebSurvey(true, "WEB", "/survey/form/abcdef123456"))
        assertFalse(shouldUseWebSurvey(false, "WEB", "/survey/form/abcdef123456"))
        assertFalse(shouldUseWebSurvey(true, "WEB", null))
        assertFalse(shouldUseWebSurvey(true, "WEB", ""))
        assertFalse(shouldUseWebSurvey(true, "APK", "/survey/form/abcdef123456"))
        assertFalse(shouldUseWebSurvey(false, null, null))
    }

    @Test
    fun `неполный отзыв - только при sendIncomplete и rating больше 0`() {
        val state = WebSurveyState()
        assertNull(state.incompleteToSave(sendIncomplete = true)) // состояния ещё нет

        state.onState(BridgeIncoming.State(rating = 4, comment = "черновик", startedAt = 10L))
        assertNull(state.incompleteToSave(sendIncomplete = false))
        assertEquals(
            WebSurveyState.FeedbackDraft(rating = 4, text = "черновик", startedAt = 10L),
            state.incompleteToSave(sendIncomplete = true),
        )

        state.onState(BridgeIncoming.State(rating = 0, comment = "", startedAt = null))
        assertNull(state.incompleteToSave(sendIncomplete = true))
    }

    @Test
    fun `после отправки таймаут не создаёт повторный неполный`() {
        val state = WebSurveyState()
        state.onState(BridgeIncoming.State(rating = 5, comment = "ок", startedAt = 1L))
        state.markSubmitted()
        assertNull(state.incompleteToSave(sendIncomplete = true))
        // следующий визит: страница присылает пустое состояние — тоже нечего сохранять
        state.onState(BridgeIncoming.State(rating = 0, comment = "", startedAt = null))
        assertNull(state.incompleteToSave(sendIncomplete = true))
    }

    @Test
    fun `state без startedAt получает текущее время`() {
        val state = WebSurveyState()
        state.onState(BridgeIncoming.State(rating = 5, comment = "", startedAt = null))
        val draft = state.incompleteToSave(sendIncomplete = true)!!
        assertTrue(draft.startedAt > 0L)
    }
}
