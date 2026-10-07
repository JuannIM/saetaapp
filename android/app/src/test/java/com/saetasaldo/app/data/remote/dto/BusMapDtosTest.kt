package com.saetasaldo.app.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMapDtosTest {

    private val gson = Gson()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("busmap/$name")) {
            "Missing test fixture busmap/$name"
        }.readText()

    @Test
    fun `grupos fixture parses error zero and nested tree`() {
        val dto = gson.fromJson(loadFixture("grupos_lineas.json"), LineGroupsDto::class.java)

        assertEquals(0, dto.error)
        val root = dto.grupos
        assertNotNull(root)
        assertTrue(root!!.subGrupos!!.isNotEmpty())
    }

    @Test
    fun `grupos tree contains URBANO with Corredor 1 and line 1B cod 100`() {
        val root = gson.fromJson(loadFixture("grupos_lineas.json"), LineGroupsDto::class.java)
            .grupos!!.toDomain()

        val urbano = root.subGroups.first { it.codGrupo == "URBANO" }
        val corredor1 = urbano.subGroups.first { it.codGrupo == "Corredor 1" }
        val line1B = corredor1.lineas.first { it.codLinea == "100" }
        assertEquals("1B", line1B.descripcion)
    }

    @Test
    fun `grupos tree surfaces direct lineas on top level groups`() {
        val root = gson.fromJson(loadFixture("grupos_lineas.json"), LineGroupsDto::class.java)
            .grupos!!.toDomain()

        val troncales = root.subGroups.first { it.codGrupo == "TRONCALES" }
        assertTrue(troncales.lineas.isNotEmpty())
    }

    @Test
    fun `ruta fixture parses ordered nodes with paradas`() {
        val dto = gson.fromJson(loadFixture("ruta_linea_100.json"), LineRouteDto::class.java)

        assertEquals(0, dto.error)
        val route = com.saetasaldo.app.domain.model.LineRoute(
            dto.nodos.orEmpty().mapNotNull { it.toDomain() }
        )
        assertTrue(route.nodes.size > 100)
        assertTrue(route.stops.isNotEmpty())
        val stop = route.stops.first()
        assertTrue(stop.parada)
        assertTrue(stop.codigoParada.isNotEmpty())
        assertTrue(stop.descripcionParada.isNotEmpty())
    }

    @Test
    fun `posiciones fixture parses buses with all fields`() {
        val dto = gson.fromJson(loadFixture("posiciones_100.json"), BusPositionsDto::class.java)

        assertEquals(0, dto.error)
        val buses = dto.posiciones.orEmpty().mapNotNull { it.toDomain() }
        assertTrue(buses.isNotEmpty())
        val bus = buses.first()
        assertTrue(bus.interno.isNotEmpty())
        assertTrue(bus.latitud != 0.0)
        assertTrue(bus.longitud != 0.0)
    }

    @Test
    fun `null proximaParada is accepted`() {
        val json = """{"error":0,"posiciones":[{"interno":"7","latitud":-24.8,"longitud":-65.4,"orientacion":90.0,"proximaParada":null,"vehiculoRampa":true,"vehiculoNoVisibles":false}]}"""

        val buses = gson.fromJson(json, BusPositionsDto::class.java)
            .posiciones.orEmpty().mapNotNull { it.toDomain() }

        assertEquals(1, buses.size)
        assertNull(buses[0].proximaParada)
        assertTrue(buses[0].vehiculoRampa)
    }

    @Test
    fun `vehiculoNoVisibles buses are filtered from domain`() {
        val json = """{"error":0,"posiciones":[{"interno":"7","latitud":-24.8,"longitud":-65.4,"orientacion":0.0,"proximaParada":null,"vehiculoRampa":false,"vehiculoNoVisibles":true}]}"""

        val buses = gson.fromJson(json, BusPositionsDto::class.java)
            .posiciones.orEmpty().mapNotNull { it.toDomain() }

        assertTrue(buses.isEmpty())
    }

    @Test
    fun `novedades fixture parses line news texts`() {
        val dto = gson.fromJson(loadFixture("novedades_100.json"), LineNewsDto::class.java)

        assertEquals(0, dto.error)
        val news = dto.novedadLineas.orEmpty().flatMap { it.novedades.orEmpty() }
        assertTrue(news.isNotEmpty())
    }

    @Test
    fun `configuracion fixture maps tile urls and center`() {
        val dto = gson.fromJson(loadFixture("configuracion.json"), BusConfigDto::class.java)

        assertEquals(0, dto.error)
        val config = dto.toDomain()
        assertTrue(config.habilitarMapaBuses)
        assertFalse(config.habilitarCuandoViene)
        assertEquals("https://mapmoblrj.red-bus.com.ar/tiles/{z}/{x}/{y}.png", config.urlPrincipalMapas)
        assertTrue(config.urlSecundariaMapas!!.startsWith("https://b.tile.openstreetmap.org"))
        assertEquals(-24.79, config.latitud, 0.01)
        assertEquals(-65.41, config.longitud, 0.01)
    }
}
