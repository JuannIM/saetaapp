package com.saetasaldo.app.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
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
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private const val STALE_BUS_SECONDS = 120L
private const val DEFAULT_ZOOM = 13.0
private const val MAX_ROUTE_POINTS = 2_000

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
    private var myLocationOverlay: MyLocationNewOverlay? = null

    /**
     * Shows the blue dot that tracks the user's position while this screen is
     * open. Caller must hold a location permission before calling.
     */
    fun enableMyLocation(context: Context) {
        if (myLocationOverlay != null) return
        val overlay = MyLocationNewOverlay(CoarseLocationProvider(context), mapView)
        overlay.enableMyLocation()
        mapView.overlayManager.add(overlay)
        myLocationOverlay = overlay
        mapView.invalidate()
    }

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
        val points = decimate(route.nodes.map { GeoPoint(it.latitud, it.longitud) })
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
                        route.nodes.maxOf { it.latitud },
                        route.nodes.maxOf { it.longitud },
                        route.nodes.minOf { it.latitud },
                        route.nodes.minOf { it.longitud }
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

    fun pauseMyLocation() {
        myLocationOverlay?.disableMyLocation()
    }

    fun resumeMyLocation() {
        myLocationOverlay?.enableMyLocation()
    }

    /** Stops the location listener — called when the map is disposed. */
    fun disableMyLocation() {
        myLocationOverlay?.let {
            it.disableMyLocation()
            mapView.overlayManager.remove(it)
        }
        myLocationOverlay = null
    }
}

/**
 * Feeds the my-location dot with network + passive fixes only. The default
 * GpsMyLocationProvider needs GPS_PROVIDER, which requires the precise
 * (FINE) permission the app deliberately never requests — coarse is enough
 * for orientation on a city map.
 */
private class CoarseLocationProvider(context: Context) : IMyLocationProvider, LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var consumer: IMyLocationConsumer? = null

    @SuppressLint("MissingPermission") // caller holds ACCESS_COARSE_LOCATION
    override fun startLocationProvider(myLocationConsumer: IMyLocationConsumer): Boolean {
        consumer = myLocationConsumer
        var started = false
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            if (manager?.isProviderEnabled(provider) == true) {
                runCatching {
                    manager.requestLocationUpdates(provider, 5_000L, 5f, this, Looper.getMainLooper())
                }.onSuccess { started = true }
            }
        }
        getLastKnownLocation()?.let { myLocationConsumer.onLocationChanged(it, this) }
        return started
    }

    override fun stopLocationProvider() {
        consumer = null
        runCatching { manager?.removeUpdates(this) }
    }

    @SuppressLint("MissingPermission") // caller holds ACCESS_COARSE_LOCATION
    override fun getLastKnownLocation(): Location? =
        runCatching { manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()

    override fun onLocationChanged(location: Location) {
        consumer?.onLocationChanged(location, this)
    }

    override fun destroy() = stopLocationProvider()
}

/**
 * Even stride decimation to cap drawn polyline points; keeps endpoints so the
 * rendered shape is preserved while bounding long metropolitan routes.
 */
private fun decimate(points: List<GeoPoint>, max: Int = MAX_ROUTE_POINTS): List<GeoPoint> {
    if (points.size <= max) return points
    val stride = points.size.toDouble() / (max - 1)
    return (0 until max).map { points[(it * stride).toInt().coerceAtMost(points.size - 1)] }
        .distinct()
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
                Lifecycle.Event.ON_RESUME -> {
                    osm.mapView.onResume()
                    osm.resumeMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    osm.pauseMyLocation()
                    osm.mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            osm.mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            osm.disableMyLocation()
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
