package com.saetasaldo.app.data.repository

import com.saetasaldo.app.data.local.dao.BalanceHistoryDao
import com.saetasaldo.app.data.local.dao.CardDao
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import com.saetasaldo.app.data.local.entity.CardEntity
import com.saetasaldo.app.data.remote.api.SaetaApiService
import com.saetasaldo.app.data.remote.dto.SaldoItemDto
import com.saetasaldo.app.data.remote.dto.SaldoRequestDto
import com.saetasaldo.app.data.remote.dto.SaldoResponseDto
import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.repository.TurnstileTokenProvider
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class CardRepositoryTest {

    private val cardDao = mockk<CardDao>(relaxed = true)
    private val balanceHistoryDao = mockk<BalanceHistoryDao>(relaxed = true)
    private val apiService = mockk<SaetaApiService>()
    private val solveCaptchaUseCase = mockk<SolveCaptchaUseCase>()

    private val repository = CardRepositoryImpl(
        cardDao = cardDao,
        balanceHistoryDao = balanceHistoryDao,
        apiService = apiService,
        solveCaptchaUseCase = solveCaptchaUseCase
    )

    @Test
    fun `refreshCardBalance queries API with auto captcha and records history on first read`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            cardType = "COMUN",
            cardState = "ACTIVA",
            balances = listOf(SaldoItemDto(amount = 1500.0, date = "2026-09-30")),
            balanceDate = "2026-09-30"
        )
        coEvery { apiService.queryBalance(SaldoRequestDto("123456", "ABCD")) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null
        coEvery { balanceHistoryDao.getLatestBalanceRecord(any()) } returns null

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isSuccess)
        val card = result.getOrNull()
        assertEquals(1500.0, card?.currentBalance ?: 0.0, 0.01)

        coVerify(exactly = 1) { cardDao.insertCard(any()) }
        coVerify(exactly = 1) { balanceHistoryDao.insertRecord(match { it.balance == 1500.0 && it.difference == 0.0 }) }
    }

    @Test
    fun `refreshCardBalance does not record duplicate history when balance has not changed`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 1500.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        val existingCard = CardEntity(id = "card-1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { cardDao.getCardByNumber("123456") } returns existingCard
        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns BalanceHistoryEntity(
            cardId = "card-1",
            balance = 1500.0,
            difference = 0.0
        )

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isSuccess)

        // Card is updated in DB, but NO history record is inserted since balance is identical
        coVerify(exactly = 1) { cardDao.insertCard(any()) }
        coVerify(exactly = 0) { balanceHistoryDao.insertRecord(any()) }
    }

    @Test
    fun `refreshCardBalance records history with delta when balance changes`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 810.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        val existingCard = CardEntity(id = "card-1", name = "Test", cardNumber = "123456", currentBalance = 1500.0)
        coEvery { cardDao.getCardByNumber("123456") } returns existingCard
        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns BalanceHistoryEntity(
            cardId = "card-1",
            balance = 1500.0,
            difference = 0.0
        )

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isSuccess)

        // Delta should be 810 - 1500 = -690
        coVerify(exactly = 1) {
            balanceHistoryDao.insertRecord(match { it.balance == 810.0 && it.difference == -690.0 })
        }
    }

    @Test
    fun `refreshCardBalance uses manual captcha directly without calling solver`() = runBlocking {
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 500.0))
        )
        coEvery { apiService.queryBalance(SaldoRequestDto("123456", "MANUAL12")) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null

        val result = repository.refreshCardBalance("123456", manualCaptcha = "MANUAL12")
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 1) { apiService.queryBalance(SaldoRequestDto("123456", "MANUAL12")) }
    }

    @Test
    fun `refreshCardBalance handles backend error 1 captcha mismatch`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("WRONG")
        val errorDto = SaldoResponseDto(error = 1)
        coEvery { apiService.queryBalance(any()) } returns Response.success(errorDto)

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Captcha incorrecto") == true)
    }

    @Test
    fun `refreshCardBalance handles backend error 2 card not found`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val errorDto = SaldoResponseDto(error = 2)
        coEvery { apiService.queryBalance(any()) } returns Response.success(errorDto)

        val result = repository.refreshCardBalance("999999")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no existe") == true)
    }

    @Test
    fun `refreshCardBalance handles HTTP 500 failure`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaType())
        coEvery { apiService.queryBalance(any()) } returns Response.error(500, errorBody)

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Error de conexión") == true)
    }

    @Test
    fun `refreshCardBalance propagates CancellationException without wrapping`() {
        coEvery { solveCaptchaUseCase.invoke() } throws CancellationException("Cancelled")

        try {
            runBlocking {
                repository.refreshCardBalance("123456")
            }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `account update preserves local alias nfc and favorite`() = runBlocking {
        val existing = CardEntity(
            id = "card-1",
            name = "Mi Alias",
            cardNumber = "87654321",
            nfcUid = "04A1B2C3",
            type = CardType.AZUL_COMUN,
            currentBalance = 100.0,
            isFavorite = true,
            cardState = "ACTIVA"
        )
        coEvery { cardDao.getCardByNumber("87654321") } returns existing

        val updated = repository.applyBalanceUpdate(
            CardBalanceUpdate(
                cardNumber = "87654321",
                balance = 250.0,
                cardType = null,
                cardState = null,
                suggestedName = null
            )
        )

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals("card-1", slot.captured.id)
        assertEquals("Mi Alias", slot.captured.name)
        assertEquals("04A1B2C3", slot.captured.nfcUid)
        assertTrue(slot.captured.isFavorite)
        assertEquals(250.0, slot.captured.currentBalance ?: 0.0, 0.01)
        assertTrue(slot.captured.lastUpdated != null)
        assertEquals("card-1", updated.id)
        assertEquals("Mi Alias", updated.name)
    }

    @Test
    fun `account update preserves existing type and state when remote values are absent`() = runBlocking {
        val existing = CardEntity(
            id = "card-1",
            name = "Test",
            cardNumber = "87654321",
            type = CardType.VERDE_BENEFICIARIO,
            cardState = "SUSPENDIDA"
        )
        coEvery { cardDao.getCardByNumber("87654321") } returns existing

        repository.applyBalanceUpdate(
            CardBalanceUpdate(
                cardNumber = "87654321",
                balance = 250.0,
                cardType = null,
                cardState = null,
                suggestedName = null
            )
        )

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals(CardType.VERDE_BENEFICIARIO, slot.captured.type)
        assertEquals("SUSPENDIDA", slot.captured.cardState)
    }

    @Test
    fun `account update applies non-null remote type and trimmed state`() = runBlocking {
        val existing = CardEntity(
            id = "card-1",
            name = "Test",
            cardNumber = "87654321",
            type = CardType.AZUL_COMUN,
            cardState = "ACTIVA"
        )
        coEvery { cardDao.getCardByNumber("87654321") } returns existing

        repository.applyBalanceUpdate(
            CardBalanceUpdate(
                cardNumber = "87654321",
                balance = 250.0,
                cardType = CardType.VERDE_BENEFICIARIO,
                cardState = "  BLOQUEADA  ",
                suggestedName = null
            )
        )

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals(CardType.VERDE_BENEFICIARIO, slot.captured.type)
        assertEquals("BLOQUEADA", slot.captured.cardState)
    }

    @Test
    fun `account update creates missing card with trimmed suggested name`() = runBlocking {
        coEvery { cardDao.getCardByNumber("87654321") } returns null

        val updated = repository.applyBalanceUpdate(
            CardBalanceUpdate(
                cardNumber = "  87654321  ",
                balance = 250.0,
                cardType = CardType.VERDE_BENEFICIARIO,
                cardState = "ACTIVA",
                suggestedName = "  Mi Plastico  "
            )
        )

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals("Mi Plastico", slot.captured.name)
        assertEquals("87654321", slot.captured.cardNumber)
        assertEquals(CardType.VERDE_BENEFICIARIO, slot.captured.type)
        assertEquals("Mi Plastico", updated.name)
    }

    @Test
    fun `account update creates missing card with default name and default type`() = runBlocking {
        coEvery { cardDao.getCardByNumber("87654321") } returns null

        repository.applyBalanceUpdate(
            CardBalanceUpdate(
                cardNumber = "87654321",
                balance = 250.0,
                cardType = null,
                cardState = null,
                suggestedName = "   "
            )
        )

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals("Tarjeta SAETA", slot.captured.name)
        assertEquals(CardType.AZUL_COMUN, slot.captured.type)
        assertNull(slot.captured.cardState)
        assertNull(slot.captured.nfcUid)
        assertTrue(!slot.captured.isFavorite)
    }

    @Test
    fun `account update records first history and changed balance delta`() = runBlocking {
        val existing = CardEntity(id = "card-1", name = "Test", cardNumber = "87654321")
        coEvery { cardDao.getCardByNumber("87654321") } returns existing
        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns null

        repository.applyBalanceUpdate(
            CardBalanceUpdate("87654321", 300.0, null, null, null)
        )

        coVerify(exactly = 1) {
            balanceHistoryDao.insertRecord(match {
                it.cardId == "card-1" && it.balance == 300.0 && it.difference == 0.0
            })
        }

        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns BalanceHistoryEntity(
            cardId = "card-1",
            balance = 300.0,
            difference = 0.0
        )

        repository.applyBalanceUpdate(
            CardBalanceUpdate("87654321", 450.0, null, null, null)
        )

        coVerify(exactly = 1) {
            balanceHistoryDao.insertRecord(match {
                it.cardId == "card-1" && it.balance == 450.0 && it.difference == 150.0
            })
        }
    }

    @Test
    fun `account update does not duplicate unchanged history`() = runBlocking {
        val existing = CardEntity(
            id = "card-1",
            name = "Test",
            cardNumber = "87654321",
            currentBalance = 300.0
        )
        coEvery { cardDao.getCardByNumber("87654321") } returns existing
        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns BalanceHistoryEntity(
            cardId = "card-1",
            balance = 300.0,
            difference = 0.0
        )

        repository.applyBalanceUpdate(
            CardBalanceUpdate("87654321", 300.0, null, null, null)
        )

        coVerify(exactly = 1) { cardDao.insertCard(any()) }
        coVerify(exactly = 0) { balanceHistoryDao.insertRecord(any()) }
    }

    @Test
    fun `account update rejects blank card number`() = runBlocking {
        try {
            repository.applyBalanceUpdate(
                CardBalanceUpdate("   ", 100.0, null, null, null)
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        coVerify(exactly = 0) { cardDao.getCardByNumber(any()) }
        coVerify(exactly = 0) { cardDao.insertCard(any()) }
        coVerify(exactly = 0) { balanceHistoryDao.insertRecord(any()) }
    }

    @Test
    fun `account update rejects NaN and infinity`() = runBlocking {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            try {
                repository.applyBalanceUpdate(
                    CardBalanceUpdate("87654321", bad, null, null, null)
                )
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }

        coVerify(exactly = 0) { cardDao.getCardByNumber(any()) }
        coVerify(exactly = 0) { cardDao.insertCard(any()) }
        coVerify(exactly = 0) { balanceHistoryDao.insertRecord(any()) }
    }

    @Test
    fun `anonymous success delegates through the same persistence behavior`() = runBlocking {
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val existing = CardEntity(
            id = "card-1",
            name = "Alias Local",
            cardNumber = "123456",
            nfcUid = "04F0E1D2",
            type = CardType.VERDE_BENEFICIARIO,
            isFavorite = true,
            cardState = "SUSPENDIDA"
        )
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 900.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns existing
        coEvery { balanceHistoryDao.getLatestBalanceRecord("card-1") } returns null

        val result = repository.refreshCardBalance("123456")
        assertTrue(result.isSuccess)

        val slot = slot<CardEntity>()
        coVerify(exactly = 1) { cardDao.insertCard(capture(slot)) }
        assertEquals("card-1", slot.captured.id)
        assertEquals("Alias Local", slot.captured.name)
        assertEquals("04F0E1D2", slot.captured.nfcUid)
        assertTrue(slot.captured.isFavorite)
        assertEquals(CardType.VERDE_BENEFICIARIO, slot.captured.type)
        assertEquals("ACTIVA", slot.captured.cardState)
        assertEquals(900.0, slot.captured.currentBalance ?: 0.0, 0.01)
        coVerify(exactly = 1) { balanceHistoryDao.insertRecord(match { it.balance == 900.0 && it.difference == 0.0 }) }
    }

    // --- Turnstile token path ---

    private val turnstileProvider = mockk<TurnstileTokenProvider>()

    private fun repositoryWithTurnstile() = CardRepositoryImpl(
        cardDao = cardDao,
        balanceHistoryDao = balanceHistoryDao,
        apiService = apiService,
        solveCaptchaUseCase = solveCaptchaUseCase,
        turnstileProvider = turnstileProvider
    )

    @Test
    fun `refreshCardBalance resolves turnstile token and never requests captcha image or OCR`() = runBlocking {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } returns Result.success("TURNSTILE-TOKEN")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 700.0))
        )
        coEvery {
            apiService.queryBalanceWithToken(SaldoRequestDto("123456", "TURNSTILE-TOKEN"), "true")
        } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null
        coEvery { balanceHistoryDao.getLatestBalanceRecord(any()) } returns null

        val result = repository.refreshCardBalance("123456")

        assertTrue(result.isSuccess)
        assertEquals(700.0, result.getOrNull()?.currentBalance ?: 0.0, 0.01)
        coVerify(exactly = 1) { turnstileProvider.getToken() }
        coVerify(exactly = 1) {
            apiService.queryBalanceWithToken(SaldoRequestDto("123456", "TURNSTILE-TOKEN"), "true")
        }
        coVerify(exactly = 0) { apiService.getCaptchaImage(any()) }
        coVerify(exactly = 0) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 0) { apiService.queryBalance(any()) }
    }

    @Test
    fun `refreshCardBalance returns card-not-found from token path without OCR retry`() = runBlocking {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } returns Result.success("TURNSTILE-TOKEN")
        coEvery {
            apiService.queryBalanceWithToken(any(), any())
        } returns Response.success(SaldoResponseDto(error = 2))

        val result = repository.refreshCardBalance("999999")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no existe") == true)
        coVerify(exactly = 0) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 0) { apiService.getCaptchaImage(any()) }
    }

    @Test
    fun `refreshCardBalance falls back to OCR when provider fails`() = runBlocking {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } returns Result.failure(
            IllegalStateException("Turnstile timeout")
        )
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 800.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null
        coEvery { balanceHistoryDao.getLatestBalanceRecord(any()) } returns null

        val result = repository.refreshCardBalance("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { turnstileProvider.getToken() }
        coVerify(exactly = 0) { apiService.queryBalanceWithToken(any(), any()) }
        coVerify(exactly = 1) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 1) { apiService.queryBalance(SaldoRequestDto("123456", "ABCD")) }
    }

    @Test
    fun `refreshCardBalance falls back to OCR when token is rejected with error 1`() = runBlocking {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } returns Result.success("STALE-TOKEN")
        coEvery {
            apiService.queryBalanceWithToken(any(), any())
        } returns Response.success(SaldoResponseDto(error = 1))
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 600.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null
        coEvery { balanceHistoryDao.getLatestBalanceRecord(any()) } returns null

        val result = repository.refreshCardBalance("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiService.queryBalanceWithToken(any(), "true") }
        coVerify(exactly = 1) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 1) { apiService.queryBalance(SaldoRequestDto("123456", "ABCD")) }
    }

    @Test
    fun `refreshCardBalance falls back to OCR when token query fails on HTTP`() = runBlocking {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } returns Result.success("TURNSTILE-TOKEN")
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaType())
        coEvery {
            apiService.queryBalanceWithToken(any(), any())
        } returns Response.error(500, errorBody)
        coEvery { solveCaptchaUseCase.invoke() } returns Result.success("ABCD")
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 400.0))
        )
        coEvery { apiService.queryBalance(any()) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null
        coEvery { balanceHistoryDao.getLatestBalanceRecord(any()) } returns null

        val result = repository.refreshCardBalance("123456")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 1) { apiService.queryBalance(SaldoRequestDto("123456", "ABCD")) }
    }

    @Test
    fun `refreshCardBalance with manual captcha never calls turnstile provider`() = runBlocking {
        val repository = repositoryWithTurnstile()
        val successDto = SaldoResponseDto(
            error = 0,
            cardNumber = "123456",
            balances = listOf(SaldoItemDto(amount = 500.0))
        )
        coEvery { apiService.queryBalance(SaldoRequestDto("123456", "MANUAL12")) } returns Response.success(successDto)
        coEvery { cardDao.getCardByNumber("123456") } returns null

        val result = repository.refreshCardBalance("123456", manualCaptcha = "MANUAL12")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { turnstileProvider.getToken() }
        coVerify(exactly = 0) { apiService.queryBalanceWithToken(any(), any()) }
        coVerify(exactly = 1) { apiService.queryBalance(SaldoRequestDto("123456", "MANUAL12")) }
    }

    @Test
    fun `refreshCardBalance propagates provider CancellationException without wrapping`() {
        val repository = repositoryWithTurnstile()
        coEvery { turnstileProvider.getToken() } throws CancellationException("Cancelled")

        try {
            runBlocking {
                repository.refreshCardBalance("123456")
            }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }

        coVerify(exactly = 0) { solveCaptchaUseCase.invoke() }
        coVerify(exactly = 0) { apiService.queryBalance(any()) }
    }
}
