package com.respondent.pro.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCheckTest {
    @Test
    fun `верный PIN проходит`() {
        assertTrue(pinMatches("1234", "1234"))
    }

    @Test
    fun `неверный PIN не проходит`() {
        assertFalse(pinMatches("1235", "1234"))
    }

    @Test
    fun `пустой ввод не проходит даже при пустом сохранённом PIN`() {
        assertFalse(pinMatches("", ""))
    }

    @Test
    fun `PIN приводится к валидному или заменяется на 0000`() {
        assertEquals("4321", safePin("4321", "1111"))
        assertEquals("1111", safePin("", "1111"))
        assertEquals("1111", safePin("43a1", "1111"))
        assertEquals("0000", safePin("", ""))
    }
}
