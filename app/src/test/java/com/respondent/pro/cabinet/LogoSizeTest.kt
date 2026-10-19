package com.respondent.pro.cabinet

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoSizeTest {

    @Test
    fun `ступени x1…x5 - множители как на сервере`() {
        assertEquals(0.5f, logoScale(1), 0.0001f)
        assertEquals(0.625f, logoScale(2), 0.0001f)
        assertEquals(0.75f, logoScale(3), 0.0001f)
        assertEquals(0.875f, logoScale(4), 0.0001f)
        assertEquals(1f, logoScale(5), 0.0001f)
    }

    @Test
    fun `пусто или мусор - полный размер (x5)`() {
        assertEquals(1f, logoScale(null), 0.0001f)
        assertEquals(1f, logoScale(0), 0.0001f)
        assertEquals(1f, logoScale(6), 0.0001f)
        assertEquals(1f, logoScale(-3), 0.0001f)
    }

    @Test
    fun `normalizeLogoSize - вне 1…5 и null дают 5`() {
        assertEquals(3, normalizeLogoSize(3))
        assertEquals(5, normalizeLogoSize(null))
        assertEquals(5, normalizeLogoSize(0))
        assertEquals(5, normalizeLogoSize(7))
    }

    @Test
    fun `относительный logoUrl достраивается до абсолютного`() {
        assertEquals(
            "https://my.respondent.pro/api/logo/o1?v=123",
            absoluteLogoUrl("/api/logo/o1?v=123"),
        )
    }

    @Test
    fun `абсолютный logoUrl не дублируется`() {
        assertEquals("https://cdn.example/x.png", absoluteLogoUrl("https://cdn.example/x.png"))
    }

    @Test
    fun `старый сервер без полей логотипа - null и полный размер`() {
        val json = """{"mode":"DEFAULT","settings":{"orgName":"A","greeting":"","callToAction":"C","commentHint":"","thankYouText":"T","resetTimeout":60}}"""
        val response = Gson().fromJson(json, DeviceParamsResponse::class.java)

        assertNull(response.settings.logoUrl)
        assertNull(response.settings.logoSize)
        assertEquals(5, normalizeLogoSize(response.settings.logoSize))
        assertEquals(1f, logoScale(normalizeLogoSize(response.settings.logoSize)), 0.0001f)
    }

    @Test
    fun `сервер с логотипом - поля читаются`() {
        val json = """{"mode":"DEFAULT","settings":{"orgName":"A","greeting":"","callToAction":"C","commentHint":"","thankYouText":"T","resetTimeout":60,"logoUrl":"/api/logo/o1?v=9","logoSize":3}}"""
        val settings = Gson().fromJson(json, DeviceParamsResponse::class.java).settings

        assertEquals("/api/logo/o1?v=9", settings.logoUrl)
        assertEquals(3, normalizeLogoSize(settings.logoSize))
        assertEquals(0.75f, logoScale(settings.logoSize), 0.0001f)
    }
}
