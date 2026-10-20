package com.respondent.pro.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Встроенное автозакрытие окна PIN: 10 секунд, не настраивается. */
private const val PIN_AUTO_CLOSE_SECONDS = 10

/**
 * PIN-диалог доступа к настройкам киоска. Общий для нативных шагов и веб-опроса.
 * Панели системной навигации скрыты и во время показа: окно диалога не наследует
 * immersive-флаги главного окна, и система возвращала панели обратно.
 * Автозакрытие через 10 с со счётчиком; после таймаута — статус «Закрыто автоматически».
 */
@Composable
fun SettingsPinDialog(
    open: Boolean,
    title: String,
    cancelLabel: String,
    verifyPin: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    var pinError by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(PIN_AUTO_CLOSE_SECONDS) }
    var closedByTimeout by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        secondsLeft = PIN_AUTO_CLOSE_SECONDS
        closedByTimeout = false
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        Log.i("PinDialog", "closed: timeout")
        closedByTimeout = true
        delay(800L)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            Log.i("PinDialog", "closed: cancel")
            onDismiss()
            pinError = false
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        title = { Text(title) },
        text = {
            val dialogView = LocalView.current
            SideEffect {
                @Suppress("DEPRECATION")
                dialogView.rootView.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            Column {
                PinInput(
                    pin = "",
                    onPinChanged = { pinError = false },
                    onSubmit = { pin ->
                        if (verifyPin(pin)) {
                            pinError = false
                            Log.i("PinDialog", "closed: success")
                            onSuccess()
                        } else {
                            pinError = true
                        }
                    },
                    error = pinError,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Статус отчёта закрытия: счётчик, затем «Закрыто автоматически»
                Text(
                    text = if (closedByTimeout) "Закрыто автоматически"
                    else "Автозакрытие через $secondsLeft с",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                Log.i("PinDialog", "closed: cancel")
                onDismiss()
                pinError = false
            }) {
                Text(cancelLabel)
            }
        },
    )
}
