package com.respondent.pro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.PinInput
import com.respondent.pro.ui.components.StarRating
import com.respondent.pro.ui.theme.FooterColor
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.ui.theme.TextSecondary
import com.respondent.pro.viewmodel.FeedbackViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onRatingDone: (Int) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    var timerKey by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Organization name (long press for settings)
            Text(
                text = settings.orgName.ifEmpty { "ORGANIZATION" },
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { viewModel.showPin() }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Greeting
            Text(
                text = settings.greeting,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Call to action
            Text(
                text = settings.callToAction.ifEmpty { "Пожалуйста оцените наши услуги" },
                fontSize = 24.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Star rating
            StarRating(
                rating = rating,
                onRatingChanged = { viewModel.setRating(it); timerKey++ }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Done button
            androidx.compose.material3.Button(
                onClick = { onRatingDone(rating) },
                enabled = rating > 0,
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp)
            ) {
                Text("Готово", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Text(
                text = "RESPONDENT.PRO",
                fontSize = 12.sp,
                color = FooterColor
            )
        }

        // Auto-reset timer
        AutoResetTimer(
            timeoutSeconds = settings.resetTimeout,
            resetTrigger = timerKey,
            onTimeout = { viewModel.resetRating(); timerKey++ },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // PIN dialog
        if (showPinDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.hidePin() },
                title = { Text("Введите PIN") },
                text = {
                    PinInput(
                        pin = "",
                        onPinChanged = {},
                        onSubmit = { /* handled in verifyPin */ },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.hidePin() }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}
