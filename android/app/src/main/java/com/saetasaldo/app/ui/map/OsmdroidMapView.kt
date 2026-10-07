package com.saetasaldo.app.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saetasaldo.app.BuildConfig
import com.saetasaldo.app.R
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.model.RouteNode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.util.Counters
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val STALE_BUS_SECONDS = 120L
private const val DEFAULT_ZOOM = 13.0

/**
 * Tile source driven by a URL template ({z}/{x}/{y}); after any tile download
 * error it permanently falls back to the secondary server, like the official
 * web map does.
 */
private class RedBusTileSource(
    private val primaryTemplate: String,
    private val fallbackTemplate: String
) : OnlineTileSourceBase(
    "SAETAMapa", 3, 20, 256, ".png",
    arrayOf(primaryTemplate), "© OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val template = if (Counters.tileDownloadErrors > 0) {
            fallbackTemplate
        } else {
            primaryTemplate
        }
        return template
            .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
            .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
    }
}

/** Owns the route/bus overlays so recomposition only diffs markers. */
class OsmMapController internal constructor(
    internal val mapView: MapView,
    private val busIconFactory: () -> Drawable?,
    private val stopIcon: Drawable?
) {
    private var routeLine: Polyline? = null
    private val stopMarkers = mutableListOf<Marker>()
    private val busMarkers = linkedMapOf<String, Marker>()
    private val busIcons = mutableMapOf<String, Drawable>()
    private var routeFitted = false

    fun renderRoute(route: LineRoute?, onStopClick: (RouteNode) -> Unit) {
        routeLine?.let { mapView.overlayManager.remove(it) }
        routeLine = null
        stopMarkers.forEach { mapView.overlayManager.remove(it) }
        stopMarkers.clear()
        if (route == null || route.nodes.isEmpty()) {
            routeFitted = false
            mapView.invalidate()
            return
        }
        val points = route.nodes.map { GeoPoint(it.latitud, it.longitud) }
        val density = mapView.resources.displayMetrics.density
        val line = Polyline(mapView).apply {
            outlinePaint.color = Color.RED
            outlinePaint.alpha = 180
            outlinePaint.strokeWidth = density * 6f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            setPoints(points)
        }
        mapView.overlayManager.add(0, line)
        routeLine = line
        route.stops.forEach { stop ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.latitud, stop.longitud)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = stopIcon
                title = stop.descripcionParada.ifEmpty { "Parada" }
                setOnMarkerClickListener { _, _ ->
                    onStopClick(stop)
                    true
                }
            }
            mapView.overlayManager.add(marker)
            stopMarkers.add(marker)
        }
        if (!routeFitted) {
            routeFitted = true
            mapView.post {
                runCatching {
                    val box = BoundingBox(
                        points.maxOf { it.latitude },
                        points.maxOf { it.longitude },
                        points.minOf { it.latitude },
                        points.minOf { it.longitude }
                    )
                    mapView.zoomToBoundingBox(box, false, (density * 48).toInt())
                }
            }
        }
        mapView.invalidate()
    }

    fun renderBuses(buses: List<BusMarkerState>, nowMs: Long) {
        val seen = buses.mapTo(HashSet()) { it.position.interno }
        val it = busMarkers.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.key !in seen) {
                mapView.overlayManager.remove(entry.value)
                busIcons.remove(entry.key)
                it.remove()
            }
        }
        buses.forEach { state ->
            val p = state.position
            val marker = busMarkers.getOrPut(p.interno) {
                Marker(mapView).also { m ->
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mapView.overlayManager.add(m)
                }
            }
            val icon = busIcons.getOrPut(p.interno) {
                busIconFactory()?.constantState?.newDrawable()?.mutate()
                    ?: return@forEach
            }
            val stale = state.ageSeconds(nowMs) > STALE_BUS_SECONDS
            icon.alpha = if (stale) 90 else 255
            marker.icon = icon
            marker.position = GeoPoint(state.displayedLat, state.displayedLng)
            marker.rotation = p.orientacion.toFloat()
            marker.title = "Interno ${p.interno}"
            marker.snippet = buildList {
                p.proximaParada?.let { add("Próxima: $it") }
                if (p.vehiculoRampa) add("♿ Unidad accesible")
            }.joinToString("\n")
        }
        mapView.invalidate()
    }

    fun centerOn(latitud: Double, longitud: Double, zoom: Double? = null) {
        mapView.controller.setCenter(GeoPoint(latitud, longitud))
        zoom?.let { mapView.controller.setZoom(it) }
        mapView.invalidate()
    }
}

@Composable
fun OsmdroidMapView(
    config: MapConfig,
    route: LineRoute?,
    buses: List<BusMarkerState>,
    nowMs: Long,
    onStopClick: (RouteNode) -> Unit,
    onMapReady: (OsmMapController) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val osm = remember {
        Configuration.getInstance()
            .load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        OsmMapController(
            mapView = MapView(context).apply {
                setMultiTouchControls(true)
                setTilesScaledToDpi(true)
                controller.setZoom(DEFAULT_ZOOM)
                controller.setCenter(GeoPoint(config.latitud, config.longitud))
            },
            busIconFactory = { ContextCompat.getDrawable(context, R.drawable.ic_bus_marker) },
            stopIcon = ContextCompat.getDrawable(context, R.drawable.ic_stop_marker)
        )
    }

    DisposableEffect(config) {
        osm.mapView.setTileSource(
            RedBusTileSource(
                primaryTemplate = config.urlPrincipalMapas ?: MapConfig.REDBUS_TILES_URL,
                fallbackTemplate = config.urlSecundariaMapas ?: MapConfig.OSM_TILES_URL
            )
        )
        onDispose { }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, osm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> osm.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> osm.mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            osm.mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            osm.mapView.onPause()
            osm.mapView.onDetach()
        }
    }

    AndroidView(
        factory = { osm.mapView },
        update = { mv ->
            osm.renderRoute(route, onStopClick)
            osm.renderBuses(buses, nowMs)
            onMapReady(osm)
        },
        modifier = modifier
    )
}
