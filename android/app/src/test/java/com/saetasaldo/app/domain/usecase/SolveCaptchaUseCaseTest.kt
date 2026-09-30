package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.api.SaetaApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SolveCaptchaUseCaseTest {

    private val apiService = mockk<SaetaApiService>()
    private val solver = mockk<MlKitCaptchaSolver>()
    private val useCase = SolveCaptchaUseCase(apiService, solver)

    @Test
    fun `returns failure when api fails on all attempts`() = runBlocking {
        val errorBody = "Error".toResponseBody("text/plain".toMediaType())
        coEvery { apiService.getCaptchaImage(any()) } returns Response.error(500, errorBody)

        val result = useCase(maxAttempts = 3)
        assertTrue(result.isFailure)
        coVerify(exactly = 3) { apiService.getCaptchaImage(any()) }
    }

    @Test
    fun `propagates CancellationException promptly without retrying`() {
        coEvery { apiService.getCaptchaImage(any()) } throws CancellationException("Cancelled")

        try {
            runBlocking {
                useCase(maxAttempts = 3)
            }
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
        coVerify(exactly = 1) { apiService.getCaptchaImage(any()) }
    }
}
