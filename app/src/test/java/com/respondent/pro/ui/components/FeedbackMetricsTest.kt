package com.respondent.pro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackMetricsTest {

    @Test
    fun `логотип - портрет 70 процентов экрана, ландшафт 50`() {
        assertEquals(0.875f, logoWidthFraction(isLandscape = false), 0.0001f) // 0.875 × блок 80% = 70%
        assertEquals(0.625f, logoWidthFraction(isLandscape = true), 0.0001f)  // 0.625 × блок 80% = 50%
    }

    @Test
    fun `обращение к клиенту - 54sp в портрете, 36sp в ландшафте`() {
        assertEquals(54, ctaFontSizeSp(isLandscape = false))
        assertEquals(36, ctaFontSizeSp(isLandscape = true))
    }

    @Test
    fun `звезда - без потолка размер от ширины, с потолком - не больше потолка`() {
        assertEquals(112.64f, starSizeDp(screenWidthDp = 800f, maxDp = null), 0.01f)
        assertEquals(56f, starSizeDp(screenWidthDp = 1232f, maxDp = 56f), 0.01f)
        // потолок не увеличивает маленький экран
        assertEquals(42.24f, starSizeDp(screenWidthDp = 300f, maxDp = 56f), 0.01f)
    }
}
