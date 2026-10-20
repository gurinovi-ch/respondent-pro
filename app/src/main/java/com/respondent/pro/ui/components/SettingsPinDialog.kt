package com.respondent.pro.ui.components

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Встроенное автозакрытие окна PIN: 10 секунд, не настраивается. */
private const val PIN_AUTO_CLOSE_SECONDS = 10

/**
 * PIN-доступ к настройкам киоска. Общий для нативных шагов и веб-опроса.
 *
 * Раньше это был AlertDialog — отдельное окно, которое не наследует immersive-флаги
 * активности: при показе возвращался статус-бар, высота WebView менялась на 24dp и
 * контент «прыгал». Теперь это оверлей внутри активности (без отдельного окна),
 * поэтому системные панели не дёргаются.
 *
 * Автозакрытие через 10 с со счётчиком; по таймауту — статус «Закрыто автоматически».
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

    val closeCancel: () -> Unit = {
        Log.i("PinDialog", "closed: cancel")
        onDismiss()
        pinError = false
    }

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

    BackHandler { closeCancel() }

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = closeCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(24.dp)
                // Поглощаем тапы по карточке, чтобы они не закрывали окно
                .clickable(interactionSource = cardInteraction, indication = null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = closeCancel) {
                    Text(cancelLabel)
                }
            }
        }
    }
}
