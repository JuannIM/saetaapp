package com.saetasaldo.app.domain.usecase

import android.graphics.BitmapFactory
import com.saetasaldo.app.data.ocr.CaptchaPreprocessor
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.api.SaetaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SolveCaptchaUseCase(
    private val apiService: SaetaApiService,
    private val solver: MlKitCaptchaSolver
) {
    suspend operator fun invoke(maxAttempts: Int = 3): Result<String> = withContext(Dispatchers.IO) {
        var lastAttemptCode = ""

        repeat(maxAttempts) { attempt ->
            try {
                val response = apiService.getCaptchaImage()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val bytes = body.bytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val code = solver.solve(bitmap)
                            if (CaptchaPreprocessor.isLikelyValid(code)) {
                                return@withContext Result.success(code)
                            }
                            lastAttemptCode = code
                        }
                    }
                } else {
                    response.errorBody()?.close()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Continue retry loop
            }
        }

        Result.failure(IllegalStateException("No se pudo resolver el captcha tras $maxAttempts intentos. Último código: $lastAttemptCode"))
    }
}
