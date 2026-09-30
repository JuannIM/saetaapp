package com.saetasaldo.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SaldoRequestDto(
    @SerializedName("nroExternoTarjeta")
    val cardNumber: String,
    @SerializedName("verificacionCaptcha")
    val captchaCode: String
)
