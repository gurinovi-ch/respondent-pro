package com.respondent.pro.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSurveyModeTest {

    @Test
    fun `web-опрос только при WEB и непустом пути`() {
        assertTrue(shouldUseWebSurvey("WEB", "/survey/form/abcdef123456"))
        assertFalse(shouldUseWebSurvey("WEB", null))
        assertFalse(shouldUseWebSurvey("WEB", ""))
        assertFalse(shouldUseWebSurvey("APK", "/survey/form/abcdef123456"))
        assertFalse(shouldUseWebSurvey(null, null))
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
    fun `state без startedAt получает текущее время`() {
        val state = WebSurveyState()
        state.onState(BridgeIncoming.State(rating = 5, comment = "", startedAt = null))
        val draft = state.incompleteToSave(sendIncomplete = true)!!
        assertTrue(draft.startedAt > 0L)
    }
}
