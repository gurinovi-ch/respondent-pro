package com.respondent.pro.cabinet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinCodeTest {
    @Test fun `ровно 4 цифры проходят`() {
        assertEquals("4321", normalizePinCode("4321"))
    }

    @Test fun `пусто, буквы и другая длина отбрасываются`() {
        assertNull(normalizePinCode(null))
        assertNull(normalizePinCode(""))
        assertNull(normalizePinCode("432"))
        assertNull(normalizePinCode("43210"))
        assertNull(normalizePinCode("43a1"))
        assertNull(normalizePinCode(" 4321"))
    }
}
