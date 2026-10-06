package com.saetasaldo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardWalletTest {

    @Test
    fun `passage wallet formats balance with suffix`() {
        val wallet = CardWallet(
            name = "Principal (Dinero)",
            balance = 3170.0,
            isPassageUnit = true,
            suffix = " pasajes"
        )
        assertEquals("3170 pasajes", wallet.formattedBalance())
    }

    @Test
    fun `money wallet formats balance with currency prefix and decimals`() {
        val wallet = CardWallet(
            name = "Principal (Dinero)",
            balance = 1450.5,
            prefix = "$"
        )
        assertEquals("$ 1450.50", wallet.formattedBalance())
    }

    @Test
    fun `wallet without metadata formats bare amount`() {
        val wallet = CardWallet(balance = 32.0, isPassageUnit = true)
        assertEquals("32", wallet.formattedBalance())
    }

    @Test
    fun `principalWallet prefers the named Principal wallet`() {
        val wallets = listOf(
            CardWallet(name = "Beneficio Estudiantil", balance = 30.0, isPassageUnit = true, suffix = " pasajes"),
            CardWallet(name = "Principal (Dinero)", balance = 1450.5, prefix = "$")
        )
        assertEquals(1450.5, wallets.principalWallet?.balance ?: 0.0, 0.01)
        assertEquals(1, wallets.extraWallets.size)
        assertEquals("Beneficio Estudiantil", wallets.extraWallets[0].name)
    }

    @Test
    fun `card formattedBalance converts money wallet in passage units to trips`() {
        val card = SaetaCard(
            id = "1",
            name = "Test",
            cardNumber = "123",
            currentBalance = 3170.0,
            wallets = listOf(
                CardWallet(
                    name = "Principal (Dinero)",
                    balance = 3170.0,
                    isPassageUnit = true,
                    suffix = " pasajes"
                )
            )
        )
        // 3170 pesos / 1450 tarifa = 2 viajes reales, no "3170 pasajes"
        assertEquals("≈ 2 pasajes", card.formattedBalance())
        assertEquals("≈ 1 pasaje", card.copy(
            wallets = listOf(CardWallet(
                name = "Principal (Dinero)",
                balance = 1450.0,
                isPassageUnit = true,
                suffix = " pasajes"
            ))
        ).formattedBalance())
    }

    @Test
    fun `card formattedBalance keeps literal format for true passage wallets`() {
        val card = SaetaCard(
            id = "1",
            name = "Test",
            cardNumber = "123",
            currentBalance = 1450.5,
            wallets = listOf(
                CardWallet(
                    name = "Boleto Estudiantil",
                    balance = 30.0,
                    isPassageUnit = true,
                    suffix = " pasajes"
                ),
                CardWallet(
                    name = "Principal (Dinero)",
                    balance = 1450.5,
                    prefix = "$"
                )
            )
        )
        assertEquals("$ 1450.50", card.formattedBalance())
        assertEquals("Boleto Estudiantil", card.wallets.extraWallets.first().name)
        assertEquals("30 pasajes", card.wallets.extraWallets.first().formattedBalance())
    }

    @Test
    fun `card formattedBalance falls back to currency without wallets`() {
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123", currentBalance = 1450.5)
        assertEquals("$ 1450.50", card.formattedBalance())
    }

    @Test
    fun `card formattedBalance shows placeholder when no balance`() {
        val card = SaetaCard(id = "1", name = "Test", cardNumber = "123")
        assertEquals("$ --", card.formattedBalance())
    }
}
