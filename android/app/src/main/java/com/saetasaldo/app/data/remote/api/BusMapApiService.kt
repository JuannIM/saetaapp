package com.saetasaldo.app.data.remote.api

import com.saetasaldo.app.data.remote.dto.BusConfigDto
import com.saetasaldo.app.data.remote.dto.BusPositionsDto
import com.saetasaldo.app.data.remote.dto.LineGroupsDto
import com.saetasaldo.app.data.remote.dto.LineNewsDto
import com.saetasaldo.app.data.remote.dto.LineRouteDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BusMapApiService {

    @GET("rest/gruposLineas")
    suspend fun getGruposLineas(): Response<LineGroupsDto>

    @GET("rest/rutaLinea/{codLinea}")
    suspend fun getRutaLinea(@Path("codLinea") codLinea: String): Response<LineRouteDto>

    @GET("rest/posicionesBuses/{codLinea}")
    suspend fun getPosicionesBuses(@Path("codLinea") codLinea: String): Response<BusPositionsDto>

    @GET("rest/novedadesLineas")
    suspend fun getNovedadesLineas(@Query("codLineas") codLineas: String): Response<LineNewsDto>

    @GET("rest/getConfiguracion")
    suspend fun getConfiguracion(): Response<BusConfigDto>
}
