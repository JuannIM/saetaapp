package com.saetasaldo.app.ui.turnstile

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.saetasaldo.app.data.remote.api.SaetaApiService
import com.saetasaldo.app.domain.repository.TurnstileTokenProvider
import com.saetasaldo.app.ui.account.RedBusWebViewSecurity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves a Cloudflare Turnstile token on the public, anonymous RedBus home
 * page inside an offscreen [WebView]. The token is sent as
 * `verificacionCaptcha` together with `X-Use-New-Captcha: true`, which is what
 * the portal's own consulta page does.
 *
 * SECURITY NOTE — unlike the login WebView, this one deliberately exposes a
 * one-way `@JavascriptInterface` bridge (`AndroidTurnstile.onToken`/`onError`)
 * because there is no other way to hand the token back to Kotlin. That is
 * acceptable here ONLY because the surface is the public anonymous page: no
 * credentials or form fields are entered and no authenticated session is
 * inspected. The main frame is still restricted to the exact RedBus host over
 * HTTPS (see [RedBusWebViewSecurity.isAllowedMainFrame]), TLS errors always
 * cancel, and the WebView is destroyed after every attempt. The login WebView
 * must never carry a bridge.
 */
class WebViewTurnstileTokenProvider(
    context: Context,
    private val apiService: SaetaApiService,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : TurnstileTokenProvider {

    private val appContext = context.applicationContext

    // Tokens are single-use; serializing also prevents parallel WebViews.
    private val mutex = Mutex()

    override suspend fun getToken(): Result<String> = mutex.withLock {
        try {
            val siteKey = fetchSiteKey() ?: return@withLock Result.failure(
                IllegalStateException("No se pudo obtener la clave Turnstile")
            )
            renderToken(siteKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchSiteKey(): String? {
        val response = apiService.getTurnstileKeySite()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return null
        }
        // The endpoint answers plaintext. The whitelist keeps a hostile or
        // malformed key from ever reaching the injected script.
        return response.body()?.string()?.trim()?.takeIf { SITE_KEY_REGEX.matches(it) }
    }

    private suspend fun renderToken(siteKey: String): Result<String> = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            val outcome = withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Result<String>> { cont ->
                    val view = WebView(appContext)
                    webView = view
                    val settled = AtomicBoolean(false)

                    fun complete(result: Result<String>) {
                        if (settled.compareAndSet(false, true)) {
                            cont.resume(result)
                        }
                    }

                    RedBusWebViewSecurity.applyTo(view)
                    view.addJavascriptInterface(TurnstileBridge(::complete), JS_BRIDGE_NAME)
                    view.webViewClient = object : WebViewClient() {
                        private var renderAttempted = false

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            if (request.isForMainFrame) {
                                return !RedBusWebViewSecurity.isAllowedMainFrame(request.url)
                            }
                            // Turnstile runs in an iframe: HTTPS subframes are
                            // needed, anything else is blocked.
                            return request.url.scheme?.equals("https", ignoreCase = true) != true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (renderAttempted) return
                            if (url != null && RedBusWebViewSecurity.isAllowedMainFrame(Uri.parse(url))) {
                                renderAttempted = true
                                view.evaluateJavascript(turnstileScript(siteKey), null)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                complete(Result.failure(IllegalStateException("No se pudo cargar el portal de RedBus")))
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError
                        ) {
                            handler.cancel()
                            complete(Result.failure(IllegalStateException("Error TLS al cargar el portal de RedBus")))
                        }
                    }
                    view.loadUrl(HOME_URL)
                }
            }
            outcome ?: Result.failure(IllegalStateException("Tiempo de espera agotado resolviendo el captcha Turnstile"))
        } finally {
            // Always tear the WebView down: success, failure, timeout, cancel.
            webView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    /**
     * Waits for `window.turnstile` (the page loads `api.js` asynchronously,
     * which can finish after `onPageFinished`) and then renders the widget
     * offscreen. Every terminal event reaches the `AndroidTurnstile` bridge.
     */
    private fun turnstileScript(siteKey: String): String = """
        (function() {
            var attempts = 0;
            function tryRender() {
                if (typeof window.turnstile !== 'undefined' && document.body) {
                    try {
                        var el = document.createElement('div');
                        document.body.appendChild(el);
                        window.turnstile.render(el, {
                            sitekey: "$siteKey",
                            callback: function(t) { $JS_BRIDGE_NAME.onToken(t); },
                            'error-callback': function(c) { $JS_BRIDGE_NAME.onError(String(c)); },
                            'expired-callback': function() { $JS_BRIDGE_NAME.onError('expired'); },
                            'timeout-callback': function() { $JS_BRIDGE_NAME.onError('timeout'); }
                        });
                    } catch (e) {
                        $JS_BRIDGE_NAME.onError('render:' + e);
                    }
                } else if (++attempts < 50) {
                    setTimeout(tryRender, 200);
                } else {
                    $JS_BRIDGE_NAME.onError('api-not-loaded');
                }
            }
            tryRender();
        })();
    """.trimIndent()

    private class TurnstileBridge(
        private val onResult: (Result<String>) -> Unit
    ) {
        @JavascriptInterface
        fun onToken(token: String?) {
            if (token.isNullOrBlank()) {
                onResult(Result.failure(IllegalStateException("Turnstile devolvió un token vacío")))
            } else {
                onResult(Result.success(token))
            }
        }

        @JavascriptInterface
        fun onError(code: String?) {
            onResult(Result.failure(IllegalStateException("Turnstile error: ${code ?: "desconocido"}")))
        }
    }

    private companion object {
        const val HOME_URL = "https://salta.miredbus.com.ar/"
        const val JS_BRIDGE_NAME = "AndroidTurnstile"
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
        val SITE_KEY_REGEX = Regex("^[0-9A-Za-z_-]{1,128}$")
    }
}
