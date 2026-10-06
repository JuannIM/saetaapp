package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncRedBusCardsUseCaseTest {
    private val cardRepository = mockk<CardRepository>()
    private val useCase = SyncRedBusCardsUseCase(cardRepository)

    private fun localCard(number: String) =
        SaetaCard(id = "local-$number", name = "Local $number", cardNumber = number)

    private fun stubApplyBalanceUpdate() {
        coEvery { cardRepository.applyBalanceUpdate(any()) } answers {
            localCard(firstArg<CardBalanceUpdate>().cardNumber)
        }
    }

    @Test
    fun `sync applies each unique account card once`() = runBlocking {
        every { cardRepository.getAllCards() } returns flowOf(emptyList())
        stubApplyBalanceUpdate()

        val result = useCase(
            listOf(
                RedBusAccountCard("12345678", 100.0, CardType.AZUL_COMUN, "Activa", "Personal"),
                // Same normalized number: first occurrence must win, second is ignored.
                RedBusAccountCard(" 12345678 ", 999.0, CardType.VERDE_BENEFICIARIO, "Inactiva", "Duplicada"),
                RedBusAccountCard("87654321", 300.0, null, null, "Trabajo")
            )
        )

        coVerify(exactly = 1) {
            cardRepository.applyBalanceUpdate(
                CardBalanceUpdate("12345678", 100.0, CardType.AZUL_COMUN, "Activa", "Personal", null, emptyList())
            )
        }
        coVerify(exactly = 1) {
            cardRepository.applyBalanceUpdate(
                CardBalanceUpdate("87654321", 300.0, null, null, "Trabajo", null, emptyList())
            )
        }
        coVerify(exactly = 2) { cardRepository.applyBalanceUpdate(any()) }
        verify(exactly = 1) { cardRepository.getAllCards() }
        assertEquals(setOf("12345678", "87654321"), result.syncedCardNumbers)
        assertEquals(2, result.importedCount)
    }

    @Test
    fun `sync reports imported count from cards absent locally`() = runBlocking {
        every { cardRepository.getAllCards() } returns flowOf(listOf(localCard("11111111")))
        stubApplyBalanceUpdate()

        val result = useCase(
            listOf(
                RedBusAccountCard("11111111", 50.0, null, null, null),
                RedBusAccountCard("22222222", 60.0, null, null, null),
                RedBusAccountCard("33333333", 70.0, null, null, null)
            )
        )

        assertEquals(setOf("11111111", "22222222", "33333333"), result.syncedCardNumbers)
        assertEquals(2, result.importedCount)
    }

    @Test
    fun `sync skips a card whose applyBalanceUpdate throws and continues siblings`() = runBlocking {
        every { cardRepository.getAllCards() } returns flowOf(emptyList())
        coEvery { cardRepository.applyBalanceUpdate(any()) } answers {
            val update = firstArg<CardBalanceUpdate>()
            if (update.cardNumber == "22222222") throw IllegalStateException("db write failed")
            localCard(update.cardNumber)
        }

        val result = useCase(
            listOf(
                RedBusAccountCard("11111111", 50.0, null, null, null),
                RedBusAccountCard("22222222", 60.0, null, null, null),
                RedBusAccountCard("33333333", 70.0, null, null, null)
            )
        )

        assertEquals(setOf("11111111", "33333333"), result.syncedCardNumbers)
        assertEquals(2, result.importedCount)
        coVerify(exactly = 3) { cardRepository.applyBalanceUpdate(any()) }
    }

    @Test
    fun `sync rethrows cancellation`() {
        every { cardRepository.getAllCards() } returns flowOf(emptyList())
        coEvery { cardRepository.applyBalanceUpdate(any()) } throws CancellationException("Cancelled")

        try {
            runBlocking {
                useCase(listOf(RedBusAccountCard("11111111", 50.0, null, null, null)))
            }
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }
}
