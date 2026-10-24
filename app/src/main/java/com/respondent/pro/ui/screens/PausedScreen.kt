package com.respondent.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.components.SystemIndicators
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.ui.theme.TextSecondary

@Composable
fun PausedScreen(onSettings: () -> Unit) {
    val strings = LocalAppStrings.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.billingPausedTitle,
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.billingPausedText,
                color = TextSecondary,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
        SystemIndicators(
            onSettingsClick = onSettings,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
        )
    }
}
