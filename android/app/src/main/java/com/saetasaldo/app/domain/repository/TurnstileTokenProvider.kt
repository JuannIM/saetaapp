package com.saetasaldo.app.domain.repository

/**
 * Produces a Cloudflare Turnstile token that `resultadoSaldo` accepts as
 * `verificacionCaptcha` when the `X-Use-New-Captcha: true` header is sent.
 * Tokens are single-use and short-lived: implementations resolve a fresh one
 * per call. A failure must degrade gracefully to the image-captcha/OCR path.
 */
interface TurnstileTokenProvider {
    suspend fun getToken(): Result<String>
}
