package com.respondent.pro.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPolicyTest {

    private val pkg = "com.respondent.pro"

    @Test
    fun `no device owner — no actions`() {
        assertTrue(KioskPolicy.actions(isDeviceOwner = false, pkg = pkg).isEmpty())
    }

    @Test
    fun `with device owner — full lockdown set`() {
        val actions = KioskPolicy.actions(isDeviceOwner = true, pkg = pkg)
        assertTrue(actions.contains(PolicyAction.HideStatusBar))
        assertTrue(actions.contains(PolicyAction.DisableKeyguard))
        assertTrue(actions.contains(PolicyAction.BlockFactoryReset))
        assertTrue(actions.contains(PolicyAction.BlockSafeBoot))
        assertTrue(actions.contains(PolicyAction.MaxScreenTime))
        assertTrue(actions.contains(PolicyAction.ProtectApp(pkg)))
        assertTrue(actions.contains(PolicyAction.WhitelistLockTask(pkg)))
    }

    @Test
    fun `restart when backgrounded without excursion`() {
        assertTrue(RestartPolicy.shouldRestart(isForeground = false, excursionActive = false))
    }

    @Test
    fun `no restart when foreground`() {
        assertFalse(RestartPolicy.shouldRestart(isForeground = true, excursionActive = false))
    }

    @Test
    fun `no restart during settings excursion`() {
        // Review Focus №2: watchdog не должен вытаскивать из системных настроек
        assertFalse(RestartPolicy.shouldRestart(isForeground = false, excursionActive = true))
    }
}
