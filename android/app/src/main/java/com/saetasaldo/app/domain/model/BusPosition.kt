package com.saetasaldo.app.domain.model

data class BusPosition(
    val interno: String,
    val latitud: Double,
    val longitud: Double,
    val orientacion: Double,
    val proximaParada: String?,
    val vehiculoRampa: Boolean
)
