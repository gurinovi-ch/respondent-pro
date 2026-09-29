package com.respondent.pro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.PinInput
import com.respondent.pro.ui.components.StarRating
import com.respondent.pro.ui.components.SystemIndicators
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.theme.FooterColor
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.ui.theme.TextSecondary
import com.respondent.pro.viewmodel.FeedbackViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onRatingDone: (Int) -> Unit,
    onSettings: () -> Unit = {},
    onAutoReset: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val strings = LocalAppStrings.current
    var timerKey by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Индикаторы рабочего режима: WiFi / заряд / вход в настройки (PIN).
        // Отступ = 1% высоты экрана, одинаковый сверху и справа
        val edgeMargin = (LocalConfiguration.current.screenHeightDp * 0.01f).dp
        SystemIndicators(
            onSettingsClick = { viewModel.showPin() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = edgeMargin, end = edgeMargin)
        )

        // Блок оценки — центр экрана по вертикали и горизонтали, ширина 80%
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Organization name (вход в настройки — через шестерёнку справа сверху)
                Text(
                    text = settings.orgName.ifEmpty { "ORGANIZATION" },
                    fontSize = 21.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Greeting
                Text(
                    text = settings.greeting,
                    fontSize = 21.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Call to action
                Text(
                    text = settings.callToAction.ifEmpty { strings.defaultCallToAction },
                    fontSize = 54.sp,
                    lineHeight = 54.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Star rating — 80% ширины экрана, волна до выбора оценки
                StarRating(
                    rating = rating,
                    onRatingChanged = { viewModel.setRating(it); timerKey++ }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Done button — 40% ширины экрана (0.5 от блока 80%)
                androidx.compose.material3.Button(
                    onClick = { onRatingDone(rating) },
                    enabled = rating > 0,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(56.dp)
                ) {
                    Text(strings.btnDone, fontSize = 25.sp)
                }
            }
        }

        // Футер и таймер — у нижнего края экрана
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RESPONDENT.PRO",
                fontSize = 18.sp,
                color = FooterColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Auto-reset timer
            AutoResetTimer(
                timeoutSeconds = settings.resetTimeout,
                resetTrigger = timerKey,
                onTimeout = { onAutoReset() }
            )
        }

        // PIN dialog
        if (showPinDialog) {
            var pinError by remember { mutableStateOf(false) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.hidePin(); pinError = false },
                title = { Text(strings.pinDialogTitle) },
                text = {
                    PinInput(
                        pin = "",
                        onPinChanged = { pinError = false },
                        onSubmit = { pin ->
                            if (viewModel.verifyPin(pin)) {
                                pinError = false
                                onSettings()
                            } else {
                                pinError = true
                            }
                        },
                        error = pinError,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.hidePin(); pinError = false }
                    ) {
                        Text(strings.btnCancel)
                    }
                }
            )
        }
    }
}
