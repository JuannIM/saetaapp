package com.saetasaldo.app

import android.app.Application
import android.content.Context
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.remote.RedBusAccountNetworkClient
import com.saetasaldo.app.data.remote.cookie.AndroidWebCookieStore
import com.saetasaldo.app.data.remote.cookie.WebViewCookieJar
import com.saetasaldo.app.data.repository.BusMapRepositoryImpl
import com.saetasaldo.app.data.repository.CardRepositoryImpl
import com.saetasaldo.app.data.repository.RedBusAccountRepositoryImpl
import com.saetasaldo.app.domain.repository.BusMapRepository
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import com.saetasaldo.app.domain.repository.TurnstileTokenProvider
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import com.saetasaldo.app.domain.usecase.ProcessNfcScanUseCase
import com.saetasaldo.app.domain.usecase.RefreshAllBalancesUseCase
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import com.saetasaldo.app.domain.usecase.SyncRedBusCardsUseCase
import com.saetasaldo.app.ui.account.RedBusAccountViewModel
import com.saetasaldo.app.ui.cards.CardsViewModel
import com.saetasaldo.app.ui.turnstile.WebViewTurnstileTokenProvider

class SaetaApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * App-scoped object graph, built once per process on first access. Cold-entry
 * components (widget provider, Wear listener service) deliberately keep
 * building their own minimal repositories and never touch this graph.
 */
class AppContainer(context: Context) {

    private val database: SaetaDatabase = SaetaDatabase.getInstance(context)

    // App-scoped: closing it on Activity.onDestroy would leave a dead
    // recognizer in the container for the next activity instance.
    val captchaSolver: MlKitCaptchaSolver = MlKitCaptchaSolver()
    private val solveCaptchaUseCase =
        SolveCaptchaUseCase(NetworkClient.apiService, captchaSolver)

    // Only the app-facing repository gets the Turnstile provider: its WebView
    // is created lazily inside getToken() on the main thread.
    private val turnstileProvider: TurnstileTokenProvider =
        WebViewTurnstileTokenProvider(context.applicationContext, NetworkClient.apiService)

    val repository: CardRepository = CardRepositoryImpl(
        database.cardDao(),
        database.balanceHistoryDao(),
        NetworkClient.apiService,
        solveCaptchaUseCase,
        turnstileProvider
    )

    private val webCookieStore = AndroidWebCookieStore()
    private val accountCookieJar =
        WebViewCookieJar(RedBusAccountNetworkClient.HOST, webCookieStore)
    private val accountApi = RedBusAccountNetworkClient.create(accountCookieJar)
    val accountRepository: RedBusAccountRepository =
        RedBusAccountRepositoryImpl(accountApi, accountCookieJar)

    val busMapRepository: BusMapRepository =
        BusMapRepositoryImpl(NetworkClient.busMapApiService)

    private val syncRedBusCardsUseCase = SyncRedBusCardsUseCase(repository)
    private val refreshAllBalancesUseCase =
        RefreshAllBalancesUseCase(repository, accountRepository, syncRedBusCardsUseCase)

    val getCardBalanceUseCase = GetCardBalanceUseCase(repository, accountRepository)
    val processNfcScanUseCase = ProcessNfcScanUseCase(repository)

    val cardsViewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
    val redBusAccountViewModel =
        RedBusAccountViewModel(accountRepository, syncRedBusCardsUseCase)
}
