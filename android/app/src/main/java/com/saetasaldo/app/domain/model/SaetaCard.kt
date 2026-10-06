package com.saetasaldo.app.domain.model

data class SaetaCard(
    val id: String,
    val name: String,
    val cardNumber: String,
    val nfcUid: String? = null,
    val type: CardType = CardType.AZUL_COMUN,
    val currentBalance: Double? = null,
    val lastUpdated: Long? = null,
    val isFavorite: Boolean = false,
    val cardState: String? = null,
    val colorArgb: Int? = null,
    val internalNumber: String? = null,
    val wallets: List<CardWallet> = emptyList()
) {
    /**
     * Balance rendered the way the backend declares it: "3170 pasajes" for
     * passage-unit wallets, "$ 1450.50" for money wallets. Falls back to a
     * currency format for anonymous lookups that carry no wallet metadata.
     */
    fun formattedBalance(): String {
        val wallet = wallets.principalWallet
        if (wallet != null) return wallet.formattedBalance()
        return currentBalance?.let { "$ %.2f".format(it) } ?: "$ --"
    }
}
