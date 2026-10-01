package com.saetasaldo.app.data.remote.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class SessionCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostCookies = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            if (cookie.expiresAt <= now) {
                hostCookies.remove(cookie.name)
            } else {
                hostCookies[cookie.name] = cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val validCookies = mutableListOf<Cookie>()

        val iterator = hostCookies.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cookie = entry.value
            if (cookie.expiresAt <= now) {
                iterator.remove()
            } else if (cookie.matches(url)) {
                validCookies.add(cookie)
            }
        }
        return validCookies
    }

    fun clear() {
        cookieStore.clear()
    }
}
