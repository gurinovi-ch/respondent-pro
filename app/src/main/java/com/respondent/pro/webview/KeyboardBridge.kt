package com.respondent.pro.webview

/**
 * JS-команды для веб-опроса: нативная клавиатура печатает в страницу
 * (WebView), а не в EditText. Экранируем только то, что ломает строку.
 */
fun webKeyJs(ch: String): String {
    val escaped = ch
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")
    return "window.respondentType('$escaped')"
}

fun webBackspaceJs(): String = "window.respondentBackspace()"

fun webEnterJs(): String = "window.respondentEnter()"
