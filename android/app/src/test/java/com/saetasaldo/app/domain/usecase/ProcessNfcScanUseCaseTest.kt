package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessNfcScanUseCaseTest {
    private val repository = mockk<CardRepository>()
    private val useCase = ProcessNfcScanUseCase(repository)

    @Test
    fun `returns ExistingCardFound when card with nfcUid exists`() = runBlocking {
        val card = SaetaCard(
            id = "card-1",
            name = "Mi Saeta",
            cardNumber = "123456",
            nfcUid = "04A1B2C3",
            currentBalance = 500.0
        )
        coEvery { repository.getCardByNfcUid("04A1B2C3") } returns card

        val result = useCase("04A1B2C3")

        assertTrue(result is NfcScanResult.ExistingCardFound)
        assertEquals(card, (result as NfcScanResult.ExistingCardFound).card)
        coVerify(exactly = 1) { repository.getCardByNfcUid("04A1B2C3") }
    }

    @Test
    fun `returns NewCardDiscovered when nfcUid is not registered`() = runBlocking {
        coEvery { repository.getCardByNfcUid("04FFFF") } returns null

        val result = useCase("04FFFF")

        assertTrue(result is NfcScanResult.NewCardDiscovered)
        assertEquals("04FFFF", (result as NfcScanResult.NewCardDiscovered).nfcUid)
        coVerify(exactly = 1) { repository.getCardByNfcUid("04FFFF") }
    }

    @Test
    fun `converts ByteArray to uppercase hex and looks up existing card`() = runBlocking {
        val card = SaetaCard(
            id = "card-2",
            name = "Pase Libre",
            cardNumber = "987654",
            nfcUid = "04A1B2C3",
            currentBalance = 1200.0
        )
        coEvery { repository.getCardByNfcUid("04A1B2C3") } returns card

        val bytes = byteArrayOf(0x04.toByte(), 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        val result = useCase(bytes)

        assertTrue(result is NfcScanResult.ExistingCardFound)
        assertEquals(card, (result as NfcScanResult.ExistingCardFound).card)
        coVerify(exactly = 1) { repository.getCardByNfcUid("04A1B2C3") }
    }
}
