package com.saetasaldo.app.data.remote.cookie

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCookieJarTest {

    private class FakeWebCookieStore : WebCookieStore {
        var cookieHeader: String? = null
        val writtenSetCookieHeaders = mutableListOf<String>()
        var getCalls = 0
        var setCalls = 0
        var flushCalls = 0
        var clearCalls = 0

        override fun getCookieHeader(url: String): String? {
            getCalls++
            return cookieHeader
        }

        override fun setCookie(url: String, setCookieHeader: String) {
            setCalls++
            writtenSetCookieHeaders.add(setCookieHeader)
        }

        override fun flush() {
            flushCalls++
        }

        override suspend fun clear() {
            clearCalls++
        }
    }

    private val store = FakeWebCookieStore()
    private val jar = WebViewCookieJar(allowedHost = "salta.miredbus.com.ar", store = store)

    private val httpsUrl = "https://salta.miredbus.com.ar/rest/loginInternal/usuarioLogeado".toHttpUrl()

    @Test
    fun `returns RedBus cookies to the exact https host`() {
        store.cookieHeader = "session=abc123; theme=light"

        val cookies = jar.loadForRequest(httpsUrl)

        assertEquals(2, cookies.size)
        assertEquals("session", cookies[0].name)
        assertEquals("theme", cookies[1].name)
        assertEquals(1, store.getCalls)
    }

    @Test
    fun `does not return cookies to a different host`() {
        store.cookieHeader = "session=abc123"

        val otherHost = jar.loadForRequest("https://attacker.example/".toHttpUrl())
        val subdomain = jar.loadForRequest("https://foo.salta.miredbus.com.ar/".toHttpUrl())
        val suffixHost = jar.loadForRequest("https://salta.miredbus.com.ar.attacker.invalid/".toHttpUrl())

        assertTrue(otherHost.isEmpty())
        assertTrue(subdomain.isEmpty())
        assertTrue(suffixHost.isEmpty())
        assertEquals(0, store.getCalls)
    }

    @Test
    fun `does not return cookies over http`() {
        store.cookieHeader = "session=abc123"

        val cookies = jar.loadForRequest("http://salta.miredbus.com.ar/rest".toHttpUrl())

        assertTrue(cookies.isEmpty())
        assertEquals(0, store.getCalls)
    }

    @Test
    fun `preserves cookie values containing equals signs`() {
        store.cookieHeader = "second=some=value"

        val cookies = jar.loadForRequest(httpsUrl)

        assertEquals(1, cookies.size)
        assertEquals("second", cookies[0].name)
        assertTrue(cookies[0].value == "some" + "=" + "value")
    }

    @Test
    fun `writes response cookies back to the web store`() {
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("salta.miredbus.com.ar")
            .secure()
            .build()

        jar.saveFromResponse(httpsUrl, listOf(cookie))

        assertEquals(1, store.setCalls)
        assertEquals(1, store.flushCalls)
    }

    @Test
    fun `ignores response cookies from a different host`() {
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("attacker.example")
            .secure()
            .build()

        jar.saveFromResponse("https://attacker.example/".toHttpUrl(), listOf(cookie))
        jar.saveFromResponse("http://salta.miredbus.com.ar/".toHttpUrl(), listOf(cookie))

        assertEquals(0, store.setCalls)
        assertEquals(0, store.flushCalls)
    }

    @Test
    fun `skips malformed and blank cookie pairs`() {
        store.cookieHeader = "good=1; ; noequals; spaced name=v; x=y"

        val cookies = jar.loadForRequest(httpsUrl)

        assertEquals(2, cookies.size)
        assertEquals("good", cookies[0].name)
        assertEquals("x", cookies[1].name)
    }

    @Test
    fun `ignores non matching cookie on allowed response URL`() {
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("attacker.example")
            .secure()
            .build()

        jar.saveFromResponse(httpsUrl, listOf(cookie))

        assertEquals(0, store.setCalls)
        assertEquals(0, store.flushCalls)
    }

    @Test
    fun `does not flush an empty response cookie list`() {
        jar.saveFromResponse(httpsUrl, emptyList())

        assertEquals(0, store.setCalls)
        assertEquals(0, store.flushCalls)
    }

    @Test
    fun `clear delegates to the web store`() = runBlocking {
        jar.clear()

        assertEquals(1, store.clearCalls)
    }
}
