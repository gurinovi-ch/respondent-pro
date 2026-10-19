package com.respondent.pro.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import android.webkit.WebView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.respondent.pro.ui.components.SettingsPinDialog
import com.respondent.pro.ui.components.SurveyFooter
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

    // Срез 4: веб-опрос в WebView. Откат — surveyMode=APK на планшете.
    var webFailed by remember { mutableStateOf(false) }
    var keyboardVisible by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var webTimerKey by remember { mutableIntStateOf(0) }
    var webShowPin by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    val webEnabled = shouldUseWebSurvey(settings.surveyMode, settings.surveyPath) && !webFailed

    // Таймер в веб-режиме: «неполный» из последнего состояния страницы + перезагрузка
    val onWebReset: () -> Unit = {
        feedbackViewModel.saveWebIncomplete(settings.sendIncomplete)
        feedbackViewModel.refreshDeviceParams()
        keyboardVisible = false
        reloadKey++
        webTimerKey++
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
        navController.navigate(Screen.Feedback.route) {
            popUpTo(Screen.Feedback.route) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Feedback.route) {
        composable(Screen.Feedback.route) {
            if (webEnabled) {
                WebSurveyScreen(
                    surveyPath = settings.surveyPath,
                    reloadKey = reloadKey,
                    onWebView = { webRef = it },
                    onFeedback = { feedbackViewModel.queueWebFeedback(it) },
                    onState = { feedbackViewModel.onWebState(it) },
                    onKeyboard = { keyboardVisible = it },
                    onLoadFailed = { webFailed = true },
                    footer = {
                        if (keyboardVisible) {
                            WebKeyboard(
                                language = settings.language,
                                onChar = { webRef?.evaluateJavascript(webKeyJs(it), null) },
                                onBackspace = { webRef?.evaluateJavascript(webBackspaceJs(), null) },
                                onEnter = { webRef?.evaluateJavascript(webEnterJs(), null) },
                            )
                        }
                        SurveyFooter(
                            resetTimeout = settings.resetTimeout,
                            timerKey = webTimerKey,
                            onSettingsClick = { webShowPin = true },
                            onTimeout = onWebReset,
                        )
                    },
                )
                SettingsPinDialog(
                    open = webShowPin,
                    title = strings.pinDialogTitle,
                    cancelLabel = strings.btnCancel,
                    verifyPin = feedbackViewModel::verifyPin,
                    onSuccess = {
                        webShowPin = false
                        navController.navigate(Screen.Settings.route)
                    },
                    onDismiss = { webShowPin = false },
                )
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
