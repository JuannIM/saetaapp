package com.saetasaldo.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.BusLine
import com.saetasaldo.app.domain.model.BusPosition
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.LineRoute
import com.saetasaldo.app.domain.model.MapConfig
import com.saetasaldo.app.domain.model.RouteNode
import com.saetasaldo.app.domain.repository.BusMapRepository
import com.saetasaldo.app.domain.usecase.EstimateArrivalsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BusMarkerState(
    val position: BusPosition,
    val displayedLat: Double,
    val displayedLng: Double,
    val lastChangeAtMs: Long,
    val routeS: Double? = null
) {
    fun ageSeconds(nowMs: Long): Long = ((nowMs - lastChangeAtMs) / 1000L).coerceAtLeast(0L)
}

data class BusMapUiState(
    val lineTree: LineGroup? = null,
    val selectionPath: List<String> = emptyList(),
    val selectedLine: BusLine? = null,
    val route: LineRoute? = null,
    val buses: List<BusMarkerState> = emptyList(),
    val news: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val lastPollAt: Long? = null,
    val config: MapConfig = MapConfig.DEFAULT
)

fun LineGroup.resolvePath(path: List<String>): LineGroup? {
    var node = this
    for (cod in path) {
        node = node.subGroups.firstOrNull { it.codGrupo == cod } ?: return null
    }
    return node
}

class BusMapViewModel(
    private val repository: BusMapRepository,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val tickIntervalMs: Long = TICK_INTERVAL_MS
) : ViewModel() {

    // All StateFlows are declared before init: Dispatchers.Main.immediate can
    // run a launched block inline, so nothing here may reference a field that
    // is initialized later.
    private val _uiState = MutableStateFlow(BusMapUiState())
    val uiState: StateFlow<BusMapUiState> = _uiState.asStateFlow()

    private val _displayedBuses = MutableStateFlow<List<BusMarkerState>>(emptyList())
    val displayedBuses: StateFlow<List<BusMarkerState>> = _displayedBuses.asStateFlow()

    private var pollJob: Job? = null
    private var tickerJob: Job? = null
    private val markers = LinkedHashMap<String, BusMarkerState>()
    private var interpolator = BusInterpolator(null, clockMs)
    private var lastPositions: List<BusPosition> = emptyList()

    init {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val tree = repository.lineTree()
            val config = repository.config()
            _uiState.value = _uiState.value.copy(
                lineTree = tree.getOrNull(),
                config = config.getOrNull() ?: MapConfig.DEFAULT,
                loading = false,
                error = if (tree.isFailure) {
                    tree.exceptionOrNull()?.message ?: "No se pudieron cargar las líneas"
                } else {
                    null
                }
            )
        }
    }

    fun selectGroup(level: Int, codGrupo: String) {
        val state = _uiState.value
        val tree = state.lineTree ?: return
        val path = state.selectionPath.take(level) + codGrupo
        if (tree.resolvePath(path) == null) return
        clearLineState()
        _uiState.value = _uiState.value.copy(selectionPath = path)
    }

    fun selectLine(codLinea: String) {
        val state = _uiState.value
        val tree = state.lineTree ?: return
        val line = tree.resolvePath(state.selectionPath)
            ?.lineas?.firstOrNull { it.codLinea == codLinea } ?: return
        if (state.selectedLine == line) return
        clearLineState()
        _uiState.value = _uiState.value.copy(selectedLine = line, loading = true)
        loadRouteAndNews(codLinea)
        startPolling()
        startTicker()
    }

    fun clearSelection() {
        clearLineState()
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Route-distance position of a displayed bus, for per-stop ETA. */
    fun routeSOf(interno: String): Double? = interpolator.routeSOf(interno)

    private val estimateArrivals = EstimateArrivalsUseCase()

    /**
     * Device-computed arrivals for a tapped stop ("cuándo viene"): the server
     * ETA is disabled, so each bus's projected route distance and rolling
     * median speed drive the estimate. Recomputed per call.
     */
    fun arrivalsForStop(stop: RouteNode): List<EstimateArrivalsUseCase.StopArrival> {
        val projector = interpolator.projectorOrNull()
        val stops = _uiState.value.route?.stops.orEmpty()
        val stopS = projector?.project(stop.latitud, stop.longitud)?.sMeters
        val inputs = _displayedBuses.value.map { marker ->
            val proxS = marker.position.proximaParada?.let { desc ->
                stops.filter { it.descripcionParada.equals(desc.trim(), ignoreCase = true) }
                    .mapNotNull { projector?.project(it.latitud, it.longitud)?.sMeters }
            }.orEmpty()
            EstimateArrivalsUseCase.BusInput(
                interno = marker.position.interno,
                busS = interpolator.routeSOf(marker.position.interno) ?: marker.routeS,
                medianSpeedMps = interpolator.medianSpeedMpsOf(marker.position.interno),
                busLat = marker.displayedLat,
                busLng = marker.displayedLng,
                proximaParadaS = proxS
            )
        }
        return estimateArrivals(stopS, stop.latitud, stop.longitud, inputs)
    }

    override fun onCleared() {
        stopPolling()
        markers.clear()
        interpolator.clear()
        super.onCleared()
    }

    private fun clearLineState() {
        stopPolling()
        markers.clear()
        interpolator.clear()
        lastPositions = emptyList()
        _displayedBuses.value = emptyList()
        _uiState.value = _uiState.value.copy(
            selectedLine = null,
            route = null,
            buses = emptyList(),
            news = emptyList(),
            loading = false,
            error = null,
            lastPollAt = null
        )
    }

    private fun loadRouteAndNews(codLinea: String) {
        viewModelScope.launch {
            val route = repository.route(codLinea)
            val news = repository.lineNews(codLinea)
            if (_uiState.value.selectedLine?.codLinea != codLinea) return@launch
            val loaded = route.getOrNull()
            interpolator.setRoute(
                loaded?.takeIf { it.nodes.size >= 2 }?.let { RouteProjector(it) },
                lastPositions
            )
            _uiState.value = _uiState.value.copy(
                route = loaded,
                news = news.getOrNull().orEmpty(),
                loading = false,
                error = if (route.isFailure) {
                    route.exceptionOrNull()?.message ?: "No se pudo cargar el recorrido"
                } else {
                    _uiState.value.error
                }
            )
            refreshDisplayed(clockMs())
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var delayMs = pollIntervalMs
            while (isActive) {
                val codLinea = _uiState.value.selectedLine?.codLinea ?: break
                val result = repository.positions(codLinea)
                if (_uiState.value.selectedLine?.codLinea != codLinea) break
                result.onSuccess { positions ->
                    delayMs = pollIntervalMs
                    applyPositions(positions)
                    _uiState.value = _uiState.value.copy(lastPollAt = clockMs(), error = null)
                }.onFailure { e ->
                    delayMs = (delayMs * 2).coerceAtMost(MAX_POLL_INTERVAL_MS)
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "No se pudieron cargar las posiciones"
                    )
                }
                delay(delayMs)
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                refreshDisplayed(clockMs())
            }
        }
    }

    private fun applyPositions(positions: List<BusPosition>) {
        val now = clockMs()
        lastPositions = positions
        interpolator.onFixes(positions, now)
        positions.forEach { pos ->
            val prev = markers[pos.interno]
            val moved = prev == null ||
                prev.position.latitud != pos.latitud ||
                prev.position.longitud != pos.longitud ||
                prev.position.orientacion != pos.orientacion
            markers[pos.interno] = BusMarkerState(
                position = pos,
                displayedLat = prev?.displayedLat ?: pos.latitud,
                displayedLng = prev?.displayedLng ?: pos.longitud,
                lastChangeAtMs = if (moved) now else prev.lastChangeAtMs,
                routeS = prev?.routeS
            )
        }
        _uiState.value = _uiState.value.copy(buses = markers.values.toList())
        refreshDisplayed(now)
    }

    private fun refreshDisplayed(now: Long) {
        val displayed = interpolator.tick(now)
        _displayedBuses.value = markers.values.map { marker ->
            val d = displayed[marker.position.interno]
            if (d == null) {
                marker
            } else {
                marker.copy(
                    displayedLat = d.lat,
                    displayedLng = d.lng,
                    routeS = d.routeS
                )
            }
        }
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val MAX_POLL_INTERVAL_MS = 60_000L
        const val TICK_INTERVAL_MS = 1_000L
    }
}
