package com.saetasaldo.app.ui.map

import com.google.gson.Gson
import com.saetasaldo.app.data.remote.dto.BusPositionsDto
import com.saetasaldo.app.data.remote.dto.LineRouteDto
import com.saetasaldo.app.data.remote.dto.toDomain
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.RouteNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusInterpolatorTest {

    private val gson = Gson()

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

    /** Straight northbound route: node i is at lat baseLat + i*step. */
    private fun straightRoute(nodes: Int = 11, baseLat: Double = -24.80, step: Double = 0.001) =
        LineRoute(
            (0 until nodes).map {
                RouteNode(
                    latitud = baseLat + it * step,
                    longitud = -65.41,
                    parada = false,
                    codigoParada = "",
                    descripcionParada = ""
                )
            }
        )

    private fun bus(interno: String, lat: Double, lng: Double = -65.41) =
        BusPosition(
            interno = interno,
            latitud = lat,
            longitud = lng,
            orientacion = 0.0,
            proximaParada = null,
            vehiculoRampa = false
        )

    @Test
    fun `projection lands on the route for every fixture bus`() {
        val projector = RouteProjector(fixtureRoute())
        val buses = fixtureBuses()
        assertTrue(buses.isNotEmpty())
        buses.forEach { bus ->
            val proj = projector.project(bus.latitud, bus.longitud)
            assertTrue(proj.sMeters >= 0.0)
            assertTrue(proj.sMeters <= projector.totalMeters)
            // A snapped point sits near its raw fix (route corridor < 500 m).
            assertTrue(
                "bus ${bus.interno} projects ${"%.0f".format(
                    haversineMeters(bus.latitud, bus.longitud, proj.lat, proj.lng)
                )} m away",
                haversineMeters(bus.latitud, bus.longitud, proj.lat, proj.lng) < 500.0
            )
        }
    }

    @Test
    fun `projected point reproduces the on-route raw position`() {
        val projector = RouteProjector(straightRoute())
        val proj = projector.project(-24.795, -65.41)
        assertEquals(-24.795, proj.lat, 1e-6)
        assertEquals(-65.41, proj.lng, 1e-6)
        // node spacing is 0.001 deg ≈ 111.32 m; s ≈ 5 * 111.32
        assertEquals(haversineMeters(-24.80, -65.41, -24.795, -65.41), proj.sMeters, 0.5)
    }

    @Test
    fun `pointAt roundtrips a projected s`() {
        val projector = RouteProjector(straightRoute())
        val s = projector.project(-24.7955, -65.41).sMeters
        val (lat, lng) = projector.pointAt(s)
        assertEquals(-24.7955, lat, 1e-5)
        assertEquals(-65.41, lng, 1e-6)
    }

    @Test
    fun `speed is clamped at 40 kmh`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80)), nowMs = now)
        now += 10_000L
        // ~1.1 km in 10 s ≈ 400 km/h: must clamp to MAX_SPEED_MPS.
        interpolator.onFixes(listOf(bus("1", -24.79)), nowMs = now)
        assertEquals(BusInterpolator.MAX_SPEED_MPS, interpolator.speedMpsOf("1"), 0.001)
    }

    @Test
    fun `speed is zero when the bus moved less than 10 m`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80)), nowMs = now)
        now += 30_000L
        interpolator.onFixes(listOf(bus("1", -24.80 + 0.00001)), nowMs = now)
        assertEquals(0.0, interpolator.speedMpsOf("1"), 0.001)
    }

    @Test
    fun `lerp converges to the observed fix within 1_3 s`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80)), nowMs = now)
        now += 5_000L
        interpolator.onFixes(listOf(bus("1", -24.797)), nowMs = now)
        val fixS = interpolator.routeSOf("1")!!
        // Mid-animation: displayed s is between the old and the new fix.
        val mid = interpolator.tick(now + 600)["1"]!!
        assertTrue(mid.routeS!! < fixS)
        // At ANIM_MS + slack the marker reached the observed fix (the small
        // excess is dead reckoning at clamped speed for 0.1 s).
        val landed = interpolator.tick(now + BusInterpolator.ANIM_MS + 100)["1"]!!
        assertEquals(fixS, landed.routeS!!, BusInterpolator.MAX_SPEED_MPS * 0.1 + 0.5)
        // And it stays within tolerance until 1.3 s after the fix.
        val stable = interpolator.tick(now + 1_300)["1"]!!
        assertTrue(stable.routeS!! >= fixS - 1.0)
    }

    @Test
    fun `dead reckoning advances at estimated speed after the lerp`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80)), nowMs = now)
        // Second fix 30 s later, ~333.96 m north -> v ≈ 11.13 m/s clamped to 11.11.
        now += 30_000L
        interpolator.onFixes(listOf(bus("1", -24.797)), nowMs = now)
        val speed = interpolator.speedMpsOf("1")
        assertTrue(speed in 10.0..BusInterpolator.MAX_SPEED_MPS)
        val fixS = interpolator.routeSOf("1")!!
        // 1.2 s lerp + 10 s dead reckoning: s ≈ fixS + v*10.
        val ticked = interpolator.tick(now + BusInterpolator.ANIM_MS + 10_000)["1"]!!
        assertEquals(fixS + speed * 10.0, ticked.routeS!!, 1.0)
        // Never past the route end.
        val capped = interpolator.tick(now + BusInterpolator.ANIM_MS + BusInterpolator.MAX_RECKON_MS + 60_000)["1"]!!
        assertTrue(capped.routeS!! <= fixS + speed * (BusInterpolator.MAX_RECKON_MS / 1000.0) + 0.5)
    }

    @Test
    fun `dead reckoning stops advancing past MAX_RECKON`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80)), nowMs = now)
        now += 30_000L
        interpolator.onFixes(listOf(bus("1", -24.797)), nowMs = now)
        val speed = interpolator.speedMpsOf("1")
        val fixS = interpolator.routeSOf("1")!!
        val maxS = interpolator.tick(now + BusInterpolator.ANIM_MS + 300_000)["1"]!!.routeS!!
        assertEquals(fixS + speed * 60.0, maxS, 0.5)
    }

    @Test
    fun `buses missing from a fetch keep their last displayed position`() {
        var now = 0L
        val interpolator = BusInterpolator(RouteProjector(straightRoute())) { now }
        interpolator.onFixes(listOf(bus("1", -24.80), bus("2", -24.79)), nowMs = now)
        now += 5_000L
        interpolator.onFixes(listOf(bus("1", -24.797)), nowMs = now)
        val shown = interpolator.tick(now + 2_000)
        assertNotNull(shown["2"])
        val proj = RouteProjector(straightRoute()).project(-24.79, -65.41)
        assertEquals(proj.sMeters, shown["2"]!!.routeS!!, 1.0)
    }

    @Test
    fun `without a route it lerps raw coordinates`() {
        var now = 0L
        val interpolator = BusInterpolator(null) { now }
        interpolator.onFixes(listOf(bus("1", -24.80, -65.41)), nowMs = now)
        now += 10_000L
        interpolator.onFixes(listOf(bus("1", -24.79, -65.40)), nowMs = now)
        val mid = interpolator.tick(now + 600)["1"]!!
        assertTrue(mid.lat in -24.80..-24.79)
        val done = interpolator.tick(now + 1_300)["1"]!!
        assertEquals(-24.79, done.lat, 1e-6)
        assertEquals(-65.40, done.lng, 1e-6)
    }
}
