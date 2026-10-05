package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.data.remote.RedBusSessionExpiredException
import com.saetasaldo.app.domain.model.CardBalanceUpdate
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RefreshAllBalancesUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val accountRepository = mockk<RedBusAccountRepository>()
    private val sessionState = MutableStateFlow<RedBusSessionState>(RedBusSessionState.Disconnected)
    private val delayCalls = mutableListOf<Long>()
    private val useCase = RefreshAllBalancesUseCase(
        cardRepository = cardRepository,
        redBusAccountRepository = accountRepository,
        syncRedBusCardsUseCase = SyncRedBusCardsUseCase(cardRepository),
        delayBetweenAnonymousRequestsMillis = 800L,
        delay = { delayCalls.add(it) }
    )

    @Before
    fun setUp() {
        every { accountRepository.sessionState } returns sessionState
    }

    private fun localCard(number: String) =
        SaetaCard(id = "local-$number", name = "Local $number", cardNumber = number)

    private fun stubApplyBalanceUpdate() {
        coEvery { cardRepository.applyBalanceUpdate(any()) } answers {
            localCard(firstArg<CardBalanceUpdate>().cardNumber)
        }
    }

    private fun stubAnonymousRefresh() {
        coEvery { cardRepository.refreshCardBalance(any(), any()) } answers {
            Result.success(localCard(firstArg<String>()))
        }
    }

    @Test
    fun `bulk refresh fetches account list exactly once`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val local = localCard("11111111")
        every { cardRepository.getAllCards() } returns flowOf(listOf(local))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(
            listOf(RedBusAccountCard("11111111", 900.0, null, null, null))
        )
        stubApplyBalanceUpdate()

        val result = useCase(listOf(local))

        coVerify(exactly = 1) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { cardRepository.refreshCardBalance(any(), any()) }
        assertEquals(1, result.updatedCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `bulk refresh skips anonymous query for synced cards`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val synced = localCard("11111111")
        val unlinked = localCard("22222222")
        every { cardRepository.getAllCards() } returns flowOf(listOf(synced, unlinked))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(
            listOf(RedBusAccountCard("11111111", 900.0, null, null, null))
        )
        stubApplyBalanceUpdate()
        stubAnonymousRefresh()

        val result = useCase(listOf(synced, unlinked))

        coVerify(exactly = 0) { cardRepository.refreshCardBalance("11111111", any()) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("22222222", null) }
        assertEquals(2, result.updatedCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `bulk refresh anonymously updates unlinked local cards`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val local1 = localCard("11111111")
        val local2 = localCard("22222222")
        every { cardRepository.getAllCards() } returns flowOf(listOf(local1, local2))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(
            listOf(RedBusAccountCard("99999999", 100.0, null, null, "Importada"))
        )
        stubApplyBalanceUpdate()
        stubAnonymousRefresh()

        val result = useCase(listOf(local1, local2))

        coVerify(exactly = 1) { cardRepository.refreshCardBalance("11111111", null) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("22222222", null) }
        coVerify(exactly = 1) { cardRepository.applyBalanceUpdate(any()) }
        assertEquals(3, result.updatedCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `bulk refresh falls back for all cards when account fetch fails`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val local1 = localCard("11111111")
        val local2 = localCard("22222222")
        coEvery { accountRepository.getLinkedCards() } returns
            Result.failure(RedBusSessionExpiredException())
        stubAnonymousRefresh()

        val result = useCase(listOf(local1, local2))

        coVerify(exactly = 1) { accountRepository.getLinkedCards() }
        verify(exactly = 0) { cardRepository.getAllCards() }
        coVerify(exactly = 0) { cardRepository.applyBalanceUpdate(any()) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("11111111", null) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("22222222", null) }
        assertEquals(2, result.updatedCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `bulk refresh uses anonymous path for all cards when session is disconnected`() = runBlocking {
        sessionState.value = RedBusSessionState.Disconnected
        val local1 = localCard("11111111")
        val local2 = localCard("22222222")
        stubAnonymousRefresh()

        val result = useCase(listOf(local1, local2))

        coVerify(exactly = 0) { accountRepository.getLinkedCards() }
        coVerify(exactly = 0) { cardRepository.applyBalanceUpdate(any()) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("11111111", null) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("22222222", null) }
        assertEquals(2, result.updatedCount)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `bulk refresh reports partial failures without discarding successes`() = runBlocking {
        sessionState.value = RedBusSessionState.Disconnected
        val error = IllegalStateException("portal down")
        coEvery { cardRepository.refreshCardBalance(any(), any()) } answers {
            val number = firstArg<String>()
            if (number == "22222222") Result.failure(error) else Result.success(localCard(number))
        }

        val result = useCase(
            listOf(localCard("11111111"), localCard("22222222"), localCard("33333333"))
        )

        assertEquals(2, result.updatedCount)
        assertEquals(setOf("22222222"), result.failures.keys)
        assertEquals(error, result.failures["22222222"])
    }

    @Test
    fun `bulk refresh spaces only anonymous requests by eight hundred milliseconds`() = runBlocking {
        sessionState.value = RedBusSessionState.Disconnected
        stubAnonymousRefresh()

        val result = useCase(
            listOf(localCard("11111111"), localCard("22222222"), localCard("33333333"))
        )

        assertEquals(listOf(800L, 800L), delayCalls)
        assertEquals(3, result.updatedCount)
    }

    @Test
    fun `bulk refresh does not delay for synced cards`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val synced = localCard("11111111")
        val unlinked1 = localCard("22222222")
        val unlinked2 = localCard("33333333")
        every { cardRepository.getAllCards() } returns flowOf(listOf(unlinked1, synced, unlinked2))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(
            listOf(RedBusAccountCard("11111111", 900.0, null, null, null))
        )
        stubApplyBalanceUpdate()
        stubAnonymousRefresh()

        useCase(listOf(unlinked1, synced, unlinked2))

        // The synced card is skipped, so the two anonymous requests share one delay.
        assertEquals(listOf(800L), delayCalls)
        coVerify(exactly = 0) { cardRepository.refreshCardBalance("11111111", any()) }
    }

    @Test
    fun `bulk refresh syncs account cards even when local list is empty`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        every { cardRepository.getAllCards() } returns flowOf(emptyList())
        coEvery { accountRepository.getLinkedCards() } returns Result.success(
            listOf(RedBusAccountCard("99999999", 100.0, null, null, "Importada"))
        )
        stubApplyBalanceUpdate()

        val result = useCase(emptyList())

        coVerify(exactly = 1) { accountRepository.getLinkedCards() }
        coVerify(exactly = 1) { cardRepository.applyBalanceUpdate(any()) }
        coVerify(exactly = 0) { cardRepository.refreshCardBalance(any(), any()) }
        assertEquals(1, result.updatedCount)
        assertTrue(delayCalls.isEmpty())
    }

    @Test
    fun `bulk refresh with empty account list still updates local cards anonymously`() = runBlocking {
        sessionState.value = RedBusSessionState.Connected
        val local1 = localCard("11111111")
        val local2 = localCard("22222222")
        every { cardRepository.getAllCards() } returns flowOf(listOf(local1, local2))
        coEvery { accountRepository.getLinkedCards() } returns Result.success(emptyList())
        stubAnonymousRefresh()

        val result = useCase(listOf(local1, local2))

        coVerify(exactly = 0) { cardRepository.applyBalanceUpdate(any()) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("11111111", null) }
        coVerify(exactly = 1) { cardRepository.refreshCardBalance("22222222", null) }
        assertEquals(2, result.updatedCount)
    }

    @Test
    fun `bulk refresh rethrows cancellation`() {
        sessionState.value = RedBusSessionState.Disconnected
        coEvery { cardRepository.refreshCardBalance(any(), any()) } throws
            CancellationException("Cancelled")

        try {
            runBlocking { useCase(listOf(localCard("11111111"))) }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `bulk refresh rethrows cancellation from delay`() {
        sessionState.value = RedBusSessionState.Disconnected
        stubAnonymousRefresh()
        val useCase = RefreshAllBalancesUseCase(
            cardRepository = cardRepository,
            redBusAccountRepository = accountRepository,
            syncRedBusCardsUseCase = SyncRedBusCardsUseCase(cardRepository),
            delayBetweenAnonymousRequestsMillis = 800L,
            delay = { throw CancellationException("Cancelled during delay") }
        )

        try {
            runBlocking { useCase(listOf(localCard("11111111"), localCard("22222222"))) }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled during delay", e.message)
        }
    }
}
