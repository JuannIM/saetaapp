package com.saetasaldo.app.ui.account

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Hardening policy for the official RedBus login WebView. The surface is kept
 * minimal on purpose: no JavaScript interfaces, no WebMessage bridges, and no
 * DOM inspection — the page only shares its session through CookieManager.
 */
object RedBusWebViewSecurity {

    const val LOGIN_URL = "https://salta.miredbus.com.ar/login"

    /** Only https + exact host salta.miredbus.com.ar, case-insensitive. */
    fun isAllowedMainFrame(uri: Uri): Boolean {
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("salta.miredbus.com.ar", ignoreCase = true)
    }

    fun applyTo(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true        // Cloudflare Turnstile requirement
            domStorageEnabled = true        // Cloudflare Turnstile requirement
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Safe Browsing is declared in the manifest (EnableSafeBrowsing
            // meta-data); the setter was removed in SDK 35.
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true) // Turnstile iframe needs this
    }
}
