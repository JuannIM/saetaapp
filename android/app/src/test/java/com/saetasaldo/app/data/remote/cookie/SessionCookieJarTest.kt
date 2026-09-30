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
}
