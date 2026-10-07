package com.saetasaldo.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.saetasaldo.app.domain.model.BusLine
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.model.RouteNode

data class LineGroupsDto(
    @SerializedName("error") val error: Int?,
    @SerializedName("grupos") val grupos: LineGroupDto?
)

data class LineGroupDto(
    @SerializedName("codGrupo") val codGrupo: String?,
    @SerializedName("subGrupos") val subGrupos: List<LineGroupDto>?,
    @SerializedName("lineas") val lineas: List<BusLineDto>?
)

data class BusLineDto(
    @SerializedName("codLinea") val codLinea: String?,
    @SerializedName("descripcion") val descripcion: String?
)

data class LineRouteDto(
    @SerializedName("error") val error: Int?,
    @SerializedName("nodos") val nodos: List<RouteNodeDto>?
)

data class RouteNodeDto(
    @SerializedName("latitud") val latitud: Double?,
    @SerializedName("longitud") val longitud: Double?,
    @SerializedName("parada") val parada: Boolean?,
    @SerializedName("codigoParada") val codigoParada: String?,
    @SerializedName("descripcionParada") val descripcionParada: String?
)

data class BusPositionsDto(
    @SerializedName("error") val error: Int?,
    @SerializedName("posiciones") val posiciones: List<BusPositionDto>?
)

data class BusPositionDto(
    @SerializedName("interno") val interno: String?,
    @SerializedName("latitud") val latitud: Double?,
    @SerializedName("longitud") val longitud: Double?,
    @SerializedName("orientacion") val orientacion: Double?,
    @SerializedName("proximaParada") val proximaParada: String?,
    @SerializedName("vehiculoRampa") val vehiculoRampa: Boolean?,
    @SerializedName("vehiculoNoVisibles") val vehiculoNoVisibles: Boolean?
)

data class LineNewsDto(
    @SerializedName("error") val error: Int?,
    @SerializedName("novedadLineas") val novedadLineas: List<LineNewsItemDto>?
)

data class LineNewsItemDto(
    @SerializedName("codLinea") val codLinea: String?,
    @SerializedName("novedades") val novedades: List<String>?
)

data class BusConfigDto(
    @SerializedName("error") val error: Int?,
    @SerializedName("latitud") val latitud: Double?,
    @SerializedName("longitud") val longitud: Double?,
    @SerializedName("habilitarMapaBuses") val habilitarMapaBuses: Boolean?,
    @SerializedName("habilitarCuandoViene") val habilitarCuandoViene: Boolean?,
    @SerializedName("urlPrincipalMapas") val urlPrincipalMapas: String?,
    @SerializedName("urlSecundariaMapas") val urlSecundariaMapas: String?
)

fun LineGroupDto.toDomain(): LineGroup = LineGroup(
    codGrupo = codGrupo?.trim().orEmpty(),
    subGroups = subGrupos.orEmpty().map { it.toDomain() },
    lineas = lineas.orEmpty().mapNotNull { it.toDomain() }
)

fun BusLineDto.toDomain(): BusLine? {
    val cod = codLinea?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return BusLine(codLinea = cod, descripcion = descripcion?.trim().orEmpty())
}

fun RouteNodeDto.toDomain(): RouteNode? {
    val lat = latitud ?: return null
    val lng = longitud ?: return null
    return RouteNode(
        latitud = lat,
        longitud = lng,
        parada = parada == true,
        codigoParada = codigoParada.orEmpty().trim(),
        descripcionParada = descripcionParada.orEmpty().trim()
    )
}

fun BusPositionDto.toDomain(): BusPosition? {
    if (vehiculoNoVisibles == true) return null
    val cod = interno?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lat = latitud ?: return null
    val lng = longitud ?: return null
    return BusPosition(
        interno = cod,
        latitud = lat,
        longitud = lng,
        orientacion = orientacion ?: 0.0,
        proximaParada = proximaParada?.trim()?.takeIf { it.isNotEmpty() },
        vehiculoRampa = vehiculoRampa == true
    )
}

fun BusConfigDto.toDomain(): MapConfig = MapConfig(
    habilitarMapaBuses = habilitarMapaBuses == true,
    habilitarCuandoViene = habilitarCuandoViene == true,
    urlPrincipalMapas = urlPrincipalMapas?.trim()?.takeIf { it.isNotEmpty() },
    urlSecundariaMapas = urlSecundariaMapas?.trim()?.takeIf { it.isNotEmpty() },
    latitud = latitud ?: MapConfig.DEFAULT.latitud,
    longitud = longitud ?: MapConfig.DEFAULT.longitud
)
