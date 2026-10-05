package com.saetasaldo.app.data.remote.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar(
    private val allowedHost: String,
    private val store: WebCookieStore
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isAllowed(url)) return emptyList()
        val header = store.getCookieHeader(url.toString()) ?: return emptyList()
        return header.split(";")
            .mapNotNull { pair -> runCatching { parsePair(pair) }.getOrNull() }
            .filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isAllowed(url)) return
        var wroteCookie = false
        for (cookie in cookies) {
            if (cookie.matches(url)) {
                store.setCookie(url.toString(), cookie.toString())
                wroteCookie = true
            }
        }
        if (wroteCookie) store.flush()
    }

    suspend fun clear() {
        store.clear()
    }

    private fun isAllowed(url: HttpUrl): Boolean =
        url.isHttps && url.host.equals(allowedHost, ignoreCase = true)

    private fun parsePair(pair: String): Cookie {
        val separator = pair.indexOf('=')
        require(separator > 0) { "cookie pair lacks name/value separator" }
        val name = pair.substring(0, separator).trim()
        require(name.all { it.code in 0x21..0x7E && it !in "\"(),/:;<=>?@[\\]{} \t" }) {
            "cookie name is not a valid token"
        }
        val value = pair.substring(separator + 1)
        return Cookie.Builder()
            .name(name)
            .value(value)
            .hostOnlyDomain(allowedHost)
            .path("/")
            .secure()
            .build()
    }
}
