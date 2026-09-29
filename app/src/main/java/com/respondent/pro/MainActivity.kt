package com.respondent.pro

import android.content.res.Configuration
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var kioskManager: KioskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Экран никогда не гаснет (киоск, spec §5)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                        NavGraph()
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
