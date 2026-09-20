package com.respondent.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.components.CustomKeyboard
import com.respondent.pro.ui.theme.TextPrimary
import com.respondent.pro.viewmodel.CommentViewModel

@Composable
fun CommentScreen(
    viewModel: CommentViewModel,
    rating: Int,
    onSend: () -> Unit,
    onNoComment: () -> Unit
) {
    val comment by viewModel.comment.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val settings = remember { mutableStateOf(com.respondent.pro.data.repository.AppSettings()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Comment hint
        Text(
            text = settings.value.commentHint.ifEmpty { "Напишите ваш отзыв" },
            fontSize = 14.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Text field (read-only, displays custom keyboard input)
        OutlinedTextField(
            value = comment,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            placeholder = { Text("Ваш отзыв...") }
        )

        // Character counter
        Text(
            text = "${comment.length}/500",
            fontSize = 12.sp,
            color = TextPrimary.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.End
        )

        // Custom keyboard
        CustomKeyboard(
            onCharInput = { viewModel.updateComment(comment + it) },
            onBackspace = {
                if (comment.isNotEmpty()) {
                    viewModel.updateComment(comment.dropLast(1))
                }
            },
            onDone = { /* hide keyboard */ },
            modifier = Modifier.padding(top = 8.dp)
        )

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (comment.isEmpty()) {
                OutlinedButton(
                    onClick = onNoComment,
                    modifier = Modifier.weight(1f),
                    enabled = !isSending
                ) {
                    Text("Без комментария")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.sendFeedback(rating) { onSend() }
                    },
                    modifier = Modifier.weight(1f),
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
    }
}
