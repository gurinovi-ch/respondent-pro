package com.respondent.pro.cabinet

/**
 * Правила размера логотипа — зеркало серверных (срез 3 C web):
 * ступень 1…5, x1 вдвое меньше x5, шаги равные. Держим рядом с [KabinetConfig],
 * потому что это часть контракта с сервером, а не оформления экрана.
 */

/** Ступень 1…5; null/вне диапазона → 5 (полный размер — как было до среза). */
fun normalizeLogoSize(raw: Int?): Int = if (raw != null && raw in 1..5) raw else 5

/** Множитель окна логотипа по ступени: x1 = 0.5 … x5 = 1. */
fun logoScale(size: Int?): Float = when (size) {
    1 -> 0.5f
    2 -> 0.625f
    3 -> 0.75f
    4 -> 0.875f
    else -> 1f
}

/** Сервер отдаёт относительный путь `/api/logo/...`; к нему добавляем корень кабинета. */
fun absoluteLogoUrl(logoUrl: String): String =
    if (logoUrl.startsWith("http")) logoUrl else KabinetConfig.ORIGIN + logoUrl
