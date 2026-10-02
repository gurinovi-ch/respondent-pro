package com.respondent.pro.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.theme.TextSecondary

/**
 * Индикаторы рабочего режима киоска (справа сверху):
 * WiFi (перечёркнут без интернета), заряд батареи в %, шестерёнка.
 * Вся строка — тап-зона для входа в настройки через PIN.
 */
@Composable
fun SystemIndicators(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasInternet by remember { mutableStateOf(true) }
    var batteryPercent by remember { mutableStateOf(-1) }

    // Интернет: активная сеть с подтверждённой валидацией (реально доступен)
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.i("SystemIndicators", "internet validated=$validated")
                hasInternet = validated
            }

            override fun onLost(network: Network) {
                Log.i("SystemIndicators", "internet lost")
                hasInternet = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // Батарея: sticky receiver, обновление при изменении уровня
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent = (level * 100) / scale
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(
        modifier = modifier.clickable(onClick = onSettingsClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (hasInternet) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(12.dp)
        )

        if (batteryPercent >= 0) {
            Text(
                text = "$batteryPercent%",
                fontSize = 8.sp,
                color = TextSecondary.copy(alpha = 0.5f)
            )
        }

        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(12.dp)
        )
    }
}
