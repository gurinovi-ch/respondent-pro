package com.respondent.pro.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardBridgeTest {

    @Test
    fun `кавычка и обратный слэш экранируются`() {
        assertEquals("window.respondentType('\\'')", webKeyJs("'"))
        assertEquals("window.respondentType('\\\\')", webKeyJs("\\"))
    }

    @Test
    fun `юникод и пробел передаются как есть`() {
        assertEquals("window.respondentType('й')", webKeyJs("й"))
        assertEquals("window.respondentType(' ')",
            webKeyJs(" "))
    }

    @Test
    fun `перевод строки и backspace`() {
        assertEquals("window.respondentType('\\n')", webKeyJs("\n"))
        assertEquals("window.respondentBackspace()", webBackspaceJs())
        assertEquals("window.respondentEnter()", webEnterJs())
    }
}
