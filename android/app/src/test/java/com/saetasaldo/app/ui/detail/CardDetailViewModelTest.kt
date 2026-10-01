package com.saetasaldo.app.ui.detail

import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.CalculateRemainingTripsUseCase
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<CardRepository>(relaxed = true)
    private val getCardBalanceUseCase = mockk<GetCardBalanceUseCase>(relaxed = true)
    private val calculateRemainingTripsUseCase = CalculateRemainingTripsUseCase()

    private val testCard = SaetaCard(
        id = "card-1",
        name = "Mi Saeta",
        cardNumber = "123456",
        currentBalance = 2070.0,
        type = CardType.AZUL_COMUN
    )

    private val cardsFlow = MutableStateFlow<List<SaetaCard>>(listOf(testCard))
    private val historyFlow = MutableStateFlow<List<BalanceRecord>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getAllCards() } returns cardsFlow
        coEvery { repository.getHistoryForCard("card-1") } returns historyFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `card StateFlow emits matching card from repository`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        advanceUntilIdle()

        assertEquals("Mi Saeta", viewModel.card.value?.name)
        assertEquals(2070.0, viewModel.card.value?.currentBalance ?: 0.0, 0.01)
    }

    @Test
    fun `tripEstimate calculates remaining trips combining balance and fare`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        backgroundScope.launch { viewModel.tripEstimate.collect() }
        advanceUntilIdle()

        // Balance 2070.0, fare 690.0 -> 3 regular + 2 emergency = 5 total
        val estimate = viewModel.tripEstimate.value
        assertNotNull(estimate)
        assertEquals(3, estimate?.regularTrips)
        assertEquals(2, estimate?.emergencyTrips)
        assertEquals(5, estimate?.totalPossibleTrips)
    }

    @Test
    fun `updateFare recalculates trip estimate with new fare`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        backgroundScope.launch { viewModel.tripEstimate.collect() }
        advanceUntilIdle()

        // Change fare to 1035.0 (2070 / 1035 = 2 regular)
        viewModel.updateFare(1035.0)
        advanceUntilIdle()

        assertEquals(1035.0, viewModel.fare.value, 0.01)
        val estimate = viewModel.tripEstimate.value
        assertEquals(2, estimate?.regularTrips)
        assertEquals(4, estimate?.totalPossibleTrips) // 2 regular + 2 emergency
    }

    @Test
    fun `refreshBalance delegates to getCardBalanceUseCase with card number`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        advanceUntilIdle()

        coEvery { getCardBalanceUseCase("123456", null) } returns Result.success(testCard)

        viewModel.refreshBalance()
        advanceUntilIdle()

        coVerify(exactly = 1) { getCardBalanceUseCase("123456", null) }
    }

    @Test
    fun `setAsFavorite delegates to repository`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        advanceUntilIdle()

        viewModel.setAsFavorite()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setFavorite("card-1") }
    }

    @Test
    fun `deleteCard deletes card and invokes onDeleted callback`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        advanceUntilIdle()

        var onDeletedCalled = false
        viewModel.deleteCard {
            onDeletedCalled = true
        }
        advanceUntilIdle()

        assertTrue(onDeletedCalled)
        coVerify(exactly = 1) { repository.deleteCard(testCard) }
    }

    @Test
    fun `updateCardName saves card with updated name`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        advanceUntilIdle()

        viewModel.updateCardName("Nueva Saeta")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveCard(match { it.id == "card-1" && it.name == "Nueva Saeta" }) }
    }

    @Test
    fun `triggerManualCaptcha and dismissCaptchaDialog toggle dialog state`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.showFallbackCaptchaDialog.collect() }
        advanceUntilIdle()

        assertEquals(false, viewModel.showFallbackCaptchaDialog.value)

        viewModel.triggerManualCaptcha()
        advanceUntilIdle()
        assertEquals(true, viewModel.showFallbackCaptchaDialog.value)

        viewModel.dismissCaptchaDialog()
        advanceUntilIdle()
        assertEquals(false, viewModel.showFallbackCaptchaDialog.value)
    }

    @Test
    fun `submitManualCaptcha dismisses dialog and delegates to getCardBalanceUseCase with code`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        backgroundScope.launch { viewModel.showFallbackCaptchaDialog.collect() }
        advanceUntilIdle()

        coEvery { getCardBalanceUseCase("123456", "ABCD") } returns Result.success(testCard)

        viewModel.triggerManualCaptcha()
        advanceUntilIdle()
        assertEquals(true, viewModel.showFallbackCaptchaDialog.value)

        viewModel.submitManualCaptcha("ABCD")
        advanceUntilIdle()

        assertEquals(false, viewModel.showFallbackCaptchaDialog.value)
        coVerify(exactly = 1) { getCardBalanceUseCase("123456", "ABCD") }
    }

    @Test
    fun `refreshBalance triggers fallback captcha dialog when failure contains captcha error`() = runTest {
        val viewModel = CardDetailViewModel(
            cardId = "card-1",
            repository = repository,
            getCardBalanceUseCase = getCardBalanceUseCase,
            calculateRemainingTripsUseCase = calculateRemainingTripsUseCase
        )
        backgroundScope.launch { viewModel.card.collect() }
        backgroundScope.launch { viewModel.showFallbackCaptchaDialog.collect() }
        advanceUntilIdle()

        coEvery { getCardBalanceUseCase("123456", null) } returns Result.failure(Exception("Error de captcha tras varios intentos"))

        viewModel.refreshBalance()
        advanceUntilIdle()

        assertEquals(true, viewModel.showFallbackCaptchaDialog.value)
    }
}
