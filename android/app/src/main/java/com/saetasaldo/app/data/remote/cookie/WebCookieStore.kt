package com.saetasaldo.app.data.remote.cookie

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

interface WebCookieStore {
    fun getCookieHeader(url: String): String?
    fun setCookie(url: String, setCookieHeader: String)
    fun flush()
    suspend fun clear()
}

class AndroidWebCookieStore(
    private val cookieManager: CookieManager = CookieManager.getInstance()
) : WebCookieStore {

    override fun getCookieHeader(url: String): String? = cookieManager.getCookie(url)

    override fun setCookie(url: String, setCookieHeader: String) {
        cookieManager.setCookie(url, setCookieHeader)
    }

    override fun flush() {
        cookieManager.flush()
    }

    override suspend fun clear() {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                cookieManager.removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        flush()
    }
}
