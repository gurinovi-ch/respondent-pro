package com.respondent.pro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.respondent.pro.R

/** Фирменный шрифт нативных экранов (в веб-опросе шрифт задаёт страница). */
val Gilroy = FontFamily(
    Font(R.font.gilroy_regular, FontWeight.Normal),
    Font(R.font.gilroy_medium, FontWeight.Medium),
    Font(R.font.gilroy_semibold, FontWeight.SemiBold),
    Font(R.font.gilroy_bold, FontWeight.Bold),
)

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Gilroy),
    displayMedium = Base.displayMedium.copy(fontFamily = Gilroy),
    displaySmall = Base.displaySmall.copy(fontFamily = Gilroy),
    headlineLarge = Base.headlineLarge.copy(
        fontFamily = Gilroy,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Gilroy),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Gilroy),
    titleLarge = Base.titleLarge.copy(fontFamily = Gilroy),
    titleMedium = Base.titleMedium.copy(fontFamily = Gilroy),
    titleSmall = Base.titleSmall.copy(fontFamily = Gilroy),
    bodyLarge = Base.bodyLarge.copy(
        fontFamily = Gilroy,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = Base.bodyMedium.copy(
        fontFamily = Gilroy,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = Base.bodySmall.copy(
        fontFamily = Gilroy,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = Base.labelLarge.copy(
        fontFamily = Gilroy,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelMedium = Base.labelMedium.copy(fontFamily = Gilroy),
    labelSmall = Base.labelSmall.copy(fontFamily = Gilroy),
)
