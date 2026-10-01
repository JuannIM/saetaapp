package com.saetasaldo.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.TripEstimate
import com.saetasaldo.app.ui.cards.components.SaetaCardItem
import com.saetasaldo.app.ui.dialogs.FallbackCaptchaDialog
import com.saetasaldo.app.ui.theme.SaetaBluePrimary
import com.saetasaldo.app.ui.theme.SaetaGold
import com.saetasaldo.app.ui.theme.SaetaGreenPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card by viewModel.card.collectAsState()
    val history by viewModel.history.collectAsState()
    val fare by viewModel.fare.collectAsState()
    val tripEstimate by viewModel.tripEstimate.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val showCaptchaDialog by viewModel.showFallbackCaptchaDialog.collectAsState()
    val captchaBitmap by viewModel.captchaBitmap.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (msg.contains("captcha", ignoreCase = true) || msg.contains("reintentos", ignoreCase = true)) "Resolver" else null,
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.triggerManualCaptcha()
            }
            viewModel.clearError()
        }
    }

    LaunchedEffect(card?.id) {
        if (card != null && card?.lastUpdated == 0L) {
            viewModel.refreshBalance()
        }
    }

    if (showCaptchaDialog) {
        FallbackCaptchaDialog(
            captchaBitmap = captchaBitmap,
            onConfirm = { code -> viewModel.submitManualCaptcha(code) },
            onDismiss = { viewModel.dismissCaptchaDialog() },
            onRefreshCaptcha = { viewModel.loadCaptchaBitmap() }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = card?.name ?: "Detalle de Tarjeta",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
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
                    if (card != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar nombre")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar tarjeta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        val currentCard = card
        if (currentCard == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Physical-Digital Card Item Header
                item {
                    SaetaCardItem(
                        card = currentCard,
                        onClick = { /* Already in detail */ }
                    )
                }

                // 2. Quick Actions: Set as Widget Card + Refresh
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.setAsFavorite() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (currentCard.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (currentCard.isFavorite) SaetaGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentCard.isFavorite) "En Widget" else "Fijar en Widget",
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.refreshBalance() },
                            enabled = !isRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = SaetaBluePrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Actualizar", fontSize = 13.sp)
                        }
                    }
                }

                // 3. Trip Estimator Card
                item {
                    TripEstimatorSection(
                        estimate = tripEstimate,
                        fare = fare,
                        cardType = currentCard.type,
                        onEditFareClick = { showFareDialog = true }
                    )
                }

                // 4. Balance History Header
                item {
                    Text(
                        text = "Historial de Variaciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 5. Balance History Items or Empty State
                if (history.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Aún no hay variaciones de saldo registradas para esta tarjeta.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            )
                        }
                    }
                } else {
                    items(history, key = { it.id }) { record ->
                        BalanceHistoryItem(record = record)
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && card != null) {
        var newName by remember { mutableStateOf(card?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Editar nombre") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre de la tarjeta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCardName(newName)
                        showRenameDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && card != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar tarjeta") },
            text = { Text("¿Estás seguro de que deseás eliminar esta tarjeta y su historial? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCard(onDeleted = onBackClick)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Edit Fare Dialog
    if (showFareDialog) {
        var fareInput by remember { mutableStateOf(fare.toString()) }
        AlertDialog(
            onDismissRequest = { showFareDialog = false },
            title = { Text("Ajustar Tarifa") },
            text = {
                Column {
                    Text(
                        text = "Ingresá el costo vigente del pasaje de SAETA para estimar los viajes disponibles:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = fareInput,
                        onValueChange = { fareInput = it },
                        label = { Text("Tarifa ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = fareInput.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            viewModel.updateFare(parsed)
                        }
                        showFareDialog = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFareDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun TripEstimatorSection(
    estimate: TripEstimate?,
    fare: Double,
    cardType: CardType,
    onEditFareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Viajes Disponibles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onEditFareClick) {
                    Text(
                        text = String.format(Locale.getDefault(), "Tarifa: $ %.2f", fare),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (estimate != null) {
                Text(
                    text = "${estimate.totalPossibleTrips} viajes",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "${estimate.regularTrips} comunes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cardType == CardType.AZUL_COMUN && estimate.emergencyTrips > 0) {
                        Text(
                            text = "+ ${estimate.emergencyTrips} emergencia",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = SaetaGreenPrimary
                        )
                    }
                }

                if (estimate.remainingSubBalance > 0.0) {
                    Text(
                        text = String.format(Locale.getDefault(), "Sobrante: $ %.2f", estimate.remainingSubBalance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (estimate.isInEmergencyNegative) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saldo negativo. En uso de viaje(s) de emergencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "Consultando saldo...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BalanceHistoryItem(
    record: BalanceRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.timestamp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format(Locale.getDefault(), "Saldo: $ %.2f", record.balance),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Delta Badge
            val (deltaText, deltaColor) = when {
                record.difference > 0 -> {
                    String.format(Locale.getDefault(), "+ $ %.2f", record.difference) to SaetaGreenPrimary
                }
                record.difference < 0 -> {
                    String.format(Locale.getDefault(), "- $ %.2f", -record.difference) to MaterialTheme.colorScheme.error
                }
                else -> {
                    "$ 0.00" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        color = deltaColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = deltaText,
                    color = deltaColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
