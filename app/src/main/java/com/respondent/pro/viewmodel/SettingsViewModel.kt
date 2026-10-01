package com.respondent.pro.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.respondent.pro.data.remote.TelegramApi
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.data.repository.SettingsRepository
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.KioskStatus
import com.respondent.pro.kiosk.QrDiagnostics
import com.respondent.pro.kiosk.QrDiagnosticsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val telegramApi: TelegramApi,
    private val kioskManager: KioskManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** KioskManager — для excursion-выхода в системные настройки. */
    val kiosk: KioskManager get() = kioskManager

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    /** Результат автоопределения Chat ID */
    private val _chatIdResult = MutableStateFlow<ChatIdResult?>(null)
    val chatIdResult: StateFlow<ChatIdResult?> = _chatIdResult

    /** Статус инфокиоска — обновляется при каждом открытии экрана настроек */
    private val _kioskStatus = MutableStateFlow<KioskStatus?>(null)
    val kioskStatus: StateFlow<KioskStatus?> = _kioskStatus

    fun refreshKioskStatus() {
        _kioskStatus.value = kioskManager.status()
    }

    /** Отключение режима киоска: Lock Task + снятие Device Owner. */
    fun disableKiosk(activity: android.app.Activity) {
        if (kioskManager.disableKiosk(activity)) {
            refreshKioskStatus()
        }
    }

    /** Идёт ли запрос Chat ID */
    private val _isDetectingChatId = MutableStateFlow(false)
    val isDetectingChatId: StateFlow<Boolean> = _isDetectingChatId

    /** Результат диагностики QR-провижининга (null — ещё не запускалась) */
    private val _qrDiagnostics = MutableStateFlow<QrDiagnosticsResult?>(null)
    val qrDiagnostics: StateFlow<QrDiagnosticsResult?> = _qrDiagnostics

    /** Идёт ли диагностика QR-провижининга */
    private val _isRunningQrDiagnostics = MutableStateFlow(false)
    val isRunningQrDiagnostics: StateFlow<Boolean> = _isRunningQrDiagnostics

    /**
     * Диагностика QR-провижининга перед стиранием устройства.
     * Запускается в IO: resolve компонентов, HEAD-запрос URL — блокирующие операции.
     */
    fun runQrDiagnostics() {
        if (_isRunningQrDiagnostics.value) return
        viewModelScope.launch {
            _isRunningQrDiagnostics.value = true
            _qrDiagnostics.value = null
            try {
                val result = withContext(Dispatchers.IO) { buildQrDiagnostics().run() }
                _qrDiagnostics.value = result
                Log.i("SettingsViewModel", "QR diagnostics: ready=${result.ready} " +
                    result.results.joinToString(" ") { "${it.check}=${if (it.ok) "OK" else "FAIL"}" })
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "QR diagnostics failed", e)
            } finally {
                _isRunningQrDiagnostics.value = false
            }
        }
    }

    private fun buildQrDiagnostics() = QrDiagnostics(
        sdkInt = Build.VERSION.SDK_INT,
        hasSetupWizard = {
            resolvesPackage("com.google.android.setupwizard") ||
                resolvesPackage("com.android.setupwizard")
        },
        hasManagedProvisioning = { resolvesManagedProvisioning() },
        networkOk = { networkAvailable() },
        probeUrl = { url -> probeUrl(url) }
    )

    private fun resolvesPackage(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Resolve activity-алиаса, который принимает QR-провижининг (trusted source). */
    private fun resolvesManagedProvisioning(): Boolean {
        val intent = android.content.Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
            .setPackage(PACKAGE_MANAGED_PROVISIONING)
        val resolved = intent.resolveActivity(appContext.packageManager) != null
        return resolved || resolvesPackage(PACKAGE_MANAGED_PROVISIONING)
    }

    private fun networkAvailable(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** HEAD-запрос к URL релиза: 2xx/3xx = ссылка живая (редиректы следуют). */
    private fun probeUrl(url: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = URL_PROBE_TIMEOUT_MS
                readTimeout = URL_PROBE_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            code in 200..399
        } catch (e: Exception) {
            Log.i("SettingsViewModel", "QR diagnostics: URL probe failed: ${e.javaClass.simpleName}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE =
            "android.app.action.PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE"
        private const val PACKAGE_MANAGED_PROVISIONING = "com.android.managedprovisioning"
        private const val URL_PROBE_TIMEOUT_MS = 10_000
    }

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { _settings.value = it }
        }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.save(settings)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.saveLanguage(language)
        }
    }

    fun clearChatIdResult() {
        _chatIdResult.value = null
    }

    fun detectChatId(token: String) {
        if (token.isBlank()) {
            _chatIdResult.value = ChatIdResult.Error("Введите токен бота")
            return
        }

        viewModelScope.launch {
            _isDetectingChatId.value = true
            _chatIdResult.value = null
            try {
                val response = telegramApi.getUpdates(token.trim())
                Log.d("SettingsViewModel", "getUpdates: ok=${response.ok}, result size=${response.result?.size}")

                if (!response.ok) {
                    _chatIdResult.value = ChatIdResult.Error(response.description ?: "Ошибка API")
                    return@launch
                }

                val updates = response.result
                if (updates.isNullOrEmpty()) {
                    _chatIdResult.value = ChatIdResult.NoMessages
                    return@launch
                }

                // Берём chat_id из последнего сообщения
                val chatId = updates
                    .mapNotNull { it.message?.chat?.id }
                    .lastOrNull()

                if (chatId != null) {
                    _chatIdResult.value = ChatIdResult.Success(chatId.toString())
                } else {
                    _chatIdResult.value = ChatIdResult.NoMessages
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "detectChatId failed", e)
                _chatIdResult.value = ChatIdResult.Error(e.message ?: "Network error")
            } finally {
                _isDetectingChatId.value = false
            }
        }
    }
}

sealed class ChatIdResult {
    data class Success(val chatId: String) : ChatIdResult()
    object NoMessages : ChatIdResult()
    data class Error(val message: String) : ChatIdResult()
}
