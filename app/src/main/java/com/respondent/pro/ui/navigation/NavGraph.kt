package com.respondent.pro.ui.navigation

import androidx.compose.runtime.*
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.SettingsPinDialog
import com.respondent.pro.ui.components.SystemIndicators
import com.respondent.pro.ui.components.WebKeyboard
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.screens.*
import com.respondent.pro.viewmodel.CommentViewModel
import com.respondent.pro.viewmodel.FeedbackViewModel
import com.respondent.pro.viewmodel.SettingsViewModel
import com.respondent.pro.webview.WebSurveyScreen
import com.respondent.pro.webview.shouldUseWebSurvey
import com.respondent.pro.webview.webBackspaceJs
import com.respondent.pro.webview.webEnterJs
import com.respondent.pro.webview.webKeyJs

sealed class Screen(val route: String) {
    object Feedback : Screen("feedback")
    object Settings : Screen("settings")
    object Comment : Screen("comment/{rating}") {
        fun createRoute(rating: Int) = "comment/$rating"
    }
    object ThankYou : Screen("thankyou")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val feedbackViewModel: FeedbackViewModel = hiltViewModel()
    val commentViewModel: CommentViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    var currentRating by remember { mutableIntStateOf(0) }
    val settings by feedbackViewModel.settings.collectAsState()
    val strings = LocalAppStrings.current

    // Срез 4: веб-опрос в WebView. Режим определяется привязкой к кабинету
    // и привязанной формой; откат — отвязка планшета или формы.
    var webFailed by remember { mutableStateOf(false) }
    var keyboardVisible by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var webTimerKey by remember { mutableIntStateOf(0) }
    var webShowPin by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val webEnabled = shouldUseWebSurvey(
        feedbackViewModel.isBoundToCabinet(),
        settings.surveyMode,
        settings.surveyPath,
    ) && !webFailed

    // Таймер в веб-режиме: «неполный» из последнего состояния страницы + перезагрузка
    val onWebReset: () -> Unit = {
        Log.i("WebSurvey", "auto-reset: timeout=${settings.resetTimeout}s, reload=#${reloadKey + 1}")
        feedbackViewModel.saveWebIncomplete(settings.sendIncomplete)
        feedbackViewModel.refreshDeviceParams()
        keyboardVisible = false
        reloadKey++
        webTimerKey++
        // Пробуем веб-режим снова после сбоя (иначе нативные шаги «залипают» до перезапуска)
        webFailed = false
    }

    // Shared reset function
    val onAutoReset: () -> Unit = {
        // Таймерный сброс на главный: подтягиваем актуальные параметры устройства,
        // best-effort — не влияет на сам сброс (спека §6, ревизия 05.10)
        feedbackViewModel.refreshDeviceParams()
        // Не сохраняем «неполный» отзыв, если отправка уже идёт —
        // полный отзыв и так будет отправлен, дубля не должно быть
        if (settings.sendIncomplete && !commentViewModel.isSending.value) {
            feedbackViewModel.saveIncompleteFeedback(commentViewModel.getComment())
        } else {
            feedbackViewModel.resetAll()
        }
        commentViewModel.reset()
        webFailed = false
        navController.navigate(Screen.Feedback.route) {
            popUpTo(Screen.Feedback.route) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Feedback.route) {
        composable(Screen.Feedback.route) {
            if (webEnabled) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WebSurveyScreen(
                        surveyPath = settings.surveyPath,
                        reloadKey = reloadKey,
                        onWebView = { webRef = it },
                        onFeedback = { feedback ->
                            feedbackViewModel.queueWebFeedback(feedback).also { webTimerKey++ }
                        },
                        onState = {
                            feedbackViewModel.onWebState(it)
                            // Любое касание на странице (оценка/текст) перезапускает таймер
                            webTimerKey++
                        },
                        onKeyboard = {
                            keyboardVisible = it
                            webTimerKey++
                        },
                        onLoadFailed = { webFailed = true },
                        footer = {
                            // Клавиатура, затем таймер — таймер всегда у нижнего края экрана
                            if (keyboardVisible) {
                                WebKeyboard(
                                    language = settings.language,
                                    onChar = { webRef?.evaluateJavascript(webKeyJs(it), null) },
                                    onBackspace = { webRef?.evaluateJavascript(webBackspaceJs(), null) },
                                    onEnter = { webRef?.evaluateJavascript(webEnterJs(), null) },
                                )
                            }
                            AutoResetTimer(
                                timeoutSeconds = settings.resetTimeout,
                                resetTrigger = webTimerKey,
                                onTimeout = onWebReset,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    // Служебные иконки (PIN) — поверх WebView в левом верхнем углу
                    SystemIndicators(
                        onSettingsClick = {
                            webShowPin = true
                            webTimerKey++
                        },
                        modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
                    )
                    // Закрытие окна PIN (успех/отмена/таймаут) — мягкое обновление контента:
                    // страница перезапрашивает данные формы без перезагрузки (хук respondentRefresh)
                    val closeWebPin: () -> Unit = {
                        webShowPin = false
                        webTimerKey++
                        feedbackViewModel.refreshDeviceParams()
                        webRef?.evaluateJavascript(
                            "window.respondentRefresh && window.respondentRefresh()",
                            null,
                        )
                    }
                    // PIN — оверлей внутри активности (не отдельное окно: панели не дёргаются)
                    SettingsPinDialog(
                        open = webShowPin,
                        title = strings.pinDialogTitle,
                        cancelLabel = strings.btnCancel,
                        verifyPin = feedbackViewModel::verifyPin,
                        onSuccess = {
                            closeWebPin()
                            navController.navigate(Screen.Settings.route)
                        },
                        onDismiss = closeWebPin,
                    )
                }
            } else {
                FeedbackScreen(
                    viewModel = feedbackViewModel,
                    onRatingDone = { rating ->
                        currentRating = rating
                        navController.navigate(Screen.Comment.createRoute(rating))
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onAutoReset = onAutoReset
                )
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onStart = {
                    navController.navigate(Screen.Feedback.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Comment.route) { backStackEntry ->
            val rating = backStackEntry.arguments?.getString("rating")?.toIntOrNull() ?: 0
            CommentScreen(
                viewModel = commentViewModel,
                rating = rating,
                startedAt = feedbackViewModel.getStartedAt(),
                settings = settings,
                onSend = {
                    // Отзыв успешно отправлен — сбрасываем состояние,
                    // чтобы таймер автосброса не отправил его повторно как «неполный»
                    feedbackViewModel.resetAll()
                    navController.navigate(Screen.ThankYou.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                },
                onNoComment = {
                    commentViewModel.sendFeedback(rating, feedbackViewModel.getStartedAt()) {
                        feedbackViewModel.resetAll()
                        navController.navigate(Screen.ThankYou.route) {
                            popUpTo(Screen.Feedback.route) { inclusive = true }
                        }
                    }
                },
                onAutoReset = onAutoReset,
                onClose = {
                    feedbackViewModel.resetAll()
                    commentViewModel.reset()
                    navController.navigate(Screen.Feedback.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ThankYou.route) {
            ThankYouScreen(
                settings = settings,
                onDismiss = {
                    feedbackViewModel.resetAll()
                    commentViewModel.reset()
                    navController.navigate(Screen.Feedback.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                },
                onAutoReset = onAutoReset,
                onClose = {
                    feedbackViewModel.resetAll()
                    commentViewModel.reset()
                    navController.navigate(Screen.Feedback.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
