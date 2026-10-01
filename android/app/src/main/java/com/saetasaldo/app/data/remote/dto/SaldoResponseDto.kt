package com.saetasaldo.app.data.remote.dto

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
    val balances: List<SaldoItemDto>? = null,
    @SerializedName("fechaSaldo")
    val balanceDate: String? = null
)

data class SaldoItemDto(
    @SerializedName("value")
    val value: Double? = null,
    @SerializedName("monto")
    val monto: Double? = null,
    @SerializedName("fecha")
    val date: String? = null
) {
    val amount: Double get() = value ?: monto ?: 0.0

    constructor(amount: Double, date: String? = null) : this(value = amount, monto = amount, date = date)
}
