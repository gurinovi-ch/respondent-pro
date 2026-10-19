package com.respondent.pro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.respondent.pro.views.KeyboardView

/**
 * Нативная клавиатура для веб-опроса: те же клавиши, но символы уходят
 * в страницу через JS-мост (системная клавиатура не вызывается).
 */
@Composable
fun WebKeyboard(
    language: String,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
) {
    AndroidView(
        // Высота клавиатуры ограничена: в корне keyboard.xml есть ряды match_parent,
        // поэтому без ограничения она растягивается на весь экран, а при 30% обрезался
        // нижний ряд. 210dp = 4 ряда по 47.5dp + отступы.
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        factory = { context ->
            KeyboardView(context).apply {
                setLayout(if (language == "en") KeyboardView.LAYOUT_ENG else KeyboardView.LAYOUT_RUS)
                bindWeb(object : KeyboardView.WebTarget {
                    override fun onChar(ch: String) = onChar(ch)
                    override fun onBackspace() = onBackspace()
                    override fun onEnter() = onEnter()
                })
            }
        },
    )
}
