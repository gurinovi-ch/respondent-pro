package com.respondent.pro.webview

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.respondent.pro.cabinet.KabinetConfig

private const val TAG = "WebSurvey"
const val BRIDGE_OBJECT = "RespondentBridge"
/** URL опроса для веб-режима; null → планшет остаётся на нативных шагах. */
fun surveyUrl(surveyPath: String?): String? =
    if (surveyPath.isNullOrBlank()) null else KabinetConfig.ORIGIN + surveyPath

/** Разрешён только наш origin (и только https). */
fun isAllowedSurveyUrl(url: String): Boolean =
    url.startsWith("${KabinetConfig.ORIGIN}/")

/**
 * Экран веб-опроса в WebView (срез 4). Киоск-логика (футер, таймер, PIN) — снаружи,
 * в [footer]; страница общается с нативным слоем только через мост [BRIDGE_OBJECT]:
 *  - feedback → [onFeedback] (очередь Room; возвращает true → странице уходит ack);
 *  - state → [onState] (последнее состояние для таймаута/«неполных»);
 *  - keyboard → [onKeyboard] (показ нативной клавиатуры).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSurveyScreen(
    surveyPath: String?,
    reloadKey: Int,
    onWebView: (WebView) -> Unit = {},
    onFeedback: suspend (BridgeIncoming.Feedback) -> Boolean,
    onState: (BridgeIncoming.State) -> Unit,
    onKeyboard: (Boolean) -> Unit,
    onLoadFailed: () -> Unit,
    footer: @Composable () -> Unit,
) {
    val url = surveyUrl(surveyPath)
    if (url == null) {
        onLoadFailed()
        return
    }
    val webRef = remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    // Перезагрузка страницы после таймера (счётчик меняет родитель)
    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) webRef.value?.reload()
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = !isAllowedSurveyUrl(request.url.toString())

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                Log.i(TAG, "load error ${error.errorCode} ${request.url}")
                                onLoadFailed()
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            Log.i(TAG, "page finished $url")
                        }
                    }
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        Log.i(TAG, "WEB_MESSAGE_LISTENER unsupported — native fallback")
                        post { onLoadFailed() }
                        return@apply
                    }
                    WebViewCompat.addWebMessageListener(
                        this,
                        BRIDGE_OBJECT,
                        setOf(KabinetConfig.ORIGIN),
                    ) { view, message, _, _, _ ->
                        val raw = message.data
                        val parsed = raw?.let { parseBridgeMessage(it) }
                        Log.i(TAG, "bridge: ${raw?.take(200)}")
                        when (parsed) {
                            is BridgeIncoming.Feedback -> scope.launch {
                                val ok = onFeedback(parsed)
                                view.post {
                                    view.evaluateJavascript("window.respondentFeedbackQueued($ok)", null)
                                }
                            }
                            is BridgeIncoming.State -> onState(parsed)
                            is BridgeIncoming.Keyboard -> onKeyboard(parsed.visible)
                            is BridgeIncoming.FormUnavailable -> onLoadFailed()
                            null -> {
                                view.post {
                                    view.evaluateJavascript("window.respondentFeedbackQueued(false)", null)
                                }
                            }
                        }
                    }
                    loadUrl(url)
                    post {
                        webRef.value = this
                        onWebView(this)
                    }
                }
            },
        )
        footer()
    }
}
