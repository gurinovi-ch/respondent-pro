package com.respondent.pro.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.respondent.pro.ui.screens.*
import com.respondent.pro.viewmodel.CommentViewModel
import com.respondent.pro.viewmodel.FeedbackViewModel
import com.respondent.pro.viewmodel.SettingsViewModel

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
