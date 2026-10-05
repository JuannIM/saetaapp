package com.saetasaldo.app.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.RedBusAccountCard

private const val PRINCIPAL_WALLET_NAME = "Principal (Dinero)"

private val NUMBER_TOKEN_REGEX = Regex("""-?\s*\d[\d.,]*""")

data class RedBusSessionDto(
    @SerializedName("error")
    val error: Int?
)

data class RedBusCardListDto(
    @SerializedName("error")
    val error: Int?,
    @SerializedName("tarjetas")
    val cards: List<RedBusCardDto>?
)

data class RedBusCardDto(
    @SerializedName("nroInterno")
    val internalNumber: String?,
    @SerializedName("relationship")
    val relationship: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("estadoTarjeta")
    val cardState: String?,
    @SerializedName("tarjetaDatosAdicionales")
    val additionalData: RedBusCardAdditionalDataDto?
)

data class RedBusCardAdditionalDataDto(
    @SerializedName("saldos")
    val balances: List<RedBusAccountBalanceDto>?,
    @SerializedName("tipoTarjeta")
    val cardType: String?,
    @SerializedName("estadoTarjeta")
    val cardState: String?,
    @SerializedName("codExterno")
    val externalCode: JsonElement?
)

data class RedBusAccountBalanceDto(
    @SerializedName("saldo")
    val balance: JsonElement?,
    @SerializedName("monedero")
    val wallet: RedBusWalletDto?
)

data class RedBusWalletDto(
    @SerializedName("nombre")
    val name: String?
)

fun RedBusCardListDto.toDomainCards(): List<RedBusAccountCard> {
    if (error != 0) return emptyList()
    return cards.orEmpty().mapNotNull { it.toDomainCard() }
}

private fun RedBusCardDto.toDomainCard(): RedBusAccountCard? {
    val additional = additionalData ?: return null

    val cardNumber = additional.externalCode
        ?.takeIf { it.isJsonPrimitive }
        ?.asJsonPrimitive
        ?.takeIf { it.isNumber || it.isString }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val principalBalance = additional.balances.orEmpty().firstOrNull { entry ->
        entry.wallet?.name?.trim().equals(PRINCIPAL_WALLET_NAME, ignoreCase = true)
    } ?: return null

    val balance = parseBalanceAmount(principalBalance.balance) ?: return null

    return RedBusAccountCard(
        cardNumber = cardNumber,
        balance = balance,
        cardType = additional.cardType.trimToNull()?.let { CardType.fromBackendString(it) },
        cardState = additional.cardState.trimToNull() ?: cardState.trimToNull(),
        suggestedName = description?.trim()?.takeIf { it.isNotEmpty() }
    )
}

private fun parseBalanceAmount(element: JsonElement?): Double? {
    if (element == null || !element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    val amount = when {
        primitive.isNumber -> primitive.asDouble
        primitive.isString -> {
            val raw = primitive.asString
            if (NUMBER_TOKEN_REGEX.find(raw) == null) return null
            SaldoResponseDto.parseAmount(raw)
        }
        else -> return null
    }
    return amount.takeIf { it.isFinite() }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
