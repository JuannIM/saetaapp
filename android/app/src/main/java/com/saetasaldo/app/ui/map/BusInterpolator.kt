package com.saetasaldo.app.ui.map

import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineRoute
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0
private const val METERS_PER_DEGREE = 111_320.0

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))
}

/**
 * Projects lat/lng onto the route polyline, yielding `s`: the distance in
 * meters from the route start along the polyline. Segment lengths use
 * haversine; the point-in-segment projection uses a local equirectangular
 * plane (accurate at city scale).
 */
class RouteProjector(route: LineRoute) {

    data class Projection(val sMeters: Double, val lat: Double, val lng: Double)

    private val lats: DoubleArray
    private val lngs: DoubleArray
    private val cumMeters: DoubleArray
    private val segMeters: DoubleArray
    private val cosRefLat: Double
    private val refLat: Double
    private val refLng: Double
    val totalMeters: Double

    init {
        val n = route.nodes.size
        lats = DoubleArray(n)
        lngs = DoubleArray(n)
        cumMeters = DoubleArray(n)
        segMeters = DoubleArray(maxOf(0, n - 1))
        refLat = route.nodes.firstOrNull()?.latitud ?: 0.0
        refLng = route.nodes.firstOrNull()?.longitud ?: 0.0
        cosRefLat = cos(Math.toRadians(refLat))
        var acc = 0.0
        for (i in 0 until n) {
            val node = route.nodes[i]
            lats[i] = node.latitud
            lngs[i] = node.longitud
            if (i > 0) {
                segMeters[i - 1] = haversineMeters(lats[i - 1], lngs[i - 1], lats[i], lngs[i])
                acc += segMeters[i - 1]
            }
            cumMeters[i] = acc
        }
        totalMeters = acc
    }

    private fun xOf(lat: Double, lng: Double): Double =
        (lng - refLng) * METERS_PER_DEGREE * cosRefLat

    private fun yOf(lat: Double, lng: Double): Double =
        (lat - refLat) * METERS_PER_DEGREE

    fun project(lat: Double, lng: Double): Projection {
        if (lats.isEmpty()) return Projection(0.0, lat, lng)
        if (lats.size == 1) return Projection(0.0, lats[0], lngs[0])
        val px = xOf(lat, lng)
        val py = yOf(lat, lng)
        var bestDist2 = Double.MAX_VALUE
        var bestS = 0.0
        var bestX = 0.0
        var bestY = 0.0
        for (i in segMeters.indices) {
            val ax = xOf(lats[i], lngs[i])
            val ay = yOf(lats[i], lngs[i])
            val abx = xOf(lats[i + 1], lngs[i + 1]) - ax
            val aby = yOf(lats[i + 1], lngs[i + 1]) - ay
            val ab2 = abx * abx + aby * aby
            val t = if (ab2 > 0) {
                (((px - ax) * abx + (py - ay) * aby) / ab2).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val cx = ax + t * abx
            val cy = ay + t * aby
            val d2 = (px - cx) * (px - cx) + (py - cy) * (py - cy)
            if (d2 < bestDist2) {
                bestDist2 = d2
                bestS = cumMeters[i] + t * segMeters[i]
                bestX = cx
                bestY = cy
            }
        }
        return Projection(
            sMeters = bestS,
            lat = refLat + bestY / METERS_PER_DEGREE,
            lng = refLng + bestX / (METERS_PER_DEGREE * cosRefLat)
        )
    }

    /** Lat/lng of the point `sMeters` along the polyline (clamped to route). */
    fun pointAt(sMeters: Double): Pair<Double, Double> {
        if (lats.isEmpty()) return 0.0 to 0.0
        val s = sMeters.coerceIn(0.0, totalMeters)
        var i = cumMeters.size - 1
        while (i > 0 && cumMeters[i] > s) i--
        if (i >= segMeters.size) return lats.last() to lngs.last()
        val segLen = segMeters[i]
        val t = if (segLen > 0) ((s - cumMeters[i]) / segLen).coerceIn(0.0, 1.0) else 0.0
        return lats[i] + (lats[i + 1] - lats[i]) * t to lngs[i] + (lngs[i + 1] - lngs[i]) * t
    }
}

data class DisplayedBus(
    val lat: Double,
    val lng: Double,
    val routeS: Double?
)

/**
 * Keeps per-interno animation state so markers glide between fixes instead of
 * teleporting: on each fix the marker lerps to the observed point over ~1.2 s,
 * then keeps advancing along the polyline at the estimated speed (dead
 * reckoning, capped) until the next fix snaps it back.
 */
class BusInterpolator(
    private var projector: RouteProjector?,
    private val clockMs: () -> Long = System::currentTimeMillis
) {

    private class Track {
        var fixS = 0.0
        var fixLat = 0.0
        var fixLng = 0.0
        var fixAtMs = 0L
        var animFromS = 0.0
        var animFromLat = 0.0
        var animFromLng = 0.0
        var animStartMs = 0L
        var speedMps = 0.0
        var displayedS = 0.0
        var displayedLat = 0.0
        var displayedLng = 0.0
        var hasFix = false
        val speedHistory = ArrayDeque<Double>()
    }

    private val tracks = linkedMapOf<String, Track>()

    /** Swap the projector (e.g. when the route arrives after the first fix). */
    fun setRoute(projector: RouteProjector?, latestFixes: List<BusPosition>) {
        this.projector = projector
        tracks.clear()
        onFixes(latestFixes)
    }

    fun onFixes(buses: List<BusPosition>, nowMs: Long = clockMs()) {
        buses.forEach { bus ->
            val tr = tracks.getOrPut(bus.interno) { Track() }
            val proj = projector?.project(bus.latitud, bus.longitud)
            val s = proj?.sMeters ?: 0.0
            val snapLat = proj?.lat ?: bus.latitud
            val snapLng = proj?.lng ?: bus.longitud
            if (tr.hasFix) {
                val dtSec = (nowMs - tr.fixAtMs) / 1000.0
                if (dtSec > 0 && projector != null) {
                    val ds = s - tr.fixS
                    tr.speedMps = if (abs(ds) < MIN_MOVE_METERS) {
                        0.0
                    } else {
                        (ds / dtSec).coerceIn(0.0, MAX_SPEED_MPS)
                    }
                    tr.speedHistory.addLast(tr.speedMps)
                    while (tr.speedHistory.size > SPEED_HISTORY_SIZE) {
                        tr.speedHistory.removeFirst()
                    }
                }
                tr.animFromS = tr.displayedS
                tr.animFromLat = tr.displayedLat
                tr.animFromLng = tr.displayedLng
            } else {
                tr.animFromS = s
                tr.animFromLat = snapLat
                tr.animFromLng = snapLng
                tr.displayedS = s
                tr.displayedLat = snapLat
                tr.displayedLng = snapLng
                tr.speedMps = 0.0
                tr.hasFix = true
            }
            tr.fixS = s
            tr.fixLat = snapLat
            tr.fixLng = snapLng
            tr.fixAtMs = nowMs
            tr.animStartMs = nowMs
        }
    }

    fun tick(nowMs: Long = clockMs()): Map<String, DisplayedBus> {
        val proj = projector
        val out = LinkedHashMap<String, DisplayedBus>(tracks.size)
        tracks.forEach { (interno, tr) ->
            val phase = (nowMs - tr.animStartMs).coerceAtLeast(0L)
            val animFrac = (phase.toDouble() / ANIM_MS).coerceIn(0.0, 1.0)
            if (proj != null) {
                val baseS = tr.animFromS + (tr.fixS - tr.animFromS) * animFrac
                val reckonMs = (phase - ANIM_MS).coerceIn(0L, MAX_RECKON_MS)
                val s = (baseS + tr.speedMps * reckonMs / 1000.0)
                    .coerceIn(0.0, proj.totalMeters)
                val (lat, lng) = proj.pointAt(s)
                tr.displayedS = s
                tr.displayedLat = lat
                tr.displayedLng = lng
                out[interno] = DisplayedBus(lat, lng, s)
            } else {
                val lat = tr.animFromLat + (tr.fixLat - tr.animFromLat) * animFrac
                val lng = tr.animFromLng + (tr.fixLng - tr.animFromLng) * animFrac
                tr.displayedLat = lat
                tr.displayedLng = lng
                out[interno] = DisplayedBus(lat, lng, null)
            }
        }
        return out
    }

    fun speedMpsOf(interno: String): Double = tracks[interno]?.speedMps ?: 0.0

    /** Rolling median of the last ≤5 fix-to-fix speeds, for ETA. */
    fun medianSpeedMpsOf(interno: String): Double? {
        val history = tracks[interno]?.speedHistory?.takeIf { it.isNotEmpty() }
            ?: return null
        val sorted = history.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    fun routeSOf(interno: String): Double? =
        if (projector != null) tracks[interno]?.fixS else null

    fun projectorOrNull(): RouteProjector? = projector

    fun clear() {
        tracks.clear()
    }

    companion object {
        const val ANIM_MS = 1_200L
        const val MAX_SPEED_MPS = 40_000.0 / 3_600.0
        const val MIN_MOVE_METERS = 10.0
        const val MAX_RECKON_MS = 60_000L
        const val SPEED_HISTORY_SIZE = 5
    }
}
