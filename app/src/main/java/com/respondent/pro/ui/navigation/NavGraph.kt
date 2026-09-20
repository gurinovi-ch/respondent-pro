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

    NavHost(navController = navController, startDestination = Screen.Feedback.route) {
        composable(Screen.Feedback.route) {
            FeedbackScreen(
                viewModel = feedbackViewModel,
                onRatingDone = { rating ->
                    currentRating = rating
                    navController.navigate(Screen.Comment.createRoute(rating))
                }
            )
        }

        composable(Screen.Comment.route) { backStackEntry ->
            val rating = backStackEntry.arguments?.getString("rating")?.toIntOrNull() ?: 0
            CommentScreen(
                viewModel = commentViewModel,
                rating = rating,
                onSend = {
                    navController.navigate(Screen.ThankYou.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                },
                onNoComment = {
                    commentViewModel.sendFeedback(rating) {
                        navController.navigate(Screen.ThankYou.route) {
                            popUpTo(Screen.Feedback.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.ThankYou.route) {
            val settings by feedbackViewModel.settings.collectAsState()
            ThankYouScreen(
                settings = settings,
                onDismiss = {
                    feedbackViewModel.resetRating()
                    commentViewModel.reset()
                    navController.navigate(Screen.Feedback.route) {
                        popUpTo(Screen.Feedback.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
