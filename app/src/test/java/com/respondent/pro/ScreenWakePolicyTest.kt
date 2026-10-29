package com.respondent.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWakePolicyTest {

    @Test
    fun `экран будим только при переходе в режим «держать включённым» и погашенном дисплее`() {
        // Пауза снята, дисплей погас — будим
        assertTrue(shouldWakeScreen(wasKeeping = false, keep = true, interactive = false))
        // Дисплей уже горит — будить нечего
        assertFalse(shouldWakeScreen(wasKeeping = false, keep = true, interactive = true))
        // Остаёмся в паузе / без питания — не будим
        assertFalse(shouldWakeScreen(wasKeeping = false, keep = false, interactive = false))
        // Уже держали экран включённым — повторно не будим
        assertFalse(shouldWakeScreen(wasKeeping = true, keep = true, interactive = false))
        // Ушли в паузу — не будим
        assertFalse(shouldWakeScreen(wasKeeping = true, keep = false, interactive = false))
    }
}
