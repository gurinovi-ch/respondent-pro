package com.respondent.pro.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class FeedbackMessageFormatTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `звёзды, текст и дата без лишних полей`() {
        val message = formatFeedbackMessage(
            rating = 5,
            text = "Отличный сервис",
            createdAt = millis(2026, Calendar.OCTOBER, 22, 12, 22),
        )

        assertEquals(
            "⭐️⭐️⭐️⭐️⭐️ (5/5)\nОтличный сервис\n\n22.10.2026 12:22",
            message,
        )
    }

    @Test
    fun `без текста остаётся только оценка и дата`() {
        val message = formatFeedbackMessage(
            rating = 3,
            text = "   ",
            createdAt = millis(2026, Calendar.OCTOBER, 22, 12, 22),
        )

        assertEquals("⭐️⭐️⭐️ (3/5)\n\n22.10.2026 12:22", message)
    }

    @Test
    fun `для telegram текст экранируется как HTML`() {
        val message = formatFeedbackMessage(
            rating = 4,
            text = "цена < 100 & быстро",
            createdAt = millis(2026, Calendar.OCTOBER, 22, 12, 22),
            html = true,
        )

        assertEquals(
            "⭐️⭐️⭐️⭐️ (4/5)\nцена &lt; 100 &amp; быстро\n\n22.10.2026 12:22",
            message,
        )
    }
}
