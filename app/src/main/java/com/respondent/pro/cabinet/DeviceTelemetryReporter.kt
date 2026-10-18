package com.respondent.pro.cabinet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Точка геолокации: последний известный фикс одного из провайдеров. */
data class GeoFix(val latitude: Double, val longitude: Double, val timeMs: Long)

/** Самая свежая точка из набора last-known (GPS/сеть/passive). */
fun newestFix(fixes: List<GeoFix>): GeoFix? = fixes.maxByOrNull { it.timeMs }

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

    /** Геолокация: last-known фикс (GPS/сеть/passive) → POST /tablets/location. */
    suspend fun sendLocation(): Boolean {
        if (storage.read() == null) {
            Log.i(TAG, "Not bound — skip location")
            return false
        }
        val fix = lastKnownFix() ?: playServicesFix() ?: requestFix() ?: run {
            Log.i(TAG, "No location fix")
            return false
        }
        return try {
            api.postLocation(LocationPayload(fix.latitude, fix.longitude))
            Log.i(TAG, "Location sent")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Log.i(TAG, "Location failed: HTTP ${e.code()}")
            false
        } catch (e: IOException) {
            Log.i(TAG, "Location network error")
            false
        } catch (e: Exception) {
            Log.i(TAG, "Location unexpected error")
            false
        }
    }

    private fun lastKnownFix(): GeoFix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fixes = listOf(
            LocationManager.GPS_PROVIDER,
            "fused", // Google Play fused (если зарегистрирован на устройстве)
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)?.let { GeoFix(it.latitude, it.longitude, it.time) }
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return newestFix(fixes)
    }

    /** Fused-локатор Google Play: Wi-Fi/сота, работает в помещении (15 с, BALANCED). */
    @SuppressLint("MissingPermission")
    private suspend fun playServicesFix(): GeoFix? {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return null
        }
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val token = CancellationTokenSource().token
            val location = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
            location?.let { GeoFix(it.latitude, it.longitude, it.time) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Активный одноразовый фикс (15 с): нужен, когда last-known пуст (гео только включили). */
    @SuppressLint("MissingPermission")
    private suspend fun requestFix(): GeoFix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { p ->
                try { lm.isProviderEnabled(p) } catch (e: Exception) { false }
            } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) {
                            cont.resume(GeoFix(location.latitude, location.longitude, location.time))
                        }
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                try {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
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
