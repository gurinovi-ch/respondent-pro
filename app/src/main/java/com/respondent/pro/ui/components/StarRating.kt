package com.respondent.pro.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.respondent.pro.ui.theme.StarEmpty
import com.respondent.pro.ui.theme.StarFilled

/**
 * Ряд из 5 звёзд, занимающий 80% ширины экрана.
 *
 * Пока оценка не выбрана (rating == 0) по звёздам идёт волна «прыжков»
 * слева направо (сдвиг 150 мс на звезду); после выбора оценки все звёзды
 * возвращаются к масштабу 1.0 и замирают.
 */
@Composable
fun StarRating(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = context.getSystemService(Vibrator::class.java)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Ряд = 80% экрана; звезда занимает 0.88 слота, SpaceEvenly разнесёт остаток
    val starSize = screenWidth * 0.8f / 5f * 0.88f

    val wave = rememberInfiniteTransition(label = "starWave")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 1..5) {
            val isSelected = i <= rating
            val color by animateColorAsState(
                targetValue = if (isSelected) StarFilled else StarEmpty,
                label = "starColor$i"
            )
            // Волна дыхания со сдвигом старта; после выбора цель — 1.0 (замираем)
            val scale by wave.animateFloat(
                initialValue = 1f,
                targetValue = if (rating == 0) 1.08f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 150)
                ),
                label = "starScale$i"
            )

            Icon(
                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "$i звёзд",
                tint = color,
                modifier = Modifier
                    .size(starSize)
                    .graphicsLayer {
                        // Масштаб только при отрисовке — ширина ряда не меняется
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable {
                        onRatingChanged(i)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        }
                    }
            )
        }
    }
}
