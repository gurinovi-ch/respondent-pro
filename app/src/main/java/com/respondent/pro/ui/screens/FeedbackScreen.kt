package com.respondent.pro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.CircleCloseButton
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
        // Крестик отмены — появляется после выбора оценки, до нажатия «Готово».
        // Отступ = 1% высоты экрана (как был у индикаторов)
        if (rating > 0) {
            val edgeMargin = (LocalConfiguration.current.screenHeightDp * 0.01f).dp
            CircleCloseButton(
                onClick = {
                    viewModel.resetAll()
                    timerKey++
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = edgeMargin, end = edgeMargin)
            )
        }

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

        // Футер, статус-блок и таймер — у нижнего края экрана
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RESPONDENT.PRO",
                fontSize = 18.sp,
                color = FooterColor
            )

            // Равные отступы сверху и снизу от статус-блока;
            // в сумме с статусом и таймером футер поднят ~ на 30% от прежнего отступа
            Spacer(modifier = Modifier.height(9.dp))

            SystemIndicators(onSettingsClick = {
                viewModel.showPin()
                // Таймер автосброса стартует заново: на ввод PIN-кода
                // должно быть полное время, а не остаток от экрана оценки
                timerKey++
            })

            Spacer(modifier = Modifier.height(9.dp))

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
                containerColor = Color.White,
                shape = RoundedCornerShape(10.dp),
                title = { Text(strings.pinDialogTitle) },
                text = {
                    // Панели системной навигации скрыты и во время показа
                    // PIN-диалога: окно диалога не наследует immersive-флаги
                    // главного окна, и система возвращала панели обратно
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
