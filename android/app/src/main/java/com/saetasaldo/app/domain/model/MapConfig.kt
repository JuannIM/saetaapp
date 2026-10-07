package com.saetasaldo.app.domain.model

data class MapConfig(
    val habilitarMapaBuses: Boolean,
    val habilitarCuandoViene: Boolean,
    val urlPrincipalMapas: String?,
    val urlSecundariaMapas: String?,
    val latitud: Double,
    val longitud: Double
) {
    companion object {
        const val REDBUS_TILES_URL = "https://mapmoblrj.red-bus.com.ar/tiles/{z}/{x}/{y}.png"
        const val OSM_TILES_URL = "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png"

        val DEFAULT = MapConfig(
            habilitarMapaBuses = true,
            habilitarCuandoViene = false,
            urlPrincipalMapas = REDBUS_TILES_URL,
            urlSecundariaMapas = OSM_TILES_URL,
            latitud = -24.7899943,
            longitud = -65.4130054
        )
    }
}
