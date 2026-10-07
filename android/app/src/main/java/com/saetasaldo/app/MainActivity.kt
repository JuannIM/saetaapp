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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.data.nfc.AndroidNfcManager
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.remote.RedBusAccountNetworkClient
import com.saetasaldo.app.data.remote.cookie.AndroidWebCookieStore
import com.saetasaldo.app.data.remote.cookie.WebViewCookieJar
import com.saetasaldo.app.data.repository.CardRepositoryImpl
import com.saetasaldo.app.data.repository.RedBusAccountRepositoryImpl
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import com.saetasaldo.app.domain.repository.TurnstileTokenProvider
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import com.saetasaldo.app.domain.usecase.NfcScanResult
import com.saetasaldo.app.domain.usecase.ProcessNfcScanUseCase
import com.saetasaldo.app.domain.usecase.RefreshAllBalancesUseCase
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import com.saetasaldo.app.domain.usecase.SyncRedBusCardsUseCase
import com.saetasaldo.app.ui.account.RedBusAccountViewModel
import com.saetasaldo.app.ui.account.RedBusLoginScreen
import com.saetasaldo.app.ui.cards.CardsScreen
import com.saetasaldo.app.ui.cards.CardsViewModel
import com.saetasaldo.app.ui.detail.CardDetailScreen
import com.saetasaldo.app.ui.detail.CardDetailViewModel
import com.saetasaldo.app.ui.nfc.NfcScanBottomSheet
import com.saetasaldo.app.ui.theme.SaetaSaldoTheme
import com.saetasaldo.app.ui.turnstile.WebViewTurnstileTokenProvider
import com.saetasaldo.app.wear.WearSyncManager
import com.saetasaldo.app.widget.SaetaBalanceWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface Screen {
    data object CardsList : Screen
    data class CardDetail(val cardId: String, val refreshOnOpen: Boolean = false) : Screen
    data object RedBusLogin : Screen
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
    private lateinit var redBusAccountViewModel: RedBusAccountViewModel

    private val scannedTagFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    private var captchaSolver: MlKitCaptchaSolver? = null

    // Offscreen WebView that resolves the anonymous Turnstile captcha. Only the
    // app-facing repository gets it: the widget builds its own provider-less
    // repository so it never touches a WebView in a cold process.
    private val turnstileProvider: TurnstileTokenProvider by lazy {
        WebViewTurnstileTokenProvider(applicationContext, NetworkClient.apiService)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcManager = AndroidNfcManager(this)

        val db = SaetaDatabase.getInstance(this)
        val solver = MlKitCaptchaSolver().also { captchaSolver = it }
        val solveCaptchaUseCase = SolveCaptchaUseCase(NetworkClient.apiService, solver)
        repository = CardRepositoryImpl(
            db.cardDao(),
            db.balanceHistoryDao(),
            NetworkClient.apiService,
            solveCaptchaUseCase,
            turnstileProvider
        )

        processNfcScanUseCase = ProcessNfcScanUseCase(repository)

        val webCookieStore = AndroidWebCookieStore()
        val accountCookieJar = WebViewCookieJar(RedBusAccountNetworkClient.HOST, webCookieStore)
        val accountApiService = RedBusAccountNetworkClient.create(accountCookieJar)
        val accountRepository = RedBusAccountRepositoryImpl(accountApiService, accountCookieJar)
        val syncRedBusCardsUseCase = SyncRedBusCardsUseCase(repository)
        val refreshAllBalancesUseCase = RefreshAllBalancesUseCase(repository, accountRepository, syncRedBusCardsUseCase)
        redBusAccountViewModel = RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)

        getCardBalanceUseCase = GetCardBalanceUseCase(repository, accountRepository)
        cardsViewModel = CardsViewModel(repository, refreshAllBalancesUseCase)

        setContent {
            SaetaSaldoTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SaetaAppContent(
                        cardsViewModel = cardsViewModel,
                        redBusAccountViewModel = redBusAccountViewModel,
                        repository = repository,
                        accountRepository = accountRepository,
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

    override fun onDestroy() {
        super.onDestroy()
        captchaSolver?.close()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun SaetaAppContent(
    cardsViewModel: CardsViewModel,
    redBusAccountViewModel: RedBusAccountViewModel,
    repository: CardRepository,
    accountRepository: RedBusAccountRepository,
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
    val accountState by redBusAccountViewModel.uiState.collectAsState()

    // One-shot check for an existing RedBus session; never syncs on its own.
    LaunchedEffect(Unit) {
        redBusAccountViewModel.checkExistingSession()
    }

    // Push the favorite card snapshot to paired watches whenever it changes.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val sync = WearSyncManager(context)
        repository.getAllCards()
            .map { cards -> cards.firstOrNull { it.isFavorite } }
            .distinctUntilChanged()
            .collect { favorite ->
                sync.pushFavoriteCard(favorite)
                SaetaBalanceWidget().updateAll(context)
            }
    }

    // Handle NFC tag scanned events
    LaunchedEffect(Unit) {
        scannedTagFlow.collect { uid ->
            scannedTagFlow.resetReplayCache()
            showNfcBottomSheet = false
            when (val result = processNfcScanUseCase(uid)) {
                is com.saetasaldo.app.domain.usecase.NfcScanResult.ExistingCardFound -> {
                    currentScreen = Screen.CardDetail(result.card.id, refreshOnOpen = true)
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
                onAddCardClick = {
                    newCardPrompt = NewCardPromptState(nfcUid = null)
                },
                onScanNfcClick = {
                    showNfcBottomSheet = true
                },
                isNfcSupported = isNfcSupported,
                redBusAccountState = accountState,
                onConnectRedBus = { currentScreen = Screen.RedBusLogin },
                onSyncRedBus = { redBusAccountViewModel.sync() },
                onDisconnectRedBus = { redBusAccountViewModel.disconnect() },
                onAccountMessageConsumed = { redBusAccountViewModel.consumeMessage() }
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
                    getCardBalanceUseCase = getCardBalanceUseCase,
                    accountRepository = accountRepository
                )
            }
            CardDetailScreen(
                viewModel = detailViewModel,
                refreshOnOpen = screen.refreshOnOpen,
                onBackClick = {
                    currentScreen = Screen.CardsList
                }
            )
        }

        is Screen.RedBusLogin -> {
            // Return to cards once the session is verified. Backing out keeps
            // an already-valid session; it is never disconnected here.
            LaunchedEffect(accountState.sessionState) {
                if (accountState.sessionState == RedBusSessionState.Connected) {
                    currentScreen = Screen.CardsList
                }
            }
            RedBusLoginScreen(
                isVerifying = accountState.isSyncing ||
                    accountState.sessionState == RedBusSessionState.Checking,
                onVerifySession = { redBusAccountViewModel.verifyLoginAndSync() },
                onBack = { currentScreen = Screen.CardsList }
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

    // New Card Prompt Dialog (Manual or after NFC detection)
    newCardPrompt?.let { promptState ->
        NewCardRegistrationDialog(
            nfcUid = promptState.nfcUid,
            onDismiss = { newCardPrompt = null },
            onConfirm = { name, cardNumber ->
                cardsViewModel.addNewCard(
                    name = name,
                    cardNumber = cardNumber,
                    nfcUid = promptState.nfcUid
                ) { result ->
                    val saved = result.getOrNull()
                    if (saved != null) {
                        currentScreen = Screen.CardDetail(saved.id, refreshOnOpen = true)
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
    onConfirm: (name: String, cardNumber: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (nfcUid != null) "Tarjeta NFC Detectada" else "Agregar Tarjeta SAETA",
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
                    label = { Text("Nombre o Alias (opcional)") },
                    placeholder = { Text("Ej: Mi Tarjeta, Trabajo, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Número de Tarjeta") },
                    placeholder = { Text("Ej: 12345678") },
                    supportingText = { Text("Dígitos impresos en el plástico de la tarjeta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, cardNumber) },
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
