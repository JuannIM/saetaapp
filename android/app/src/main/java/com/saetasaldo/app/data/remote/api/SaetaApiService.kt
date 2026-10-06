package com.saetasaldo.app.data.remote.api

import com.saetasaldo.app.data.remote.dto.SaldoRequestDto
import com.saetasaldo.app.data.remote.dto.SaldoResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface SaetaApiService {
    @GET("captcha.png")
    suspend fun getCaptchaImage(@Query("t") timestamp: Long = System.currentTimeMillis()): Response<ResponseBody>

    @GET("rest/getTurnstileKeySite")
    suspend fun getTurnstileKeySite(): Response<ResponseBody>

    @POST("rest/tarjetaInternal/resultadoSaldo")
    @Headers("Content-Type: application/json")
    suspend fun queryBalance(@Body body: SaldoRequestDto): Response<SaldoResponseDto>

    @POST("rest/tarjetaInternal/resultadoSaldo")
    @Headers("Content-Type: application/json")
    suspend fun queryBalanceWithToken(
        @Body body: SaldoRequestDto,
        @Header("X-Use-New-Captcha") useNew: String = "true"
    ): Response<SaldoResponseDto>
}
