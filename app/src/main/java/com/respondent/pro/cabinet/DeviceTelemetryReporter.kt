package com.respondent.pro.cabinet

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Телеметрия планшета для списка «Планшеты» в кабинете:
 * АКБ/зарядка/сеть. Отправляется раз в 2 минуты, пока приложение живо
 * (киоск всегда на экране). Best-effort: ошибки только логируются.
 */
@Singleton
class DeviceTelemetryReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: CabinetApi,
    private val storage: BindingStorage,
) {
    companion object {
        private const val TAG = "DeviceTelemetry"
        const val NETWORK_WIFI = "WIFI"
        const val NETWORK_CELLULAR = "CELLULAR"

        /** TelephonyManager.NETWORK_TYPE_* → "5G"/"4G"/"3G"/"2G"/null. */
        fun cellularType(networkType: Int): String? = when (networkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            else -> null
        }
    }

    suspend fun send(): Boolean {
        if (storage.read() == null) {
            Log.i(TAG, "Not bound — skip telemetry")
            return false
        }
        return try {
            api.telemetry(collect())
            Log.i(TAG, "Telemetry sent")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.i(TAG, "Telemetry failed: HTTP ${e.code()}")
            false
        } catch (e: IOException) {
            Log.i(TAG, "Telemetry network error")
            false
        } catch (e: Exception) {
            Log.i(TAG, "Telemetry unexpected error")
            false
        }
    }

    fun collect(): TelemetryPayload {
        val (battery, charging) = battery()
        val (type, name, cell) = network()
        return TelemetryPayload(battery, charging, type, name, cell)
    }

    private fun battery(): Pair<Int?, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    @Suppress("DEPRECATION")
    private fun network(): Triple<String?, String?, String?> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return Triple(null, null, null)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                Triple(NETWORK_WIFI, ssid(), null)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                Triple(NETWORK_CELLULAR, null, cellular())
            else -> Triple(null, null, null) // ethernet/VPN не показываем
        }
    }

    /** SSID требует разрешения на геолокацию и включённой геолокации; иначе null. */
    @Suppress("DEPRECATION")
    private fun ssid(): String? = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.connectionInfo?.ssid
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun cellular(): String? = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        tm?.let { cellularType(it.dataNetworkType) }
    } catch (e: Exception) {
        null
    }
}
