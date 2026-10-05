package com.respondent.pro.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.respondent.pro.data.repository.AppSettings
import com.respondent.pro.ui.components.AutoResetTimer
import com.respondent.pro.ui.components.CircleCloseButton
import com.respondent.pro.ui.i18n.LocalAppStrings
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.viewmodel.CommentViewModel
import com.respondent.pro.viewmodel.SendError
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
    val sendError by viewModel.sendError.collectAsState()
    val strings = LocalAppStrings.current
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
                text = settings.commentHint.ifEmpty { strings.defaultCommentHint },
                fontSize = 32.sp,
                lineHeight = 32.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(screenWidth * 0.8f)
                    .padding(top = 8.dp, bottom = 8.dp)
            )

            // EditText for keyboard input - takes remaining space
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        hint = strings.editTextHint
                        textSize = 25.2f
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
            sendError?.let { error ->
                Text(
                    text = when (error) {
                        is SendError.SaveFailed ->
                            strings.errorSavePattern.replace("%s", error.detail ?: "")
                        is SendError.NavigationFailed ->
                            strings.errorNavPattern.replace("%s", error.detail ?: "")
                    },
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
                        // Раскладка клавиатуры — по выбранному языку интерфейса
                        setLayout(
                            if (settings.language == "en") KeyboardView.LAYOUT_ENG
                            else KeyboardView.LAYOUT_RUS
                        )
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

            // Buttons — 40% ширины экрана, высота 56dp; отступ сверху уменьшен,
            // чтобы блок не подпирал низ экрана
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (comment.isEmpty()) {
                    OutlinedButton(
                        onClick = onNoComment,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier
                            .width(screenWidth * 0.4f)
                            .height(56.dp),
                        enabled = !isSending
                    ) {
                        Text(strings.btnNoComment, fontSize = 29.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.sendFeedback(rating, startedAt) { onSend() }
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier
                            .width(screenWidth * 0.4f)
                            .height(56.dp),
                        enabled = !isSending
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(strings.btnSend, fontSize = 29.sp)
                        }
                    }
                }
            }

            // Auto-reset timer
            AutoResetTimer(
                timeoutSeconds = 60,
                resetTrigger = timerKey,
                onTimeout = { onAutoReset() },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Крестик отмены (возврат на главный) — как на главном экране:
        // верх справа, отступ 1% высоты экрана
        val edgeMargin = (LocalConfiguration.current.screenHeightDp * 0.01f).dp
        CircleCloseButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = edgeMargin, end = edgeMargin)
        )
    }
}
