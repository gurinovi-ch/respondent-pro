package com.respondent.pro.data.repository

import android.app.Application
import com.respondent.pro.cabinet.DeviceParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class SettingsRepositoryPinTest {

    private fun params(pin: String?) = DeviceParams(
        orgName = "О", greeting = "П", callToAction = "О", commentHint = "К",
        thankYouText = "С", resetTimeout = 60, pinCode = pin,
    )

    @Test
    fun `валидный PIN пишется, невалидный и null не затирают локальный`() = runBlocking {
        val repo = SettingsRepository(RuntimeEnvironment.getApplication())

        repo.applyDeviceParams(params("4321"))
        assertEquals("4321", repo.settings.first().pinCode)

        repo.applyDeviceParams(params("12"))
        assertEquals("4321", repo.settings.first().pinCode)

        repo.applyDeviceParams(params(null))
        assertEquals("4321", repo.settings.first().pinCode)
    }
}
