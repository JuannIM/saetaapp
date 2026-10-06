package com.saetasaldo.app.domain.model

/**
 * A balance wallet ("monedero") as reported by the authenticated RedBus API.
 * The backend declares how the amount should be rendered via [prefix]/[suffix]
 * (e.g. " pasajes" for ticket-count wallets, "$" for money wallets), so display
 * code must honor those fields instead of assuming a currency.
 */
data class CardWallet(
    val id: Int? = null,
    val name: String? = null,
    val balance: Double,
    val isPassageUnit: Boolean = false,
    val prefix: String = "",
    val suffix: String = ""
) {
    fun formattedBalance(): String {
        val amount = if (isPassageUnit || balance == balance.toLong().toDouble()) {
            balance.toLong().toString()
        } else {
            "%.2f".format(balance)
        }
        return listOf(prefix.trim(), amount, suffix.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * The principal wallet is a *money* wallet even when the backend labels it
     * `unidadPasajes: true` with a " pasajes" suffix — the number is pesos and
     * showing "3170 pasajes" is misleading. Convert to real trips by fare.
     */
    fun formattedAsTrips(fare: Double): String {
        val safeFare = if (fare <= 0.0 || fare.isNaN()) DEFAULT_FARE else fare
        val trips = kotlin.math.floor(balance / safeFare).toInt()
        return "≈ $trips ${if (trips == 1) "pasaje" else "pasajes"}"
    }

    /** True when this wallet holds money mislabeled as passage units. */
    fun isMoneyWalletInPassageUnits(): Boolean =
        isPassageUnit && name.equals(PRINCIPAL_WALLET_NAME, ignoreCase = true)

    companion object {
        const val PRINCIPAL_WALLET_NAME = "Principal (Dinero)"
        const val DEFAULT_FARE = 1450.0
    }
}

val List<CardWallet>.principalWallet: CardWallet?
    get() = firstOrNull { it.name.equals(CardWallet.PRINCIPAL_WALLET_NAME, ignoreCase = true) }
        ?: firstOrNull()

val List<CardWallet>.extraWallets: List<CardWallet>
    get() = filter { it !== principalWallet }
        .filter { !it.name.equals(CardWallet.PRINCIPAL_WALLET_NAME, ignoreCase = true) }
