package com.respondent.pro.cabinet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingPauseSignalTest {

    @Test
    fun `пауза действует только при привязке`() {
        // привязан + персист/сигнал — пауза активна
        assertTrue(effectiveBillingPaused(true, true, false))
        assertTrue(effectiveBillingPaused(true, false, true))
        // отвязан (локальная отвязка или 401) — пауза не покрывает Free-режим
        assertFalse(effectiveBillingPaused(false, true, true))
        assertFalse(effectiveBillingPaused(false, true, false))
        assertFalse(effectiveBillingPaused(false, false, false))
        assertFalse(effectiveBillingPaused(true, false, false))
    }
}
