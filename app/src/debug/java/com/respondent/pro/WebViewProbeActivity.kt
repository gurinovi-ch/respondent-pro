package com.respondent.pro

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature

/**
 * Проба WebView (только debug): офлайн-страница через Service Worker,
 * мост отправки в нативную очередь и ввод текста из нативной клавиатуры.
 * Всё состояние и результаты — в logcat с тегом WebViewProbe, без визуальных проверок.
 */
class WebViewProbeActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WebViewProbe"
        private const val URL = "https://appassets.androidplatform.net/assets/probe/index.html"
    }

    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "page finished $url")
                probeServiceWorker()
                // Автопоследовательность вместо тапов: ввод RU/EN, backspace, отправка через мост,
                // состояние SW, IndexedDB — всё в logcat.
                handler.postDelayed({ runSequence() }, 1500)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "console ${message.message()} (line ${message.lineNumber()})")
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "ProbeBridge")
        val pkg = WebView.getCurrentWebViewPackage()
        Log.i(
            TAG,
            "webview pkg=${pkg?.packageName} ${pkg?.versionName} " +
                "swBasic=${WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)} " +
                "swIntercept=${WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)} " +
                "swCache=${WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CACHE_MODE)}",
        )
        val target = intent.getStringExtra("url") ?: URL
        Log.i(TAG, "probe started, loading $target")
        web.loadUrl(target)
    }

    private fun probeServiceWorker() {
        val js = """
            (async function () {
              try {
                const reg = await navigator.serviceWorker.getRegistration();
                console.log('PROBE swreg ' + (reg ? reg.scope : 'none'));
                console.log('PROBE online ' + navigator.onLine + ' title=' + document.title);
              } catch (e) {
                console.log('PROBE swreg error ' + e);
              }
            })()
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val target = intent.getStringExtra("url") ?: URL
        Log.i(TAG, "reload via intent: $target")
        web.loadUrl(target)
    }

    private fun runSequence() {
        val js = """
            (function () {
              var r = {};
              r.typeRu = probeType('й');
              r.typeEn = probeType('A');
              r.bs = probeBackspace();
              r.value = document.getElementById('t').value;
              r.submit = probeSubmit();
              r.sw = probeSwState();
              probeIdb();
              return JSON.stringify(r);
            })()
        """.trimIndent()
        web.evaluateJavascript(js) { result -> Log.i(TAG, "sequence: $result") }
    }

    inner class Bridge {
        @JavascriptInterface
        fun postFeedback(json: String) {
            // В реальном срезе здесь Room-очередь; в пробе — только лог и эхо-подтверждение.
            Log.i(TAG, "bridge postFeedback: $json")
            runOnUiThread { web.evaluateJavascript("window.onBridgeAck(true)", null) }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }
}
