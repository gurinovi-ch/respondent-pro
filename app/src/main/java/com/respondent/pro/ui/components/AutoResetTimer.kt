package com.respondent.pro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.respondent.pro.ui.theme.Primary
import com.respondent.pro.ui.theme.FooterColor
import kotlinx.coroutines.delay

@Composable
fun AutoResetTimer(
    timeoutSeconds: Int,
    resetTrigger: Any,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableStateOf(1f) }
    var key by remember { mutableStateOf(0) }

    LaunchedEffect(resetTrigger) {
        progress = 1f
        key++
    }

    LaunchedEffect(key) {
        val stepDelay = (timeoutSeconds * 1000L) / 100
        repeat(100) {
            delay(stepDelay)
            progress = 1f - (it + 1) / 100f
        }
        onTimeout()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "timerProgress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = Primary,
        trackColor = FooterColor
    )
}
