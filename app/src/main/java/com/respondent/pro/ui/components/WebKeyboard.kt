package com.respondent.pro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.respondent.pro.R
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
    val context = LocalContext.current
    // Высота — из ресурсов (4 ряда + отступы). На планшете values-sw720dp:
    // 4×69 + 2×4 = 284dp; прежние фиксированные 260dp обрезали нижний ряд.
    val kbHeight = with(LocalDensity.current) {
        val res = context.resources
        val natural = 4 * res.getDimension(R.dimen.kb_row_height) +
            2 * res.getDimension(R.dimen.kb_general_padding)
        val h = (maxOf(natural, res.getDimension(R.dimen.kb_max_height)) / density).dp
        h
    }
    AndroidView(
        // Футер прижат к низу (Column: WebView weight(1) → клавиатура → таймер)
        modifier = Modifier
            .fillMaxWidth()
            .height(kbHeight),
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
