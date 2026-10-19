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
        // Высота клавиатуры: 4 ряда по факту занимают ~253dp (ряд 47.5dp + отступы),
        // при 210dp нижний ряд уходил за экран; 260dp — с запасом.
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
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
