package com.saetasaldo.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saetasaldo.app.domain.model.LineGroup
import com.saetasaldo.app.domain.model.RouteNode
import com.saetasaldo.app.domain.usecase.EstimateArrivalsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val GROUP_LEVEL_LABELS = listOf("Grupo", "Corredor", "Subgrupo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusMapScreen(
    viewModel: BusMapViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val displayedBuses by viewModel.displayedBuses.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var osmController by remember { mutableStateOf<OsmMapController?>(null) }
    var selectedStop by remember { mutableStateOf<RouteNode?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Approximate (coarse) location is requested only when the user taps the
    // FAB; it is used once to center the map and never leaves the device.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locateOnce(context) { lat, lng ->
                osmController?.centerOn(lat, lng, LOCATION_ZOOM)
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Sin permiso de ubicación") }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Mapa de buses", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    val youngest = state.buses.maxOfOrNull { it.lastChangeAtMs }
                    if (state.selectedLine != null && youngest != null) {
                        val ageSec = ((nowMs - youngest) / 1_000L).coerceAtLeast(0L)
                        Text(
                            text = "Actualizado hace ${ageSec}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        locateOnce(context) { lat, lng ->
                            osmController?.centerOn(lat, lng, LOCATION_ZOOM)
                        }
                    } else {
                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Mi ubicación"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SelectorRow(
                state = state,
                onGroupSelect = viewModel::selectGroup,
                onLineSelect = viewModel::selectLine
            )

            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (state.news.isNotEmpty()) {
                Surface(
                    color = Color(0xFFFFF3CD),
                    contentColor = Color(0xFF5D4E00)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        state.news.forEach { news ->
                            Text(
                                text = news,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OsmdroidMapView(
                    config = state.config,
                    route = state.route,
                    buses = displayedBuses,
                    nowMs = nowMs,
                    onStopClick = { selectedStop = it },
                    onMapReady = { osmController = it },
                    modifier = Modifier.fillMaxSize()
                )

                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }

                val line = state.selectedLine
                if (line != null && !state.loading && state.buses.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Sin buses en circulación en ${line.descripcion}",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    selectedStop?.let { stop ->
        ModalBottomSheet(onDismissRequest = { selectedStop = null }) {
            // displayedBuses is a key so the estimates recompute each tick.
            val arrivals = remember(displayedBuses, stop) {
                viewModel.arrivalsForStop(stop)
            }
            StopArrivalsSheet(stop, arrivals)
        }
    }
}

private const val LOCATION_ZOOM = 16.0

/**
 * One-shot approximate location: best last-known fix plus a single update.
 * No continuous tracking; the value is used only to center the map.
 */
@SuppressLint("MissingPermission") // callers check ACCESS_COARSE_LOCATION first
private fun locateOnce(context: Context, onLocated: (Double, Double) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return
    val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
    providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { onLocated(it.latitude, it.longitude) }
    // "fused" == LocationManager.FUSED_PROVIDER (API 31+ literal; inlined string
    // so it is safe on minSdk 26 — it simply won't match on old devices).
    val provider = providers.firstOrNull { it == "fused" }
        ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
        ?: providers.firstOrNull()
        ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.getCurrentLocation(
            provider,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context)
        ) { onLocated(it.latitude, it.longitude) }
    } else {
        @Suppress("DEPRECATION")
        manager.requestSingleUpdate(
            provider,
            { onLocated(it.latitude, it.longitude) },
            Looper.getMainLooper()
        )
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${meters.roundToInt()} m"
    }

@Composable
private fun StopArrivalsSheet(
    stop: RouteNode,
    arrivals: List<EstimateArrivalsUseCase.StopArrival>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Llegadas estimadas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stop.descripcionParada.ifEmpty { "Parada" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        if (arrivals.isEmpty()) {
            Text(
                text = "Sin buses acercándose a esta parada",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            arrivals.forEach { arrival ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Interno ${arrival.interno}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when {
                            arrival.minutes != null -> "~${arrival.minutes} min"
                            arrival.stopped -> "sin estimar"
                            else -> "a pie ${formatDistance(arrival.straightLineMeters)}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = "Estimación calculada en el dispositivo, puede variar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
    }
}

/** One dropdown per tree level that still has subgroups, plus a line dropdown. */
@Composable
private fun SelectorRow(
    state: BusMapUiState,
    onGroupSelect: (Int, String) -> Unit,
    onLineSelect: (String) -> Unit
) {
    val tree = state.lineTree ?: return
    val levels = mutableListOf<Pair<Int, LineGroup>>()
    var node: LineGroup? = tree
    var level = 0
    while (node != null && node.subGroups.isNotEmpty()) {
        levels.add(level to node)
        val selected = state.selectionPath.getOrNull(level)
        node = node.subGroups.firstOrNull { it.codGrupo == selected }
        level++
    }
    val lineOptions = node?.lineas.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        levels.forEach { (lvl, group) ->
            MapDropdown(
                label = GROUP_LEVEL_LABELS.getOrElse(lvl) { "Subgrupo" },
                options = group.subGroups.map { it.codGrupo },
                selected = state.selectionPath.getOrNull(lvl),
                onSelect = { onGroupSelect(lvl, it) },
                modifier = Modifier.weight(1f)
            )
        }
        if (lineOptions.isNotEmpty()) {
            MapDropdown(
                label = "Línea",
                options = lineOptions.map { it.codLinea },
                optionText = { cod -> lineOptions.first { it.codLinea == cod }.descripcion.ifEmpty { cod } },
                selected = state.selectedLine?.codLinea,
                onSelect = onLineSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    optionText: (String) -> String = { it }
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let(optionText) ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(label) },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
