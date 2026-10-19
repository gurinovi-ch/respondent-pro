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

/**
 * Размер звезды: 0.88 слота из пяти на ширине 80% экрана,
 * но не больше [maxDp] (в ландшафте — 56dp).
 */
fun starSizeDp(screenWidthDp: Float, maxDp: Float?): Float {
    val fromWidth = screenWidthDp * 0.8f / 5f * 0.88f
    return if (maxDp != null) minOf(fromWidth, maxDp) else fromWidth
}
