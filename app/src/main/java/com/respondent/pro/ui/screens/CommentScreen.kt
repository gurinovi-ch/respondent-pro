package com.respondent.pro.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.viewmodel.CommentViewModel
import com.respondent.pro.views.KeyboardView

@Composable
fun CommentScreen(
    viewModel: CommentViewModel,
    rating: Int,
    startedAt: Long,
    settings: AppSettings,
    onSend: () -> Unit,
    onNoComment: () -> Unit,
    onAutoReset: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val comment by viewModel.comment.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var timerKey by remember { mutableIntStateOf(0) }
    var editTextRef by remember { mutableStateOf<EditText?>(null) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val keyboardMaxHeight = if (isLandscape) screenHeight * 0.50f else screenHeight * 0.25f

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Comment hint from settings
            Text(
                text = settings.commentHint.ifEmpty { "Ваш комментарий (отзыв) к оценке" },
                fontSize = 32.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(screenWidth * 0.8f)
                    .padding(bottom = 8.dp)
            )

            // EditText for keyboard input - takes remaining space
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        hint = "Ваш отзыв..."
                        textSize = 18f
                        maxLines = 8
                        minLines = 4
                        setPadding(32, 32, 32, 32)
                        // Block system keyboard — we use our own KeyboardView
                        showSoftInputOnFocus = false
                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(windowToken, 0)
                            }
                        }
                        setOnClickListener { timerKey++ }
                        setOnKeyListener { _, _, _ -> timerKey++; false }
                        post {
                            editTextRef = this
                        }
                    }
                },
                update = { editText ->
                    if (editText.text.toString() != comment) {
                        editText.setText(comment)
                        editText.setSelection(comment.length)
                    }
                },
                modifier = Modifier
                    .width(screenWidth * 0.8f)
                    .weight(1f)
                    .padding(bottom = 4.dp)
            )

            // Character counter
            Text(
                text = "${comment.length}/500",
                fontSize = 12.sp,
                color = TextPrimary.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = TextAlign.End
            )

            // Error message display
            errorMessage?.let { error ->
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
            }

            // Native keyboard - max 25% of screen height
            AndroidView(
                factory = { context ->
                    KeyboardView(context).apply {
                        post {
                            editTextRef?.let { setInputText(it) }
                        }
                        setOnClickListener {
                            timerKey++
                            editTextRef?.let { et ->
                                val text = et.text.toString()
                                if (text.length <= 500) {
                                    viewModel.updateComment(text)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = keyboardMaxHeight)
                    .wrapContentHeight()
            )

            // Buttons — 50% ширины экрана, высота ×2 (80dp), по центру
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (comment.isEmpty()) {
                    OutlinedButton(
                        onClick = onNoComment,
                        modifier = Modifier
                            .width(screenWidth * 0.5f)
                            .height(80.dp),
                        enabled = !isSending
                    ) {
                        Text("Без комментария")
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.sendFeedback(rating, startedAt) { onSend() }
                        },
                        modifier = Modifier
                            .width(screenWidth * 0.5f)
                            .height(80.dp),
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Отправить")
                        }
                    }
                }
            }

            // Auto-reset timer
            AutoResetTimer(
                timeoutSeconds = 60,
                resetTrigger = timerKey,
                onTimeout = { onAutoReset() },
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Close button (X) - top right
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text("✕", fontSize = 24.sp, color = Color.Gray)
        }
    }
}
