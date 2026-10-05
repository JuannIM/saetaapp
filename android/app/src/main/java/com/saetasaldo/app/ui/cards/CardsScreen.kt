package com.saetasaldo.app.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.ui.account.RedBusAccountDialog
import com.saetasaldo.app.ui.account.RedBusAccountUiState
import com.saetasaldo.app.ui.cards.components.SaetaCardItem
import com.saetasaldo.app.ui.dialogs.PrivacyPolicyDialog
import com.saetasaldo.app.ui.theme.SaetaBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    viewModel: CardsViewModel,
    onCardClick: (SaetaCard) -> Unit,
    onAddCardClick: () -> Unit,
    onScanNfcClick: () -> Unit,
    isNfcSupported: Boolean = true,
    redBusAccountState: RedBusAccountUiState? = null,
    onConnectRedBus: () -> Unit = {},
    onSyncRedBus: () -> Unit = {},
    onDisconnectRedBus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cards by viewModel.cards.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis Tarjetas SAETA",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (redBusAccountState != null) {
                        val isConnected =
                            redBusAccountState.sessionState == RedBusSessionState.Connected
                        IconButton(onClick = { showAccountDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = if (isConnected) {
                                    "Cuenta RedBus conectada"
                                } else {
                                    "Conectar cuenta RedBus"
                                },
                                tint = if (isConnected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showPrivacyDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Privacidad y Seguridad"
                        )
                    }
                    IconButton(onClick = onAddCardClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar tarjeta manualmente"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshAllBalances() },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar todas las tarjetas"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isNfcSupported) {
                    FloatingActionButton(
                        onClick = onScanNfcClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = "Escanear NFC",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = onAddCardClick,
                    containerColor = SaetaBluePrimary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nueva Tarjeta") }
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAllBalances() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No tenés tarjetas guardadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ingresá el número de tu tarjeta SAETA para consultar saldo o acercala al teléfono con NFC.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onAddCardClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SaetaBluePrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Agregar Tarjeta (Manual)")
                        }
                        if (isNfcSupported) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onScanNfcClick
                            ) {
                                Icon(Icons.Default.Nfc, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Escanear con NFC")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        SaetaCardItem(
                            card = card,
                            onClick = { onCardClick(card) }
                        )
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showAccountDialog && redBusAccountState != null) {
        RedBusAccountDialog(
            state = redBusAccountState,
            onConnect = {
                showAccountDialog = false
                onConnectRedBus()
            },
            onSync = onSyncRedBus,
            onDisconnect = onDisconnectRedBus,
            onDismiss = { showAccountDialog = false }
        )
    }
}
