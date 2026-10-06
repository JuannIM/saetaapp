package com.saetasaldo.app.data.remote.api

import com.saetasaldo.app.data.remote.dto.RedBusCardListDto
import com.saetasaldo.app.data.remote.dto.RedBusSessionDto
import retrofit2.Response
import retrofit2.http.GET

interface RedBusAccountApiService {

    @GET("rest/loginInternal/usuarioLogeado")
    suspend fun getLoggedUser(): Response<RedBusSessionDto>

    @GET("rest/tarjetaInternal/listaTarjetas")
    suspend fun getLinkedCards(): Response<RedBusCardListDto>
}
