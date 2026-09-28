package com.respondent.pro.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogSchedulerTest {

    @Test
    fun `exact alarm allowed before android 12`() {
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 29, canScheduleExact = false))
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 30, canScheduleExact = false))
    }

    @Test
    fun `exact alarm used when permitted on android 12+`() {
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 31, canScheduleExact = true))
        assertEquals(WatchdogScheduler.AlarmMode.EXACT, WatchdogScheduler.decide(sdkInt = 35, canScheduleExact = true))
    }

    @Test
    fun `inexact fallback when exact alarm not permitted on android 12+`() {
        // Review Focus №3
        assertEquals(WatchdogScheduler.AlarmMode.INEXACT, WatchdogScheduler.decide(sdkInt = 31, canScheduleExact = false))
        assertEquals(WatchdogScheduler.AlarmMode.INEXACT, WatchdogScheduler.decide(sdkInt = 35, canScheduleExact = false))
    }
}
