package com.saetasaldo.app.data.repository

import com.saetasaldo.app.data.local.dao.BalanceHistoryDao
import com.saetasaldo.app.data.local.dao.CardDao
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import com.saetasaldo.app.data.local.entity.CardEntity
import com.saetasaldo.app.data.remote.api.SaetaApiService
import com.saetasaldo.app.data.remote.dto.SaldoItemDto
import com.saetasaldo.app.data.remote.dto.SaldoRequestDto
import com.saetasaldo.app.data.remote.dto.SaldoResponseDto
import com.saetasaldo.app.domain.model.CardType
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
}
