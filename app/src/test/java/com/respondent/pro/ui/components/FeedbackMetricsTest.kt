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
    fun `отступы вокруг звёзд - 32dp в портрете, 16dp в ландшафте`() {
        assertEquals(32, contentGapDp(isLandscape = false))
        assertEquals(16, contentGapDp(isLandscape = true))
    }

    @Test
    fun `звезда - без потолка размер от ширины, с потолком - не больше потолка`() {
        assertEquals(112.64f, starSizeDp(screenWidthDp = 800f, maxDp = null), 0.01f)
        // ландшафт 1232dp: от ширины вышло бы 173dp, потолок оставляет 112dp
        assertEquals(112f, starSizeDp(screenWidthDp = 1232f, maxDp = STAR_MAX_LANDSCAPE_DP), 0.01f)
        // потолок не увеличивает маленький экран
        assertEquals(42.24f, starSizeDp(screenWidthDp = 300f, maxDp = STAR_MAX_LANDSCAPE_DP), 0.01f)
    }

    @Test
    fun `логотип при нехватке высоты ужимается, при запасе - остаётся желаемым`() {
        // места вдоволь: желаемые 640px остаются
        assertEquals(640f, cappedLogoWidthPx(640f, availPx = 980f, otherContentPx = 300f, marginPx = 12f), 0.01f)
        // высоты мало: до футера 980, прочее 700, отступ 12 → 268px высоты = 476.4px ширины
        assertEquals(
            476.4f,
            cappedLogoWidthPx(640f, availPx = 980f, otherContentPx = 700f, marginPx = 12f),
            0.5f,
        )
        // места нет вовсе — не отрицательная ширина
        assertEquals(0f, cappedLogoWidthPx(640f, availPx = 400f, otherContentPx = 400f, marginPx = 12f), 0.01f)
    }
}
