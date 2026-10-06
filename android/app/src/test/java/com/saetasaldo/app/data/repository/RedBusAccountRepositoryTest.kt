package com.saetasaldo.app.data.repository

import com.google.gson.Gson
import com.saetasaldo.app.data.remote.RedBusContractException
import com.saetasaldo.app.data.remote.RedBusHttpException
import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.RedBusNotConnectedException
import com.saetasaldo.app.data.remote.RedBusSessionExpiredException
import com.saetasaldo.app.data.remote.api.RedBusAccountApiService
import com.saetasaldo.app.data.remote.cookie.WebViewCookieJar
import com.saetasaldo.app.data.remote.dto.RedBusCardListDto
import com.saetasaldo.app.data.remote.dto.RedBusSessionDto
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.RedBusSessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RedBusAccountRepositoryTest {

    private val gson = Gson()
    private val apiService = mockk<RedBusAccountApiService>()
    private val cookieJar = mockk<WebViewCookieJar>(relaxed = true)
    private val repository = RedBusAccountRepositoryImpl(apiService, cookieJar)

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("redbus/$name")) {
            "Missing test fixture redbus/$name"
        }.readText()

    private suspend fun connect() {
        coEvery { apiService.getLoggedUser() } returns Response.success(RedBusSessionDto(error = 0))
        repository.checkSession()
    }

    @Test
    fun `initial session state is unknown`() {
        assertEquals(RedBusSessionState.Unknown, repository.sessionState.value)
    }

    @Test
    fun `only error zero marks session connected`() = runBlocking {
        coEvery { apiService.getLoggedUser() } returns Response.success(RedBusSessionDto(error = 0))

        val result = repository.checkSession()

        assertTrue(result.isSuccess)
        assertEquals(RedBusSessionState.Connected, result.getOrNull())
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
    }

    @Test
    fun `error one marks session disconnected`() = runBlocking {
        coEvery { apiService.getLoggedUser() } returns Response.success(RedBusSessionDto(error = 1))

        val result = repository.checkSession()

        assertTrue(result.isSuccess)
        assertEquals(RedBusSessionState.Disconnected, result.getOrNull())
        assertEquals(RedBusSessionState.Disconnected, repository.sessionState.value)
    }

    @Test
    fun `unknown error does not manufacture a connected session`() = runBlocking {
        coEvery { apiService.getLoggedUser() } returns Response.success(RedBusSessionDto(error = 7))

        val result = repository.checkSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusContractException)
        assertEquals(RedBusSessionState.Unknown, repository.sessionState.value)
    }

    @Test
    fun `null body is a contract failure`() = runBlocking {
        coEvery { apiService.getLoggedUser() } returns Response.success(null)

        val result = repository.checkSession()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusContractException)
        assertEquals(RedBusSessionState.Unknown, repository.sessionState.value)
    }

    @Test
    fun `http failure restores prior state and closes body`() = runBlocking {
        connect()
        val errorBody = mockk<ResponseBody>(relaxed = true)
        coEvery { apiService.getLoggedUser() } returns Response.error(500, errorBody)

        val result = repository.checkSession()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is RedBusHttpException)
        assertEquals(500, (exception as RedBusHttpException).statusCode)
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
        verify { errorBody.close() }
    }

    @Test
    fun `cards call while disconnected does not hit the api`() = runBlocking {
        val result = repository.getLinkedCards()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusNotConnectedException)
        coVerify(exactly = 0) { apiService.getLinkedCards() }
    }

    @Test
    fun `cards success returns mapped cards`() = runBlocking {
        connect()
        val dto = gson.fromJson(loadFixture("cards-success.json"), RedBusCardListDto::class.java)
        coEvery { apiService.getLinkedCards() } returns Response.success(dto)

        val result = repository.getLinkedCards()

        assertTrue(result.isSuccess)
        val cards = result.getOrNull().orEmpty()
        assertEquals(1, cards.size)
        assertEquals("12345678", cards[0].cardNumber)
        assertEquals(1450.5, cards[0].balance, 0.001)
        assertEquals(CardType.AZUL_COMUN, cards[0].cardType)
        assertEquals("ACTIVA", cards[0].cardState)
        assertEquals("Tarjeta de ejemplo", cards[0].suggestedName)
    }

    @Test
    fun `cards error ninety nine marks session disconnected`() = runBlocking {
        connect()
        coEvery { apiService.getLinkedCards() } returns
            Response.success(RedBusCardListDto(error = 99, cards = null))

        val result = repository.getLinkedCards()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusSessionExpiredException)
        assertEquals(RedBusSessionState.Disconnected, repository.sessionState.value)
    }

    @Test
    fun `cards network failure preserves connected state for retry`() = runBlocking {
        connect()
        coEvery { apiService.getLinkedCards() } throws IOException("offline")

        val result = repository.getLinkedCards()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusNetworkException)
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
    }

    @Test
    fun `cards error zero with null list is contract failure`() = runBlocking {
        connect()
        coEvery { apiService.getLinkedCards() } returns
            Response.success(RedBusCardListDto(error = 0, cards = null))

        val result = repository.getLinkedCards()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RedBusContractException)
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
    }

    @Test
    fun `cards error zero with empty list returns empty success`() = runBlocking {
        connect()
        coEvery { apiService.getLinkedCards() } returns
            Response.success(RedBusCardListDto(error = 0, cards = emptyList()))

        val result = repository.getLinkedCards()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isEmpty())
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
    }

    @Test
    fun `disconnect clears cookies and sets disconnected`() = runBlocking {
        connect()

        repository.disconnect()

        coVerify(exactly = 1) { cookieJar.clear() }
        assertEquals(RedBusSessionState.Disconnected, repository.sessionState.value)
    }

    @Test
    fun `disconnect still sets disconnected when cookie clear throws`() = runBlocking {
        connect()
        coEvery { cookieJar.clear() } throws RuntimeException("webview gone")

        val result = runCatching { repository.disconnect() }

        assertTrue(result.isFailure)
        assertEquals(RedBusSessionState.Disconnected, repository.sessionState.value)
    }

    @Test
    fun `checkSession cancellation rethrows and restores prior state`() = runBlocking {
        coEvery { apiService.getLoggedUser() } throws CancellationException("cancelled")

        try {
            repository.checkSession()
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(RedBusSessionState.Unknown, repository.sessionState.value)
    }

    @Test
    fun `getLinkedCards cancellation rethrows and preserves connected`() = runBlocking {
        connect()
        coEvery { apiService.getLinkedCards() } throws CancellationException("cancelled")

        try {
            repository.getLinkedCards()
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(RedBusSessionState.Connected, repository.sessionState.value)
    }
}
