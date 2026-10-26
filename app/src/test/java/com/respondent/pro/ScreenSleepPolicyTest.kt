package com.respondent.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSleepPolicyTest {

    @Test
    fun `в паузе экран не удерживается включённым даже при питании`() {
        assertTrue(shouldKeepScreenOn(powered = true, billingPaused = false))
        assertFalse(shouldKeepScreenOn(powered = true, billingPaused = true))
        assertFalse(shouldKeepScreenOn(powered = false, billingPaused = false))
        assertFalse(shouldKeepScreenOn(powered = false, billingPaused = true))
    }
}
