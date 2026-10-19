package com.respondent.pro.ui.screens

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.cabinet.logoScale
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.CircleCloseButton
import com.respondent.pro.ui.components.OrgLogo
import com.respondent.pro.ui.components.PinInput
import com.respondent.pro.ui.components.STAR_MAX_LANDSCAPE_DP
import com.respondent.pro.ui.components.cappedLogoWidthPx
import com.respondent.pro.ui.components.contentGapDp
import com.respondent.pro.ui.components.ctaFontSizeSp
import com.respondent.pro.ui.components.logoWidthFraction
import com.respondent.pro.ui.components.SettingsPinDialog
import com.respondent.pro.ui.components.StarRating
import com.respondent.pro.ui.components.SurveyFooter
import com.respondent.pro.ui.components.SystemIndicators
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.theme.FooterColor
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.ui.theme.TextSecondary
import com.respondent.pro.viewmodel.FeedbackViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onRatingDone: (Int) -> Unit,
    onSettings: () -> Unit = {},
    onAutoReset: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val rating by viewModel.rating.collectAsState()
    val showPinDialog by viewModel.showPinDialog.collectAsState()
    val strings = LocalAppStrings.current
    var timerKey by remember { mutableStateOf(0) }
    // Ландшафт: по вертикали не хватает места — уменьшаем только логотип,
    // обращение к клиенту и звёзды; название/приветствие/кнопка/футер без изменений.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val logoFraction = logoWidthFraction(isLandscape)
    val ctaSize = ctaFontSizeSp(isLandscape)
    val gap = contentGapDp(isLandscape)
    // Замеры раскладки: контент центрируется в области над футером, а не в полной высоте —
    // иначе кнопка «Готово» заезжает под RESPONDENT.PRO/индикаторы/таймер.
    val density = LocalDensity.current
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var footerHeightPx by remember { mutableIntStateOf(0) }
    var logoHeightPx by remember { mutableIntStateOf(0) }
    var columnWidthPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }
    // Ландшафт: 50% ширины — стартовая ширина логотипа, но по высоте он не должен
    // выдавливать остальное: если места не хватает, окно ужимается (высота = ширина × 9/16).
    val logoWidthDp: Dp? = if (!isLandscape || columnWidthPx == 0 || screenHeightPx == 0) {
        null
    } else with(density) {
        val desired = logoFraction * columnWidthPx * logoScale(settings.logoSize)
        val other = (contentHeightPx - logoHeightPx).coerceAtLeast(0)
        cappedLogoWidthPx(
            desiredPx = desired,
            availPx = (screenHeightPx - footerHeightPx).toFloat(),
            otherContentPx = other.toFloat(),
            marginPx = 8.dp.toPx(),
        ).toDp()
    }
    LaunchedEffect(isLandscape, contentHeightPx, footerHeightPx, logoHeightPx, columnWidthPx) {
        val contentDp = with(density) { contentHeightPx.toDp().value }
        val footerDp = with(density) { footerHeightPx.toDp().value }
        val logoDp = with(density) { logoHeightPx.toDp().value }
        val screenH = screenHeightDp
        Log.i(
            "FeedbackScreen",
            "fit: landscape=$isLandscape screenH=${screenH}dp footer=${footerDp.roundToInt()}dp " +
                "content=${contentDp.roundToInt()}dp (logo=${logoDp.roundToInt()}dp) " +
                "avail=${(screenH - footerDp).roundToInt()}dp logoSize=${settings.logoSize} " +
                "logoW=${logoWidthDp?.let { with(density) { it.toPx() } }?.roundToInt() ?: -1}px " +
                "logoFrac=$logoFraction cta=${ctaSize}sp gap=${gap}dp starMax=${if (isLandscape) STAR_MAX_LANDSCAPE_DP.toInt() else 0}dp",
        )
    }

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { screenHeightPx = it.size.height }) {
        // Крестик отмены — появляется после выбора оценки, до нажатия «Готово».
        // Отступ = 1% высоты экрана (как был у индикаторов)
        if (rating > 0) {
            val edgeMargin = (LocalConfiguration.current.screenHeightDp * 0.01f).dp
            CircleCloseButton(
                onClick = {
                    viewModel.resetAll()
                    timerKey++
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = edgeMargin, end = edgeMargin)
            )
        }

        // Блок оценки — центр экрана по вертикали и горизонтали, ширина 80%.
        // В ландшафте центрируем в области над футером (90dp), иначе кнопка «Готово»
        // заезжает под RESPONDENT.PRO/индикаторы/таймер.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isLandscape) with(density) { footerHeightPx.toDp() } else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .onGloballyPositioned {
                        contentHeightPx = it.size.height
                        columnWidthPx = it.size.width
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Логотип организации (из кабинета): выше названия, окно 16:9 × ступень размера.
                // Нет логотипа/выключен в кабинете — не рисуется ничего, вёрстка не сдвигается.
                OrgLogo(
                    logoUrl = settings.logoUrl,
                    logoSize = settings.logoSize,
                    widthFraction = logoFraction,
                    width = logoWidthDp,
                    onHeightChanged = { logoHeightPx = it },
                )

                // Organization name (вход в настройки — через шестерёнку справа сверху)
                Text(
                    text = settings.orgName.ifEmpty { "ORGANIZATION" },
                    fontSize = 21.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Greeting
                Text(
                    text = settings.greeting,
                    fontSize = 21.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Call to action
                Text(
                    text = settings.callToAction.ifEmpty { strings.defaultCallToAction },
                    fontSize = ctaSize.sp,
                    lineHeight = ctaSize.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(gap.dp))

                // Star rating — 80% ширины экрана, волна до выбора оценки
                StarRating(
                    rating = rating,
                    onRatingChanged = { viewModel.setRating(it); timerKey++ },
                    maxStarSize = if (isLandscape) STAR_MAX_LANDSCAPE_DP.dp else null
                )

                Spacer(modifier = Modifier.height(gap.dp))

                // Done button — 40% ширины экрана (0.5 от блока 80%)
                androidx.compose.material3.Button(
                    onClick = { onRatingDone(rating) },
                    enabled = rating > 0,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(56.dp)
                ) {
                    Text(strings.btnDone, fontSize = 25.sp)
                }
            }
        }

        // Футер, статус-блок и таймер — у нижнего края экрана (общий с веб-опросом)
        SurveyFooter(
            resetTimeout = settings.resetTimeout,
            timerKey = timerKey,
            onSettingsClick = {
                viewModel.showPin()
                // Таймер автосброса стартует заново: на ввод PIN-кода
                // должно быть полное время, а не остаток от экрана оценки
                timerKey++
            },
            onTimeout = { onAutoReset() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { footerHeightPx = it.size.height },
        )

        // PIN dialog (общий с веб-опросом)
        SettingsPinDialog(
            open = showPinDialog,
            title = strings.pinDialogTitle,
            cancelLabel = strings.btnCancel,
            verifyPin = viewModel::verifyPin,
            onSuccess = onSettings,
            onDismiss = viewModel::hidePin,
        )
    }
}
