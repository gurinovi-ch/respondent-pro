package com.respondent.pro.ui.i18n

import org.junit.Assert.assertTrue
import org.junit.Test

class KioskStringsTest {

    private fun kioskValues(s: AppStrings) = listOf(
        s.kioskTitle, s.kioskStatusOwner, s.kioskStatusLock, s.kioskStatusNoOwner,
        s.kioskAdbSpoiler, s.kioskAdbSteps, s.kioskCmdHint,
        s.kioskQrSpoiler, s.kioskQrSteps, s.kioskQrSsidLabel,
        s.kioskQrPasswordLabel, s.kioskQrShowButton, s.kioskQrShareButton,
        s.kioskQrDiagSpoiler, s.kioskQrDiagRun, s.kioskQrDiagRunning,
        s.kioskQrDiagReady, s.kioskQrDiagNotReady, s.kioskQrDiagApi,
        s.kioskQrDiagScanner, s.kioskQrDiagMp, s.kioskQrDiagNetwork,
        s.kioskQrDiagPayload, s.kioskQrDiagUrl, s.kioskQrDiagNote
    )

    @Test
    fun `kiosk strings are present and non-blank in ru`() {
        assertTrue(kioskValues(ruStrings).all { it.isNotBlank() })
    }

    @Test
    fun `kiosk strings are present and non-blank in en`() {
        assertTrue(kioskValues(enStrings).all { it.isNotBlank() })
    }
}
