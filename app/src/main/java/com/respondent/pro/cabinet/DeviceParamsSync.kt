package com.respondent.pro.cabinet

import android.util.Log
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Один fetch параметров устройства при старте — best-effort (спека §6):
 * нет привязки → skip; любая ошибка → тихий лог, локальные настройки целы,
 * привязка НЕ отзывается (зона pair/feedbacks).
 */
class DeviceParamsSync(
    private val api: CabinetApi,
    private val storage: BindingStorage,
    private val applyParams: suspend (DeviceParams) -> Unit,
) {
    companion object {
        private const val TAG = "DeviceParamsSync"
    }

    suspend fun fetchAndApply(): Boolean {
        if (storage.read() == null) {
            Log.i(TAG, "Not bound — skip device params fetch")
            return false
        }
        return try {
            val response = api.deviceParams()
            applyParams(response.settings)
            Log.i(TAG, "Device params applied, mode=${response.mode}")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.i(TAG, "Fetch failed: HTTP ${e.code()} — keeping local settings")
            false
        } catch (e: IOException) {
            Log.i(TAG, "Fetch network error — keeping local settings")
            false
        } catch (e: Exception) {
            Log.i(TAG, "Fetch unexpected error — keeping local settings")
            false
        }
    }
}
