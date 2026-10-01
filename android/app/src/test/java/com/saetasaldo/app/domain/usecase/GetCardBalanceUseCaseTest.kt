package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCardBalanceUseCaseTest {
    private val repository = mockk<CardRepository>()
    private val useCase = GetCardBalanceUseCase(repository)

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
}
