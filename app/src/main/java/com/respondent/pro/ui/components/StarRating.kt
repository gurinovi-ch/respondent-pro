package com.respondent.pro.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.respondent.pro.ui.theme.StarEmpty
import com.respondent.pro.ui.theme.StarFilled

@Composable
fun StarRating(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = context.getSystemService(Vibrator::class.java)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..5) {
            val isSelected = i <= rating
            val color by animateColorAsState(
                targetValue = if (isSelected) StarFilled else StarEmpty,
                label = "starColor$i"
            )

            Icon(
                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "$i звёзд",
                tint = color,
                modifier = Modifier
                    .size(64.dp)
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
