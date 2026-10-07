package com.saetasaldo.app.domain.usecase

import com.google.gson.Gson
import com.saetasaldo.app.data.remote.dto.BusPositionsDto
import com.saetasaldo.app.data.remote.dto.LineRouteDto
import com.saetasaldo.app.data.remote.dto.toDomain
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.ui.map.RouteProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateArrivalsUseCaseTest {

    private val gson = Gson()
    private val useCase = EstimateArrivalsUseCase()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("busmap/$name")) {
            "Missing test fixture busmap/$name"
        }.readText()

    private fun fixtureRoute(): LineRoute =
        gson.fromJson(loadFixture("ruta_linea_100.json"), LineRouteDto::class.java)
            .let { LineRoute(it.nodos.orEmpty().mapNotNull { n -> n.toDomain() }) }

    private fun fixtureBuses(): List<BusPosition> =
        gson.fromJson(loadFixture("posiciones_100.json"), BusPositionsDto::class.java)
            .posiciones.orEmpty().mapNotNull { it.toDomain() }

    private fun input(
        interno: String,
        busS: Double?,
        speed: Double?,
        lat: Double = -24.79,
        lng: Double = -65.41,
        proximaParadaS: List<Double> = emptyList()
    ) = EstimateArrivalsUseCase.BusInput(
        interno = interno,
        busS = busS,
        medianSpeedMps = speed,
        busLat = lat,
        busLng = lng,
        proximaParadaS = proximaParadaS
    )

    @Test
    fun `etas on real fixtures are positive ordered and within sane bounds`() {
        val route = fixtureRoute()
        val projector = RouteProjector(route)
        // A stop roughly mid-route so several buses are behind it.
        val stop = route.stops[route.stops.size / 2]
        val stopS = projector.project(stop.latitud, stop.longitud).sMeters
        val buses = fixtureBuses().map { bus ->
            val s = projector.project(bus.latitud, bus.longitud).sMeters
            val proxS = route.stops
                .filter { it.descripcionParada.equals(bus.proximaParada?.trim(), ignoreCase = true) }
                .map { projector.project(it.latitud, it.longitud).sMeters }
            input(bus.interno, s, speed = 8.0, bus.latitud, bus.longitud, proxS)
        }

        val arrivals = useCase(stopS, stop.latitud, stop.longitud, buses)

        assertTrue(arrivals.isNotEmpty())
        val minutes = arrivals.mapNotNull { it.minutes }
        assertTrue(minutes.isNotEmpty())
        assertTrue(minutes.all { it in 1..60 })
        assertEquals(minutes.sorted(), minutes)
        assertTrue(arrivals.size <= 4)
    }

    @Test
    fun `top results are the closest buses by remaining route distance`() {
        val buses = listOf(
            input("far", busS = 1_000.0, speed = 10.0),
            input("near", busS = 4_000.0, speed = 2.0),
            input("mid", busS = 2_500.0, speed = 6.0)
        )
        val arrivals = useCase(stopS = 5_000.0, stopLat = -24.79, stopLng = -65.41, buses = buses)
        assertEquals(listOf("near", "mid", "far"), arrivals.map { it.interno })
    }

    @Test
    fun `minutes round up from remaining distance over speed`() {
        // 1000 m at 10 m/s = 100 s -> ~2 min (round up).
        val arrivals = useCase(
            stopS = 2_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 1_000.0, speed = 10.0))
        )
        assertEquals(2, arrivals.single().minutes)
    }

    @Test
    fun `slow bus is clamped to 2 kmh minimum`() {
        // 500 m at clamped 0.556 m/s = 900 s = 15 min.
        val arrivals = useCase(
            stopS = 1_500.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 1_000.0, speed = 0.1))
        )
        assertEquals(15, arrivals.single().minutes)
    }

    @Test
    fun `truly stopped bus is marked sin estimar`() {
        val arrivals = useCase(
            stopS = 5_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 1_000.0, speed = 0.0))
        )
        val arrival = arrivals.single()
        assertNull(arrival.minutes)
        assertTrue(arrival.stopped)
    }

    @Test
    fun `bus without speed history is marked sin estimar`() {
        val arrivals = useCase(
            stopS = 5_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 1_000.0, speed = null))
        )
        assertTrue(arrivals.single().stopped)
        assertNull(arrivals.single().minutes)
    }

    @Test
    fun `retrograde bus whose announced stop is behind is skipped`() {
        val arrivals = useCase(
            stopS = 5_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(
                input("retro", busS = 2_000.0, speed = 8.0, proximaParadaS = listOf(500.0))
            )
        )
        assertTrue(arrivals.isEmpty())
    }

    @Test
    fun `bus already past the stop is excluded`() {
        val arrivals = useCase(
            stopS = 1_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 2_000.0, speed = 8.0))
        )
        assertTrue(arrivals.isEmpty())
    }

    @Test
    fun `announced stop ahead of the bus passes the sanity check`() {
        val arrivals = useCase(
            stopS = 6_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(
                input("ok", busS = 2_000.0, speed = 8.0, proximaParadaS = listOf(3_000.0))
            )
        )
        assertEquals(1, arrivals.size)
        assertNotNull(arrivals.single().minutes)
    }

    @Test
    fun `without route projection buses fall back to straight line distance`() {
        val arrivals = useCase(
            stopS = null, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = null, speed = 8.0, lat = -24.80, lng = -65.41))
        )
        val arrival = arrivals.single()
        assertNull(arrival.minutes)
        assertTrue(!arrival.stopped)
        assertTrue(arrival.straightLineMeters > 1_000.0)
    }

    @Test
    fun `a pie distance is always populated`() {
        val arrivals = useCase(
            stopS = 5_000.0, stopLat = -24.79, stopLng = -65.41,
            buses = listOf(input("1", busS = 1_000.0, speed = 10.0, lat = -24.79, lng = -65.42))
        )
        assertTrue(arrivals.single().straightLineMeters > 500.0)
    }
}
