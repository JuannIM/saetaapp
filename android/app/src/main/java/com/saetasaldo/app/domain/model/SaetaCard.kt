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
     * The primary display is always the real money amount. When the backend
     * mislabels the money wallet as passage units (" pasajes" on "Principal
     * (Dinero)"), the suffix is ignored — [tripsSubtitle] exposes the trip
     * count as secondary info instead.
     */
    fun formattedBalance(): String {
        val wallet = wallets.principalWallet
        if (wallet != null && !wallet.isMoneyWalletInPassageUnits()) {
            return wallet.formattedBalance()
        }
        return currentBalance?.let { "$ %.2f".format(it) } ?: "$ --"
    }

    /**
     * "≈ 2 pasajes" when the principal wallet is money denominated in passage
     * units; null when the concept does not apply to this card.
     */
    fun tripsSubtitle(fare: Double = CardWallet.DEFAULT_FARE): String? =
        wallets.principalWallet
            ?.takeIf { it.isMoneyWalletInPassageUnits() }
            ?.formattedAsTrips(fare)
}
