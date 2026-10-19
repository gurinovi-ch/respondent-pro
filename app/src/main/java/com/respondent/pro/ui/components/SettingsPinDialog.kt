package com.respondent.pro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/**
 * PIN-диалог доступа к настройкам киоска. Общий для нативных шагов и веб-опроса.
 * Панели системной навигации скрыты и во время показа: окно диалога не наследует
 * immersive-флаги главного окна, и система возвращала панели обратно.
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

    AlertDialog(
        onDismissRequest = { onDismiss(); pinError = false },
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
            PinInput(
                pin = "",
                onPinChanged = { pinError = false },
                onSubmit = { pin ->
                    if (verifyPin(pin)) {
                        pinError = false
                        onSuccess()
                    } else {
                        pinError = true
                    }
                },
                error = pinError,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss(); pinError = false }) {
                Text(cancelLabel)
            }
        },
    )
}
