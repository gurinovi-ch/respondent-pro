package com.respondent.pro.ui.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinetStringsTest {

    private fun values(s: AppStrings) = listOf(
        s.kabinetCardTitle, s.kabinetStatusUnbound, s.kabinetStatusBound,
        s.kabinetStatusBoundPoint, s.kabinetCodeLabel, s.kabinetBindButton,
        s.kabinetBinding, s.kabinetUnbindButton, s.kabinetHint,
        s.kabinetErrorInvalid, s.kabinetErrorNetwork, s.kabinetRevoked
    )

    @Test
    fun `cabinet strings non-blank in ru and en`() {
        assertTrue(values(ruStrings).all { it.isNotBlank() })
        assertTrue(values(enStrings).all { it.isNotBlank() })
    }

    @Test
    fun `ru differs from en for every key`() {
        values(ruStrings).zip(values(enStrings)).forEach { (ru, en) ->
            assertFalse("ru and en must differ: $ru", ru == en)
        }
    }

    @Test
    fun `bound formats contain placeholders`() {
        assertTrue(ruStrings.kabinetStatusBound.contains("%s"))
        assertTrue(ruStrings.kabinetStatusBoundPoint.contains("%1\$s"))
        assertTrue(ruStrings.kabinetStatusBoundPoint.contains("%2\$s"))
    }
}
