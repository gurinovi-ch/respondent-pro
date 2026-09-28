package com.respondent.pro

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
}
