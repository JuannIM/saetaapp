package com.saetasaldo.app.domain.usecase

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Device-computed ETA per stop ("cuándo viene"): the server's own ETA is
 * disabled in Salta, so arrivals are estimated from each bus's projected
 * route distance `s` and its rolling median speed.
 */
class EstimateArrivalsUseCase {

    data class BusInput(
        val interno: String,
        /** Route-distance of the bus; null when it can't be projected. */
        val busS: Double?,
        /** Median of the last fixes' speed (m/s); null/0 = truly stopped. */
        val medianSpeedMps: Double?,
        val busLat: Double,
        val busLng: Double,
        /** Route-distances of the announced `proximaParada` (may repeat on loops). */
        val proximaParadaS: List<Double> = emptyList()
    )

    data class StopArrival(
        val interno: String,
        /** Rounded-up minutes; null when there is no estimate. */
        val minutes: Int?,
        /** Straight-line "a pie" distance bus → stop. */
        val straightLineMeters: Double,
        /** True when the bus is ahead but its median speed is zero. */
        val stopped: Boolean
    )

    operator fun invoke(
        stopS: Double?,
        stopLat: Double,
        stopLng: Double,
        buses: List<BusInput>,
        maxResults: Int = 3
    ): List<StopArrival> {
        val arrivals = mutableListOf<Pair<Double, StopArrival>>()
        val unestimable = mutableListOf<StopArrival>()
        buses.forEach { bus ->
            val straight = haversineMeters(bus.busLat, bus.busLng, stopLat, stopLng)
            val s = bus.busS
            if (stopS == null || s == null) {
                unestimable += StopArrival(bus.interno, null, straight, stopped = false)
                return@forEach
            }
            // Direction sanity: the announced next stop must lie ahead of the bus.
            if (bus.proximaParadaS.isNotEmpty() &&
                bus.proximaParadaS.none { it >= s - DIRECTION_TOLERANCE_M }
            ) {
                return@forEach
            }
            // Only stops ahead of the bus on the polyline.
            if (stopS <= s) return@forEach
            val v = bus.medianSpeedMps
            if (v == null || v <= 0.0) {
                unestimable += StopArrival(bus.interno, null, straight, stopped = true)
                return@forEach
            }
            val vEff = v.coerceAtLeast(MIN_SPEED_MPS)
            val minutes = ceil((stopS - s) / vEff / 60.0).toInt()
            arrivals += (stopS - s) to StopArrival(bus.interno, minutes, straight, stopped = false)
        }
        arrivals.sortBy { it.first }
        return (arrivals.map { it.second }.take(maxResults) + unestimable)
            .take(MAX_ROWS)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    companion object {
        /** Buses slower than this still report a conservative estimate. */
        const val MIN_SPEED_MPS = 2_000.0 / 3_600.0
        private const val DIRECTION_TOLERANCE_M = 50.0
        private const val MAX_ROWS = 4
    }
}
