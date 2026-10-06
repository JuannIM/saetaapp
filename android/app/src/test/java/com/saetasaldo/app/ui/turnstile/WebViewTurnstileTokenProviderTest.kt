package com.saetasaldo.app.ui.turnstile

import com.saetasaldo.app.data.remote.api.SaetaApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.Response

/**
 * Robolectric cannot run real JavaScript or Turnstile challenges, so these
 * tests only prove the provider degrades to a failure Result instead of
 * hanging: the injected script never produces a token, and the timeout or an
 * upstream failure must surface as `Result.failure`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WebViewTurnstileTokenProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val apiService = mockk<SaetaApiService>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun provider(timeoutMillis: Long = 1_000L) = WebViewTurnstileTokenProvider(
        RuntimeEnvironment.getApplication(),
        apiService,
        timeoutMillis = timeoutMillis
    )

    @Test
    fun `getToken returns failure instead of hanging when the challenge never resolves`() = runTest {
        coEvery { apiService.getTurnstileKeySite() } returns Response.success(
            "0x4AAAAAAE2vQc2RkurVvWK8".toResponseBody("text/plain".toMediaType())
        )

        val result = provider().getToken()

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { apiService.getTurnstileKeySite() }
    }

    @Test
    fun `getToken returns failure when the sitekey request fails`() = runTest {
        coEvery { apiService.getTurnstileKeySite() } returns Response.error(
            500,
            "error".toResponseBody("text/plain".toMediaType())
        )

        val result = provider().getToken()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getToken returns failure when the sitekey response is malformed`() = runTest {
        coEvery { apiService.getTurnstileKeySite() } returns Response.success(
            "<script>alert(1)</script>".toResponseBody("text/plain".toMediaType())
        )

        val result = provider().getToken()

        assertTrue(result.isFailure)
    }
}
