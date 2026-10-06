package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.data.remote.RedBusContractException
import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.RedBusSessionExpiredException
import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GetCardBalanceUseCaseTest {
    private val repository = mockk<CardRepository>()
    private val accountRepository = mockk<RedBusAccountRepository>()
    private val useCase = GetCardBalanceUseCase(repository)

    private fun accountUseCase(state: RedBusSessionState): GetCardBalanceUseCase {
        every { accountRepository.sessionState } returns MutableStateFlow(state)
        return GetCardBalanceUseCase(repository, accountRepository)
    }

    @Test
    fun `returns balance when repository succeeds`() = runBlocking {
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")
        assertTrue(result.isSuccess)
        assertEquals(1500.0, result.getOrNull()?.currentBalance ?: 0.0, 0.01)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
    }

    @Test
    fun `passes manual captcha when provided`() = runBlocking {
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", "XYZ12") } returns Result.success(card)

        val result = useCase("123456", "XYZ12")
        assertTrue(result.isSuccess)
        assertEquals(1500.0, result.getOrNull()?.currentBalance ?: 0.0, 0.01)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", "XYZ12") }
    }

    @Test
    fun `returns failure when repository fails`() = runBlocking {
        val error = IllegalArgumentException("Captcha incorrecto. Reintentá nuevamente.")
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.failure(error)

        val result = useCase("123456")
        assertTrue(result.isFailure)
        assertEquals("Captcha incorrecto. Reintentá nuevamente.", result.exceptionOrNull()?.message)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
    }

    @Test
    fun `connected linked card uses account balance without captcha query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        val accountCard = RedBusAccountCard(
            cardNumber = " 123456 ",
            balance = 2500.0,
            cardType = CardType.VERDE_BENEFICIARIO,
            cardState = "Activa",
            suggestedName = "Sube Trabajo"
        )
        coEvery { accountRepository.getLinkedCards() } returns Result.success(listOf(accountCard))
        val updated = SaetaCard(
            id = "1",
            name = "Sube Trabajo",
            cardNumber = "123456",
            type = CardType.VERDE_BENEFICIARIO,
            currentBalance = 2500.0,
            cardState = "Activa"
        )
        coEvery { repository.applyBalanceUpdate(any()) } returns updated

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        assertEquals(2500.0, result.getOrNull()?.currentBalance ?: 0.0, 0.01)
        coVerify(exactly = 1) {
            repository.applyBalanceUpdate(
                CardBalanceUpdate(
                    cardNumber = "123456",
                    balance = 2500.0,
                    cardType = CardType.VERDE_BENEFICIARIO,
                    cardState = "Activa",
                    suggestedName = "Sube Trabajo"
                )
            )
        }
        coVerify(exactly = 0) { repository.refreshCardBalance(any(), any()) }
    }

    @Test
    fun `manual captcha always bypasses account lookup`() = runBlocking {
        val useCase = GetCardBalanceUseCase(repository, accountRepository)
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", "XYZ12") } returns Result.success(card)

        val result = useCase("123456", "XYZ12")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", "XYZ12") }
        verify(exactly = 0) { accountRepository.sessionState }
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
    }

    @Test
    fun `disconnected state uses anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Disconnected)
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
    }

    @Test
    fun `unlinked card falls back to anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        val other = RedBusAccountCard("999999", 100.0, CardType.AZUL_COMUN, "Activa", "Otra")
        coEvery { accountRepository.getLinkedCards() } returns Result.success(listOf(other))
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
        coVerify(exactly = 0) { repository.applyBalanceUpdate(any()) }
    }

    @Test
    fun `expired session falls back to anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        coEvery { accountRepository.getLinkedCards() } returns
            Result.failure(RedBusSessionExpiredException())
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
        coVerify(exactly = 0) { repository.applyBalanceUpdate(any()) }
    }

    @Test
    fun `account network failure falls back to anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        coEvery { accountRepository.getLinkedCards() } returns
            Result.failure(RedBusNetworkException(IOException("timeout")))
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
        coVerify(exactly = 0) { repository.applyBalanceUpdate(any()) }
    }

    @Test
    fun `account contract failure falls back to anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        coEvery { accountRepository.getLinkedCards() } returns
            Result.failure(RedBusContractException())
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
        coVerify(exactly = 0) { repository.applyBalanceUpdate(any()) }
    }

    @Test
    fun `apply balance update failure falls back to anonymous query`() = runBlocking {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        val match = RedBusAccountCard("123456", 2500.0, CardType.AZUL_COMUN, "Activa", null)
        coEvery { accountRepository.getLinkedCards() } returns Result.success(listOf(match))
        coEvery { repository.applyBalanceUpdate(any()) } throws RuntimeException("db write failed")
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { repository.refreshCardBalance("123456", null) } returns Result.success(card)

        val result = useCase("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.refreshCardBalance("123456", null) }
    }

    @Test
    fun `cancellation is rethrown instead of falling back`() {
        val useCase = accountUseCase(RedBusSessionState.Connected)
        coEvery { accountRepository.getLinkedCards() } throws CancellationException("Cancelled")

        try {
            runBlocking { useCase("123456") }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
        coVerify(exactly = 0) { repository.refreshCardBalance(any(), any()) }
    }
}
