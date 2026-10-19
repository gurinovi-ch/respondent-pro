package com.respondent.pro.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.respondent.pro.cabinet.absoluteLogoUrl
import com.respondent.pro.cabinet.logoScale

/**
 * Логотип организации выше названия: окно 16:9 шириной 70% × ступень ширины экрана
 * (внутри блока 80% это `0.875f × ступень`), картинка вписана (`ContentScale.Fit`),
 * поэтому пропорции исходника целы. `logoUrl = null` → не рисуется ничего, включая
 * зазор: вёрстка без логотипа не сдвигается.
 */
@Composable
fun OrgLogo(
    logoUrl: String?,
    logoSize: Int,
    modifier: Modifier = Modifier,
    gapAfter: Dp = 12.dp,
    /** Доля ширины колонки под окно: 0.875 (70% экрана) в портрете, 0.625 (50%) в ландшафте. */
    widthFraction: Float = 0.875f,
) {
    if (logoUrl.isNullOrBlank()) return
    val url = absoluteLogoUrl(logoUrl)
    Column(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success ->
                        Log.i(TAG, "loaded size=$logoSize scale=${logoScale(logoSize)} frac=$widthFraction from=${state.result.dataSource}")
                    is AsyncImagePainter.State.Error ->
                        Log.i(TAG, "error $url: ${state.result.throwable.message}")
                    else -> Unit
                }
            },
            modifier = Modifier
                .fillMaxWidth(widthFraction * logoScale(logoSize))
                .aspectRatio(16f / 9f),
        )
        Spacer(modifier = Modifier.height(gapAfter))
    }
}

private const val TAG = "OrgLogo"
