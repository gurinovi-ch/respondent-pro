package com.respondent.pro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.theme.FooterColor

/**
 * Нижний блок киоска: RESPONDENT.PRO, индикаторы (шестерёнка → PIN) и таймер автосброса.
 * Общий для нативных шагов и веб-опроса в WebView (срез 4).
 */
@Composable
fun SurveyFooter(
    resetTimeout: Int,
    timerKey: Int,
    onSettingsClick: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RESPONDENT.PRO",
            fontSize = 18.sp,
            color = FooterColor,
        )
        Spacer(modifier = Modifier.height(9.dp))
        SystemIndicators(onSettingsClick = onSettingsClick)
        Spacer(modifier = Modifier.height(9.dp))
        AutoResetTimer(
            timeoutSeconds = resetTimeout,
            resetTrigger = timerKey,
            onTimeout = onTimeout,
        )
    }
}
