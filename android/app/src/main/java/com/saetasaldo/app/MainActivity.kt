package com.saetasaldo.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.data.nfc.AndroidNfcManager
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.repository.CardRepositoryImpl
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import com.saetasaldo.app.domain.usecase.NfcScanResult
import com.saetasaldo.app.domain.usecase.ProcessNfcScanUseCase
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import com.saetasaldo.app.ui.cards.CardsScreen
import com.saetasaldo.app.ui.cards.CardsViewModel
import com.saetasaldo.app.ui.detail.CardDetailScreen
import com.saetasaldo.app.ui.detail.CardDetailViewModel
import com.saetasaldo.app.ui.nfc.NfcScanBottomSheet
import com.saetasaldo.app.ui.theme.SaetaSaldoTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object CardsList : Screen
    data class CardDetail(val cardId: String) : Screen
}

data class NewCardPromptState(
    val nfcUid: String? = null,
    val initialCardNumber: String = ""
)

class MainActivity : ComponentActivity() {

    private lateinit var nfcManager: AndroidNfcManager
    private lateinit var repository: CardRepository
    private lateinit var processNfcScanUseCase: ProcessNfcScanUseCase
    private lateinit var getCardBalanceUseCase: GetCardBalanceUseCase
    private lateinit var cardsViewModel: CardsViewModel

    private val scannedTagFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcManager = AndroidNfcManager(this)

        val db = SaetaDatabase.getInstance(this)
        val solver = MlKitCaptchaSolver()
        val solveCaptchaUseCase = SolveCaptchaUseCase(NetworkClient.apiService, solver)
        repository = CardRepositoryImpl(db.cardDao(), db.balanceHistoryDao(), NetworkClient.apiService, solveCaptchaUseCase)

        processNfcScanUseCase = ProcessNfcScanUseCase(repository)
        getCardBalanceUseCase = GetCardBalanceUseCase(repository)
        cardsViewModel = CardsViewModel(repository, getCardBalanceUseCase)

        setContent {
            SaetaSaldoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaetaAppContent(
                        cardsViewModel = cardsViewModel,
                        repository = repository,
                        processNfcScanUseCase = processNfcScanUseCase,
                        getCardBalanceUseCase = getCardBalanceUseCase,
                        isNfcSupported = nfcManager.isNfcSupported,
                        isNfcEnabled = nfcManager.isNfcEnabled,
                        scannedTagFlow = scannedTagFlow,
                        onScanNfc = { nfcManager.triggerHapticFeedback() }
                    )
                }
            }
        }

        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        nfcManager.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            } ?: return

            val uid = AndroidNfcManager.extractUidFromTag(tag)
            if (uid.isNotBlank()) {
                nfcManager.triggerHapticFeedback()
                scannedTagFlow.tryEmit(uid)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaetaAppContent(
    cardsViewModel: CardsViewModel,
    repository: CardRepository,
    processNfcScanUseCase: ProcessNfcScanUseCase,
    getCardBalanceUseCase: GetCardBalanceUseCase,
    isNfcSupported: Boolean,
    isNfcEnabled: Boolean,
    scannedTagFlow: MutableSharedFlow<String>,
    onScanNfc: () -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.CardsList) }
    var showNfcBottomSheet by remember { mutableStateOf(false) }
    var newCardPrompt by remember { mutableStateOf<NewCardPromptState?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    // Handle NFC tag scanned events
    LaunchedEffect(Unit) {
        scannedTagFlow.collect { uid ->
            scannedTagFlow.resetReplayCache()
            showNfcBottomSheet = false
            when (val result = processNfcScanUseCase(uid)) {
                is com.saetasaldo.app.domain.usecase.NfcScanResult.ExistingCardFound -> {
                    getCardBalanceUseCase(result.card.cardNumber)
                    currentScreen = Screen.CardDetail(result.card.id)
                }
                is com.saetasaldo.app.domain.usecase.NfcScanResult.NewCardDiscovered -> {
                    newCardPrompt = NewCardPromptState(nfcUid = result.nfcUid)
                }
            }
        }
    }

    when (val screen = currentScreen) {
        is Screen.CardsList -> {
            CardsScreen(
                viewModel = cardsViewModel,
                onCardClick = { card ->
                    currentScreen = Screen.CardDetail(card.id)
                },
                onScanNfcClick = {
                    showNfcBottomSheet = true
                }
            )
        }

        is Screen.CardDetail -> {
            BackHandler {
                currentScreen = Screen.CardsList
            }
            val detailViewModel = remember(screen.cardId) {
                CardDetailViewModel(
                    cardId = screen.cardId,
                    repository = repository,
                    getCardBalanceUseCase = getCardBalanceUseCase
                )
            }
            CardDetailScreen(
                viewModel = detailViewModel,
                onBackClick = {
                    currentScreen = Screen.CardsList
                }
            )
        }
    }

    // NFC Scan Bottom Sheet
    if (showNfcBottomSheet) {
        NfcScanBottomSheet(
            onDismissRequest = { showNfcBottomSheet = false },
            isNfcSupported = isNfcSupported,
            isNfcEnabled = isNfcEnabled,
            sheetState = sheetState
        )
    }

    // New Card Prompt Dialog (after NFC detection of unknown card)
    newCardPrompt?.let { promptState ->
        NewCardRegistrationDialog(
            nfcUid = promptState.nfcUid,
            onDismiss = { newCardPrompt = null },
            onConfirm = { name, cardNumber, cardType ->
                cardsViewModel.addNewCard(
                    name = name,
                    cardNumber = cardNumber,
                    nfcUid = promptState.nfcUid,
                    type = cardType
                ) { result ->
                    val saved = result.getOrNull()
                    if (saved != null) {
                        currentScreen = Screen.CardDetail(saved.id)
                    }
                }
                newCardPrompt = null
            }
        )
    }
}

@Composable
fun NewCardRegistrationDialog(
    nfcUid: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, cardNumber: String, type: CardType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CardType.AZUL_COMUN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (nfcUid != null) "Tarjeta NFC Detectada" else "Registrar Tarjeta",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (nfcUid != null) {
                    Text(
                        text = "UID NFC: $nfcUid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej: Mi Tarjeta)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Número de Tarjeta (impreso al frente)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tipo de Tarjeta:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == CardType.AZUL_COMUN,
                        onClick = { selectedType = CardType.AZUL_COMUN }
                    )
                    Text("Público (Azul)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = selectedType == CardType.VERDE_BENEFICIARIO,
                        onClick = { selectedType = CardType.VERDE_BENEFICIARIO }
                    )
                    Text("Beneficiario (Verde)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, cardNumber, selectedType) },
                enabled = cardNumber.isNotBlank()
            ) {
                Text("Guardar y Consultar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
