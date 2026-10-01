package com.saetasaldo.app.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class SaldoResponseDto(
    @SerializedName("error")
    val error: Int, // 0 = OK, 1 = Captcha inválido, 2 = Tarjeta inválida
    @SerializedName("mensaje")
    val message: String? = null,
    @SerializedName("numeroTarjeta")
    val cardNumber: String? = null,
    @SerializedName("tipoTarjeta")
    val cardType: String? = null,
    @SerializedName("estadoTarjeta")
    val cardState: String? = null,
    @SerializedName("saldos")
    val rawSaldos: JsonElement? = null,
    @SerializedName("fechaSaldo")
    val balanceDate: String? = null,
    val explicitBalances: List<SaldoItemDto>? = null
) {
    val balances: List<SaldoItemDto>?
        get() = explicitBalances ?: parseSaldos(rawSaldos)

    val effectiveBalance: Double
        get() = balances?.firstOrNull()?.amount ?: parseAmount(message)

    constructor(
        error: Int,
        message: String? = null,
        cardNumber: String? = null,
        cardType: String? = null,
        cardState: String? = null,
        balances: List<SaldoItemDto>? = null,
        balanceDate: String? = null
    ) : this(
        error = error,
        message = message,
        cardNumber = cardNumber,
        cardType = cardType,
        cardState = cardState,
        rawSaldos = null,
        balanceDate = balanceDate,
        explicitBalances = balances
    )

    companion object {
        private val NUMBER_REGEX = Regex("""-?\s*\d[\d.,]*""")

        fun parseAmount(raw: String?): Double {
            if (raw.isNullOrBlank()) return 0.0
            val isNegative = raw.contains("-")
            val sanitized = raw.replace("$", "").trim()
            val match = NUMBER_REGEX.find(sanitized) ?: return 0.0
            var numStr = match.value.replace(" ", "")

            if (numStr.contains(".") && numStr.contains(",")) {
                numStr = if (numStr.lastIndexOf(",") > numStr.lastIndexOf(".")) {
                    numStr.replace(".", "").replace(",", ".")
                } else {
                    numStr.replace(",", "")
                }
            } else if (numStr.contains(",")) {
                numStr = numStr.replace(",", ".")
            }

            val parsed = numStr.toDoubleOrNull() ?: 0.0
            return if (isNegative && parsed > 0.0) -parsed else parsed
        }

        fun parseSaldos(element: JsonElement?): List<SaldoItemDto> {
            if (element == null || element.isJsonNull) return emptyList()
            val list = mutableListOf<SaldoItemDto>()

            when {
                element.isJsonArray -> {
                    for (item in element.asJsonArray) {
                        when {
                            item.isJsonObject -> {
                                val obj = item.asJsonObject
                                val valueStr = obj.get("value")?.takeIf { !it.isJsonNull }?.asString
                                    ?: obj.get("monto")?.takeIf { !it.isJsonNull }?.asString
                                    ?: obj.get("saldo")?.takeIf { !it.isJsonNull }?.asString
                                    ?: obj.get("importe")?.takeIf { !it.isJsonNull }?.asString
                                    ?: obj.get("credito")?.takeIf { !it.isJsonNull }?.asString

                                val dateStr = obj.get("fecha")?.takeIf { !it.isJsonNull }?.asString
                                    ?: obj.get("date")?.takeIf { !it.isJsonNull }?.asString

                                val amount = parseAmount(valueStr)
                                list.add(SaldoItemDto(amount = amount, date = dateStr, rawValue = valueStr))
                            }
                            item.isJsonPrimitive -> {
                                val primStr = item.asString
                                val amount = parseAmount(primStr)
                                list.add(SaldoItemDto(amount = amount, rawValue = primStr))
                            }
                        }
                    }
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val valueStr = obj.get("value")?.takeIf { !it.isJsonNull }?.asString
                        ?: obj.get("monto")?.takeIf { !it.isJsonNull }?.asString
                        ?: obj.get("saldo")?.takeIf { !it.isJsonNull }?.asString
                        ?: obj.get("importe")?.takeIf { !it.isJsonNull }?.asString
                    val dateStr = obj.get("fecha")?.takeIf { !it.isJsonNull }?.asString
                    val amount = parseAmount(valueStr)
                    list.add(SaldoItemDto(amount = amount, date = dateStr, rawValue = valueStr))
                }
                element.isJsonPrimitive -> {
                    val primStr = element.asString
                    val amount = parseAmount(primStr)
                    list.add(SaldoItemDto(amount = amount, rawValue = primStr))
                }
            }
            return list
        }
    }
}

data class SaldoItemDto(
    @SerializedName("amount")
    val amount: Double = 0.0,
    @SerializedName("fecha")
    val date: String? = null,
    @SerializedName("rawValue")
    val rawValue: String? = null
) {
    val value: Double get() = amount
    val monto: Double get() = amount
    val fecha: String? get() = date

    constructor(amount: Double, date: String? = null) : this(
        amount = amount,
        date = date,
        rawValue = amount.toString()
    )
}
