package com.respondent.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.CircleCloseButton
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.theme.Primary
import kotlinx.coroutines.delay

@Composable
fun ThankYouScreen(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onAutoReset: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    var timerKey by remember { mutableIntStateOf(0) }
    val strings = LocalAppStrings.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Крестик отмены (возврат на главный) — как на главном экране:
        // верх справа, отступ 1% высоты экрана
        val edgeMargin = (LocalConfiguration.current.screenHeightDp * 0.01f).dp
        CircleCloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = edgeMargin, end = edgeMargin)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = settings.thankYouText.ifEmpty { strings.defaultThankYou },
                fontSize = 24.sp,
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center
            )
        }

        // Auto-reset timer
        AutoResetTimer(
            timeoutSeconds = settings.resetTimeout,
            resetTrigger = timerKey,
            onTimeout = { onAutoReset() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
