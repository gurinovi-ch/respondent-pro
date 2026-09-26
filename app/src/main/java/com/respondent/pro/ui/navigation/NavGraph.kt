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
        if (settings.sendIncomplete) {
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
                    navController.navigate(Screen.ThankYou.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                },
                onNoComment = {
                    commentViewModel.sendFeedback(rating, feedbackViewModel.getStartedAt()) {
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
