package com.respondent.pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.data.repository.SettingsRepository
import com.respondent.pro.kiosk.KioskManager
import com.respondent.pro.kiosk.SettingsExcursionOverlay
import com.respondent.pro.kiosk.WatchdogScheduler
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.i18n.appStringsFor
import com.respondent.pro.ui.navigation.NavGraph
import com.respondent.pro.ui.theme.RespondentProTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** В паузе не удерживаем экран включённым: система гасит его по своему таймауту. */
internal fun shouldKeepScreenOn(powered: Boolean, billingPaused: Boolean): Boolean =
    powered && !billingPaused

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var kioskManager: KioskManager

    private var powered = false
    private var billingPaused = false

    /**
     * Экран горит только при подключённом питании: на батарее система сама
     * управляет (экран может погаснуть, работает charge limit / battery saver).
     */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            setKeepScreenOn(isPowered(intent))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SSID в телеметрии: без георазрешения Android отдаёт «<unknown ssid>».
        // На провижининге киоска разрешение обычно выдаётся автоматически.
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
        // Статус питания: первый же broadcast (sticky ACTION_BATTERY_CHANGED)
        // задаёт флаг экрана. RECEIVER_EXPORTED — для system-бroadcast'ов.
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        // Политики DO — идемпотентно, без DO тихо пропускается
        kioskManager.applyPolicies()
        // Перепланируем цепочку стража на каждый запуск (spec §3)
        WatchdogScheduler.scheduleNext(this)
        setContent {
            val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())
            RespondentProTheme {
                // Язык интерфейса применяется мгновенно при выборе в настройках
                CompositionLocalProvider(
                    LocalAppStrings provides appStringsFor(appSettings.language)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavGraph(onBillingPausedChange = ::setBillingPaused)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Оверлей убираем первым — иначе он останется поверх нашего экрана
        SettingsExcursionOverlay.hide(this)
        kioskManager.onActivityResumed(this)
    }

    override fun onPause() {
        kioskManager.onActivityPaused()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
        super.onDestroy()
    }

    /** При подключённом питании — экран активен, но в паузе флаг снимается. */
    private fun setKeepScreenOn(powered: Boolean) {
        this.powered = powered
        applyKeepScreenOn()
    }

    private fun setBillingPaused(paused: Boolean) {
        billingPaused = paused
        applyKeepScreenOn()
    }

    private fun applyKeepScreenOn() {
        val keep = shouldKeepScreenOn(powered, billingPaused)
        if (keep) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        Log.i(TAG, "keepScreenOn=$keep powered=$powered billingPaused=$billingPaused")
    }

    /**
     * Питание подключено (USB/AC/беспроводное) — НЕ «батарея заряжается».
     *
     * Важно: планшет может быть воткнут в USB, но в режиме «зарядка отключена»
     * (функция сохранения батареи, charge limit) — тогда EXTRA_STATUS=DISCHARGING,
     * хотя кабель подключён. Требование — «при подключенном питании экран
     * всегда активен», поэтому ориентируемся на EXTRA_PLUGGED.
     */
    private fun isPowered(intent: Intent?): Boolean =
        (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Киоск: системные панели (навигации и статуса) скрыты. Пере-применяем
        // при каждом получении фокуса — система возвращает панели после
        // диалогов, клавиатуры и экскурсий в системные настройки (spec §9)
        if (hasFocus) hideSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Поворот экрана пере-применяет системные инсеты — без этого окно
        // расширяется до полной высоты, а нижняя полоса остаётся закрытой
        // (контент внизу обрезался). Синхронизируем скрытие панелей.
        hideSystemBars()
    }

    /** Скрыть системные панели (вызывается также из SettingsScreen при выходе). */
    fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.statusBars()
        )
        // Свайп по краю показывает временную панель, которая сама скрывается
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
