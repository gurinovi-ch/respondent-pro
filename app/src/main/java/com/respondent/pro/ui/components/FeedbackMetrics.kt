package com.respondent.pro.ui.components

/**
 * Метрики стартового экрана R apk. В ландшафте по вертикали не хватает места,
 * поэтому уменьшаются только логотип, обращение к клиенту и звёзды;
 * название, приветствие, кнопка и футер сохраняют портретные размеры.
 */

/** Доля ширины колонки 80% под окно логотипа: портрет — 70% экрана, ландшафт — 50%. */
fun logoWidthFraction(isLandscape: Boolean): Float = if (isLandscape) 0.625f else 0.875f

/** Кегль обращения к клиенту: портрет 54sp, ландшафт 36sp. */
fun ctaFontSizeSp(isLandscape: Boolean): Int = if (isLandscape) 36 else 54

/** Потолок размера звезды в ландшафте: звезда того же размера, что в портрете на 800dp. */
const val STAR_MAX_LANDSCAPE_DP = 112f

/** Отступы вокруг звёзд: портрет 32dp, ландшафт 16dp (место под крупные звёзды). */
fun contentGapDp(isLandscape: Boolean): Int = if (isLandscape) 16 else 32

/**
 * Размер звезды: 0.88 слота из пяти на ширине 80% экрана,
 * но не больше [maxDp] (в ландшафте — [STAR_MAX_LANDSCAPE_DP]).
 */
fun starSizeDp(screenWidthDp: Float, maxDp: Float?): Float {
    val fromWidth = screenWidthDp * 0.8f / 5f * 0.88f
    return if (maxDp != null) minOf(fromWidth, maxDp) else fromWidth
}

/**
 * Ширина окна логотипа, когда по высоте не хватает места: берём желаемую (50% экрана),
 * но не шире, чем остаётся над футером после остальных элементов ([otherContentPx]) и
 * отступа [marginPx]. Пропорция 16:9 → высота = ширина × 9/16.
 */
fun cappedLogoWidthPx(
    desiredPx: Float,
    availPx: Float,
    otherContentPx: Float,
    marginPx: Float,
): Float {
    val maxHeightPx = (availPx - otherContentPx - marginPx).coerceAtLeast(0f)
    return minOf(desiredPx, maxHeightPx * 16f / 9f)
}
