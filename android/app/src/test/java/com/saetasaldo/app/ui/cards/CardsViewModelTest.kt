package com.saetasaldo.app.ui.cards

import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import com.saetasaldo.app.domain.usecase.RefreshAllBalancesResult
import com.saetasaldo.app.domain.usecase.RefreshAllBalancesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<CardRepository>(relaxed = true)
    private val getCardBalanceUseCase = mockk<GetCardBalanceUseCase>(relaxed = true)
    private val refreshAllBalancesUseCase = mockk<RefreshAllBalancesUseCase>(relaxed = true)
    private val cardsFlow = MutableStateFlow<List<SaetaCard>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getAllCards() } returns cardsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cards StateFlow emits initial empty list and updates from repository`() = runTest {
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()
        assertTrue(viewModel.cards.value.isEmpty())

        val card1 = SaetaCard(id = "1", name = "Principal", cardNumber = "111111", currentBalance = 1500.0)
        cardsFlow.value = listOf(card1)
        advanceUntilIdle()

        assertEquals(1, viewModel.cards.value.size)
        assertEquals("Principal", viewModel.cards.value[0].name)
    }

    @Test
    fun `refresh all delegates the current card snapshot once`() = runTest {
        val card1 = SaetaCard(id = "1", name = "Tarjeta 1", cardNumber = "111111")
        val card2 = SaetaCard(id = "2", name = "Tarjeta 2", cardNumber = "222222")
        cardsFlow.value = listOf(card1, card2)

        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { refreshAllBalancesUseCase(any()) } returns
            RefreshAllBalancesResult(updatedCount = 2, failures = emptyMap())

        viewModel.refreshAllBalances()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshAllBalancesUseCase(match { it.size == 2 }) }
        coVerify(exactly = 0) { getCardBalanceUseCase(any()) }
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh all displays the first partial failure`() = runTest {
        val card = SaetaCard(id = "1", name = "Tarjeta 1", cardNumber = "111111")
        cardsFlow.value = listOf(card)

        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { refreshAllBalancesUseCase(any()) } returns RefreshAllBalancesResult(
            updatedCount = 1,
            failures = mapOf("222222" to RuntimeException("fallo parcial"))
        )

        viewModel.refreshAllBalances()
        advanceUntilIdle()

        assertEquals("fallo parcial", viewModel.errorMessage.value)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh all with no failures clears stale errors`() = runTest {
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { getCardBalanceUseCase("111111") } returns Result.failure(RuntimeException("stale"))
        viewModel.refreshCard("111111")
        advanceUntilIdle()
        assertEquals("stale", viewModel.errorMessage.value)

        coEvery { refreshAllBalancesUseCase(any()) } returns
            RefreshAllBalancesResult(updatedCount = 1, failures = emptyMap())

        viewModel.refreshAllBalances()
        advanceUntilIdle()

        assertNull(viewModel.errorMessage.value)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh all resets loading after cancellation or failure`() = runTest {
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { refreshAllBalancesUseCase(any()) } throws CancellationException("cancelled")
        viewModel.refreshAllBalances()
        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)

        coEvery { refreshAllBalancesUseCase(any()) } throws RuntimeException("boom")
        viewModel.refreshAllBalances()
        advanceUntilIdle()
        assertFalse(viewModel.isRefreshing.value)
        assertEquals("boom", viewModel.errorMessage.value)
    }

    @Test
    fun `refreshCard queries balance for specific card number`() = runTest {
        val card = SaetaCard(id = "1", name = "Tarjeta 1", cardNumber = "111111")
        coEvery { getCardBalanceUseCase("111111") } returns Result.success(card)

        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        viewModel.refreshCard("111111")
        advanceUntilIdle()

        coVerify(exactly = 1) { getCardBalanceUseCase("111111") }
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `addNewCard saves card and immediately refreshes balance`() = runTest {
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)
        advanceUntilIdle()

        coEvery { getCardBalanceUseCase(any()) } returns Result.success(
            SaetaCard(id = "new", name = "Mi Tarjeta", cardNumber = "999999", currentBalance = 2000.0)
        )

        var callbackInvoked = false
        viewModel.addNewCard(
            name = "Mi Tarjeta",
            cardNumber = "999999",
            nfcUid = "04A1B2C3"
        ) { result ->
            callbackInvoked = true
            assertTrue(result.isSuccess)
        }
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        coVerify(exactly = 1) {
            repository.saveCard(match {
                it.cardNumber == "999999" && it.name == "Mi Tarjeta" && it.nfcUid == "04A1B2C3" && it.type == CardType.AZUL_COMUN
            })
        }
        coVerify(exactly = 1) { getCardBalanceUseCase("999999") }
    }

    @Test
    fun `deleteCard delegates to repository`() = runTest {
        val card = SaetaCard(id = "1", name = "A borrar", cardNumber = "111111")
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)

        viewModel.deleteCard(card)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteCard(card) }
    }

    @Test
    fun `setFavorite delegates to repository`() = runTest {
        val viewModel = CardsViewModel(repository, getCardBalanceUseCase, refreshAllBalancesUseCase)

        viewModel.setFavorite("card-123")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setFavorite("card-123") }
    }
}
