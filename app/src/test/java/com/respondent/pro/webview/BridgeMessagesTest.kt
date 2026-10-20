package com.respondent.pro.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessagesTest {

    @Test
    fun `feedback - валидный payload парсится`() {
        val msg = parseBridgeMessage(
            """{"type":"feedback","payload":{"rating":5,"text":"ок","startedAt":1000,"clientKey":"k1"}}""",
        )
        assertEquals(BridgeIncoming.Feedback(5, "ок", 1000, "k1"), msg)
    }

    @Test
    fun `feedback - rating вне 1…5 отбрасывается`() {
        assertNull(parseBridgeMessage("""{"type":"feedback","payload":{"rating":0,"text":"","startedAt":1,"clientKey":"k"}}"""))
        assertNull(parseBridgeMessage("""{"type":"feedback","payload":{"rating":6,"text":"","startedAt":1,"clientKey":"k"}}"""))
    }

    @Test
    fun `feedback - текст длиннее 500 и пустой clientKey - null`() {
        val long = "я".repeat(501)
        val longKey = "k".repeat(65)
        assertNull(parseBridgeMessage("""{"type":"feedback","payload":{"rating":5,"text":"$long","startedAt":1,"clientKey":"k"}}"""))
        assertNull(parseBridgeMessage("""{"type":"feedback","payload":{"rating":5,"text":"","startedAt":1,"clientKey":""}}"""))
        assertNull(parseBridgeMessage("""{"type":"feedback","payload":{"rating":5,"text":"","startedAt":1,"clientKey":"$longKey"}}"""))
    }

    @Test
    fun `state и keyboard парсятся`() {
        assertEquals(
            BridgeIncoming.State(3, "привет", 7),
            parseBridgeMessage("""{"type":"state","payload":{"rating":3,"comment":"привет","startedAt":7}}"""),
        )
        assertEquals(
            BridgeIncoming.Keyboard(true),
            parseBridgeMessage("""{"type":"keyboard","payload":{"visible":true}}"""),
        )
    }

    @Test
    fun `state - рейтинг вне диапазона приводится к 0…5`() {
        assertEquals(
            BridgeIncoming.State(0, "", null),
            parseBridgeMessage("""{"type":"state","payload":{"rating":-3,"comment":"","startedAt":null}}"""),
        )
        assertEquals(
            BridgeIncoming.State(5, "", null),
            parseBridgeMessage("""{"type":"state","payload":{"rating":9,"comment":"","startedAt":null}}"""),
        )
    }

    @Test
    fun `keyboard без visible - false`() {
        assertEquals(
            BridgeIncoming.Keyboard(false),
            parseBridgeMessage("""{"type":"keyboard","payload":{}}"""),
        )
    }

    @Test
    fun `feedback без startedAt - подставляется текущее время`() {
        val before = System.currentTimeMillis()
        val msg = parseBridgeMessage(
            """{"type":"feedback","payload":{"rating":4,"text":"","clientKey":"k"}}""",
        ) as BridgeIncoming.Feedback
        assertTrue(msg.startedAt >= before)
    }

    @Test
    fun `ошибка загрузки формы от страницы - FormUnavailable`() {
        assertEquals(
            BridgeIncoming.FormUnavailable,
            parseBridgeMessage("""{"type":"error"}"""),
        )
    }

    @Test
    fun `мусор и неизвестный тип - null`() {
        assertNull(parseBridgeMessage("not json"))
        assertNull(parseBridgeMessage("""{"type":"unknown"}"""))
        assertNull(parseBridgeMessage("""{"type":"feedback"}"""))
    }
}
