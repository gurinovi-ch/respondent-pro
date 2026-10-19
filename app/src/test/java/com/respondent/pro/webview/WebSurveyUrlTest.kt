package com.respondent.pro.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSurveyUrlTest {

    @Test
    fun `путь есть - абсолютный URL от ORIGIN`() {
        assertEquals(
            "https://my.respondent.pro/survey/form/abcdef123456",
            surveyUrl("/survey/form/abcdef123456"),
        )
    }

    @Test
    fun `пути нет - null (нативные шаги)`() {
        assertNull(surveyUrl(null))
        assertNull(surveyUrl(""))
    }

    @Test
    fun `чужой origin не открываем`() {
        assertFalse(isAllowedSurveyUrl("https://evil.example/survey/form/x"))
        assertFalse(isAllowedSurveyUrl("http://my.respondent.pro/survey/form/x"))
        assertTrue(isAllowedSurveyUrl("https://my.respondent.pro/survey/form/x"))
    }
}
