package com.respondent.pro.kiosk

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Смоук-проверка R7: payload собирается и кодируется в QR-изображение.
 * Robolectric обязателен — encodeQr использует android.graphics.Bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class ProvisioningQrEncodeTest {

    private val url = "https://example.com/releases/latest/download/RESPONDENT.PRO.apk"

    @Test
    fun `encodeQr returns non-null bitmap for built payload`() {
        val payload = ProvisioningQr.buildPayload(url, "prCK-MzGvKNp6nGB80__FmtpGvzJCA5P-c5-_DSi0hw=", "MyNet", "secret1")
        val bitmap = ProvisioningQr.encodeQr(payload)
        assertNotNull("encodeQr must return a non-null bitmap", bitmap)
        val bmp = bitmap!!
        assertTrue(bmp.width > 0)
        assertTrue(bmp.height > 0)
    }
}
