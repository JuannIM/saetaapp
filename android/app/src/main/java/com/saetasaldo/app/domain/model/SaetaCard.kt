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
     * Balance rendered the way the backend declares it — except when the
     * principal money wallet is mislabeled as passage units, in which case the
     * peso amount is converted to real trips by fare ("≈ 2 pasajes"). Falls
     * back to a currency format for anonymous lookups without wallet metadata.
     */
    fun formattedBalance(fare: Double = CardWallet.DEFAULT_FARE): String {
        val wallet = wallets.principalWallet
        if (wallet != null) {
            return if (wallet.isMoneyWalletInPassageUnits()) {
                wallet.formattedAsTrips(fare)
            } else {
                wallet.formattedBalance()
            }
        }
        return currentBalance?.let { "$ %.2f".format(it) } ?: "$ --"
    }
}
