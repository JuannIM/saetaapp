package com.saetasaldo.app.domain.model

data class BusLine(
    val codLinea: String,
    val descripcion: String
)

data class LineGroup(
    val codGrupo: String,
    val subGroups: List<LineGroup> = emptyList(),
    val lineas: List<BusLine> = emptyList()
)
