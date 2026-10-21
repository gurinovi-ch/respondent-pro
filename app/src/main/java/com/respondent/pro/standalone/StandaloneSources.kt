package com.respondent.pro.standalone

import android.content.Context
import android.os.Build
import com.respondent.pro.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val osVersion: String?,
    val sdkInt: Int,
    val appVersion: String?,
)

interface DeviceInfoSource { fun info(): DeviceInfo }
interface CompanyNameSource { suspend fun companyName(): String }

@Singleton
class AndroidDeviceInfoSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceInfoSource {
    override fun info(): DeviceInfo {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = pkg.versionName,
        )
    }
}

@Singleton
class SettingsCompanyNameSource @Inject constructor(
    private val repository: SettingsRepository,
) : CompanyNameSource {
    override suspend fun companyName(): String =
        repository.settings.first().orgName
}
