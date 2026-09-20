package com.respondent.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.respondent.pro.ui.theme.Primary
import com.respondent.pro.ui.theme.Surface
import com.respondent.pro.ui.theme.TextPrimary

@Composable
fun CustomKeyboard(
    onCharInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEnglish by remember { mutableStateOf(false) }

    val ruLayout = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", ".")
    )

    val enLayout = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m", ",", ".")
    )

    val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val symbols = listOf("!", "@", "#", "$", "%", "&", "*", "-", "+", "=")

    val layout = if (isEnglish) enLayout else ruLayout

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Digits row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            digits.forEach { char ->
                KeyButton(char) { onCharInput(char) }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Letters rows
        layout.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { char ->
                    KeyButton(char) { onCharInput(char) }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Symbols row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            symbols.forEach { char ->
                KeyButton(char) { onCharInput(char) }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Bottom row: lang switch, space, backspace, done
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(if (isEnglish) "EN" else "RU") { isEnglish = !isEnglish }
            Spacer(modifier = Modifier.width(4.dp))
            ActionButton("⌫") { onBackspace() }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary)
                    .clickable { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Готово",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun KeyButton(char: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .size(width = 36.dp, height = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE0E0E0))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            char,
            fontSize = 18.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .size(width = 56.dp, height = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFBDBDBD))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
