package com.saetasaldo.app.ui.cards

import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
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
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
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

        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { refreshAllBalancesUseCase(any()) } returns
            RefreshAllBalancesResult(updatedCount = 2, failures = emptyMap())

        viewModel.refreshAllBalances()
        advanceUntilIdle()

        coVerify(exactly = 1) { refreshAllBalancesUseCase(match { it.size == 2 }) }
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `refresh all displays the first partial failure`() = runTest {
        val card = SaetaCard(id = "1", name = "Tarjeta 1", cardNumber = "111111")
        cardsFlow.value = listOf(card)

        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
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
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
        backgroundScope.launch { viewModel.cards.collect() }
        advanceUntilIdle()

        coEvery { refreshAllBalancesUseCase(any()) } returns RefreshAllBalancesResult(
            updatedCount = 0,
            failures = mapOf("111111" to RuntimeException("stale"))
        )
        viewModel.refreshAllBalances()
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
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
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
    fun `addNewCard saves a new card without querying the balance`() = runTest {
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
        coEvery { repository.getCardByNumber("999999") } returns null
        var saved: SaetaCard? = null

        viewModel.addNewCard(name = "Mi Tarjeta", cardNumber = " 999999 ", nfcUid = "04a1b2c3") { saved = it.getOrNull() }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveCard(match { it.cardNumber == "999999" && it.name == "Mi Tarjeta" && it.nfcUid == "04A1B2C3" }) }
        assertEquals("999999", saved?.cardNumber)
    }

    @Test
    fun `addNewCard reuses an existing card with the same number`() = runTest {
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
        val existing = SaetaCard(id = "1", name = "Principal", cardNumber = "111111", currentBalance = 1500.0)
        coEvery { repository.getCardByNumber("111111") } returns existing
        var saved: SaetaCard? = null

        viewModel.addNewCard(name = "", cardNumber = "111111", nfcUid = "04ab") { saved = it.getOrNull() }
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveCard(match { it.id == "1" && it.name == "Principal" && it.nfcUid == "04AB" && it.currentBalance == 1500.0 }) }
        assertEquals("1", saved?.id)
    }

    @Test
    fun `deleteCard delegates to repository`() = runTest {
        val card = SaetaCard(id = "1", name = "A borrar", cardNumber = "111111")
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)

        viewModel.deleteCard(card)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteCard(card) }
    }

    @Test
    fun `setFavorite delegates to repository`() = runTest {
        val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)

        viewModel.setFavorite("card-123")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setFavorite("card-123") }
    }
}
