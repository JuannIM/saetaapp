package com.saetasaldo.app.ui.account

import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.RedBusSessionExpiredException
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import com.saetasaldo.app.domain.usecase.RedBusSyncResult
import com.saetasaldo.app.domain.usecase.SyncRedBusCardsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RedBusAccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val accountRepository = mockk<RedBusAccountRepository>()
    private val syncRedBusCardsUseCase = mockk<SyncRedBusCardsUseCase>()
    private val sessionState = MutableStateFlow<RedBusSessionState>(RedBusSessionState.Unknown)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountRepository.sessionState } returns sessionState
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun accountCard(number: String) = RedBusAccountCard(
        cardNumber = number,
        balance = 100.0,
        cardType = null,
        cardState = null,
        suggestedName = null
    )

    private fun stubConnectedSession() {
        coEvery { accountRepository.checkSession() } coAnswers {
            sessionState.value = RedBusSessionState.Connected
            Result.success(RedBusSessionState.Connected)
        }
    }

    @Test
    fun `existing valid session becomes connected without syncing automatically`() = runTest {
        stubConnectedSession()
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.checkExistingSession()
        advanceUntilIdle()

        assertEquals(RedBusSessionState.Connected, viewModel.uiState.value.sessionState)
        assertFalse(viewModel.uiState.value.isSyncing)
        assertNull(viewModel.uiState.value.message)
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { syncRedBusCardsUseCase(any()) }
    }

    @Test
    fun `successful login verification connects and performs first sync`() = runTest {
        val cards = listOf(accountCard("11111111"), accountCard("22222222"))
        stubConnectedSession()
        coEvery { accountRepository.getLinkedCards() } returns Result.success(cards)
        coEvery { syncRedBusCardsUseCase(cards) } returns
            RedBusSyncResult(syncedCardNumbers = setOf("11111111", "22222222"), importedCount = 2)
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.checkSession() }
        coVerify(exactly = 1) { accountRepository.getLinkedCards() }
        coVerify(exactly = 1) { syncRedBusCardsUseCase(cards) }
        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals(
            "Sesión verificada. 2 tarjetas sincronizadas.",
            viewModel.uiState.value.message
        )
    }

    @Test
    fun `failed login verification remains disconnected and does not sync`() = runTest {
        sessionState.value = RedBusSessionState.Disconnected
        coEvery { accountRepository.checkSession() } returns
            Result.failure(RedBusNetworkException(IOException("timeout")))
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        assertEquals(RedBusSessionState.Disconnected, viewModel.uiState.value.sessionState)
        assertEquals(
            "Falló la conexión con RedBus. Reintentá en unos segundos.",
            viewModel.uiState.value.message
        )
        assertFalse(viewModel.uiState.value.isSyncing)
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { syncRedBusCardsUseCase(any()) }
    }

    @Test
    fun `manual sync reports imported card count`() = runTest {
        sessionState.value = RedBusSessionState.Connected
        val cards = listOf(accountCard("11111111"), accountCard("22222222"))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(cards)
        coEvery { syncRedBusCardsUseCase(cards) } returns
            RedBusSyncResult(syncedCardNumbers = setOf("11111111", "22222222"), importedCount = 1)
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.getLinkedCards() }
        coVerify(exactly = 1) { syncRedBusCardsUseCase(cards) }
        assertEquals(
            "Sincronización completa: 2 tarjetas sincronizadas, 1 importada.",
            viewModel.uiState.value.message
        )
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun `expired session displays reconnect message`() = runTest {
        sessionState.value = RedBusSessionState.Connected
        val expired = RedBusSessionExpiredException()
        coEvery { accountRepository.getLinkedCards() } coAnswers {
            sessionState.value = RedBusSessionState.Disconnected
            Result.failure(expired)
        }
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.sync()
        advanceUntilIdle()

        assertEquals(expired.message, viewModel.uiState.value.message)
        assertEquals(RedBusSessionState.Disconnected, viewModel.uiState.value.sessionState)
        assertFalse(viewModel.uiState.value.isSyncing)
        coVerify(exactly = 0) { syncRedBusCardsUseCase(any()) }
    }

    @Test
    fun `disconnect clears state and keeps local cards untouched`() = runTest {
        sessionState.value = RedBusSessionState.Connected
        coEvery { accountRepository.disconnect() } coAnswers {
            sessionState.value = RedBusSessionState.Disconnected
        }
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.disconnect() }
        assertEquals(RedBusSessionState.Disconnected, viewModel.uiState.value.sessionState)
        assertEquals("Sesión de RedBus cerrada.", viewModel.uiState.value.message)
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { syncRedBusCardsUseCase(any()) }
    }

    @Test
    fun `concurrent verification requests are coalesced`() = runTest {
        stubConnectedSession()
        coEvery { accountRepository.getLinkedCards() } returns Result.success(emptyList())
        coEvery { syncRedBusCardsUseCase(any()) } returns
            RedBusSyncResult(syncedCardNumbers = emptySet(), importedCount = 0)
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountRepository.checkSession() }
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun `disconnected check during verification stays silent`() = runTest {
        coEvery { accountRepository.checkSession() } coAnswers {
            sessionState.value = RedBusSessionState.Disconnected
            Result.success(RedBusSessionState.Disconnected)
        }
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        assertEquals(RedBusSessionState.Disconnected, viewModel.uiState.value.sessionState)
        // Mid-login Disconnected is expected: no alarming message is shown.
        assertEquals(null, viewModel.uiState.value.message)
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { syncRedBusCardsUseCase(any()) }
    }

    @Test
    fun `verification with no linked cards shows session verified without count`() = runTest {
        stubConnectedSession()
        coEvery { accountRepository.getLinkedCards() } returns Result.success(emptyList())
        coEvery { syncRedBusCardsUseCase(any()) } returns
            RedBusSyncResult(syncedCardNumbers = emptySet(), importedCount = 0)
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        assertEquals("Sesión verificada.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun `consumeMessage clears the current message`() = runTest {
        coEvery { accountRepository.checkSession() } returns
            Result.failure(RedBusNetworkException(IOException("timeout")))
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()
        assertEquals(
            "Falló la conexión con RedBus. Reintentá en unos segundos.",
            viewModel.uiState.value.message
        )

        viewModel.consumeMessage()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `cancellation during verification resets syncing state`() = runTest {
        coEvery { accountRepository.checkSession() } throws CancellationException("cancelled")
        val viewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.verifyLoginAndSync()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing)
        assertNull(viewModel.uiState.value.message)
    }
}
