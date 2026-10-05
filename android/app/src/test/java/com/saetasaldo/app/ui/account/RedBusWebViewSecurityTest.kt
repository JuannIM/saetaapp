package com.saetasaldo.app.ui.account

import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RedBusWebViewSecurityTest {

    private fun assertAllowed(url: String) =
        assertTrue(url, RedBusWebViewSecurity.isAllowedMainFrame(Uri.parse(url)))

    private fun assertBlocked(url: String) =
        assertFalse(url, RedBusWebViewSecurity.isAllowedMainFrame(Uri.parse(url)))

    private fun configuredWebView(block: (WebView) -> Unit) {
        val webView = WebView(RuntimeEnvironment.getApplication())
        try {
            RedBusWebViewSecurity.applyTo(webView)
            block(webView)
        } finally {
            webView.destroy()
        }
    }

    @Test
    fun `allows exact RedBus https origin`() {
        assertAllowed("https://salta.miredbus.com.ar/login")
        assertAllowed("https://salta.miredbus.com.ar/")
        assertAllowed("https://salta.miredbus.com.ar/rest/tarjetaInternal/listaTarjetas")
        assertAllowed("https://SALTA.MIREDBUS.COM.AR/login")
    }

    @Test
    fun `rejects http RedBus URL`() {
        assertBlocked("http://salta.miredbus.com.ar/")
        assertBlocked("http://salta.miredbus.com.ar/login")
    }

    @Test
    fun `rejects deceptive RedBus suffix and prefix hosts`() {
        assertBlocked("https://salta.miredbus.com.ar.attacker.invalid/")
        assertBlocked("https://attacker-salta.miredbus.com.ar/")
        assertBlocked("https://foo.salta.miredbus.com.ar.evil.invalid/")
        assertBlocked("https://miredbus.com.ar/")
        assertBlocked("https://www.salta.miredbus.com.ar/")
    }

    @Test
    fun `rejects file content javascript and intent schemes`() {
        assertBlocked("file:///sdcard/login.html")
        assertBlocked("content://com.saetasaldo.app/secret")
        assertBlocked("javascript:alert(1)")
        assertBlocked("intent://salta.miredbus.com.ar/login#Intent;end")
    }

    @Test
    fun `configuration enables javascript and dom storage for Turnstile`() = configuredWebView { webView ->
        assertTrue(webView.settings.javaScriptEnabled)
        assertTrue(webView.settings.domStorageEnabled)
    }

    @Test
    fun `configuration disables file content and mixed content access`() = configuredWebView { webView ->
        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
        assertFalse(webView.settings.allowFileAccessFromFileURLs)
        assertFalse(webView.settings.allowUniversalAccessFromFileURLs)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, webView.settings.mixedContentMode)
    }

    @Test
    fun `configuration keeps safe browsing enabled`() = configuredWebView { webView ->
        assertTrue(webView.settings.safeBrowsingEnabled)
    }
}
