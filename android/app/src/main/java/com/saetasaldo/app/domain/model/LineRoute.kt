package com.saetasaldo.app.domain.model

data class RouteNode(
    val latitud: Double,
    val longitud: Double,
    val parada: Boolean,
    val codigoParada: String,
    val descripcionParada: String
)

data class LineRoute(
    val nodes: List<RouteNode>
) {
    val stops: List<RouteNode> get() = nodes.filter { it.parada }
}
