package com.saetasaldo.app.data.remote.cookie

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {
    private val cookieJar = SessionCookieJar()
    private val url = "https://salta.miredbus.com.ar/captcha.png".toHttpUrl()

    @Test
    fun `stores JSESSIONID and retrieves it for subsequent requests`() {
        val cookie = Cookie.Builder()
            .name("JSESSIONID")
            .value("TESTSESSION12345")
            .domain("salta.miredbus.com.ar")
            .path("/")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        val retrieved = cookieJar.loadForRequest(url)
        assertEquals(1, retrieved.size)
        assertEquals("JSESSIONID", retrieved[0].name)
        assertEquals("TESTSESSION12345", retrieved[0].value)
    }

    @Test
    fun `clearCookies removes all active session cookies`() {
        val cookie = Cookie.Builder()
            .name("JSESSIONID")
            .value("ABC")
            .domain("salta.miredbus.com.ar")
            .build()
        cookieJar.saveFromResponse(url, listOf(cookie))
        cookieJar.clear()
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `expired cookies are not returned and get pruned`() {
        val expiredCookie = Cookie.Builder()
            .name("EXPIRED_TOKEN")
            .value("OLD")
            .domain("salta.miredbus.com.ar")
            .expiresAt(System.currentTimeMillis() - 10000)
            .build()
        cookieJar.saveFromResponse(url, listOf(expiredCookie))
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `cookies for different path are not returned`() {
        val pathCookie = Cookie.Builder()
            .name("PATH_SPECIFIC")
            .value("SECRET")
            .domain("salta.miredbus.com.ar")
            .path("/admin")
            .build()
        cookieJar.saveFromResponse(url, listOf(pathCookie))
        val loaded = cookieJar.loadForRequest("https://salta.miredbus.com.ar/captcha.png".toHttpUrl())
        assertTrue(loaded.none { it.name == "PATH_SPECIFIC" })
    }
}
