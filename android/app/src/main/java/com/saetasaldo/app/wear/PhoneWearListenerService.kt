package com.saetasaldo.app.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.repository.CardRepositoryImpl
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Handles "refresh" requests sent from the watch. Runs the same anonymous
 * balance path as the widget — the account-aware path needs WebView session
 * state that a cold service cannot safely touch — then pushes the result back.
 */
class PhoneWearListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearSyncManager.PATH_REFRESH_REQUEST) return
        scope.launch {
            val db = SaetaDatabase.getInstance(this@PhoneWearListenerService)
            MlKitCaptchaSolver().use { solver ->
                val repo = CardRepositoryImpl(
                    db.cardDao(),
                    db.balanceHistoryDao(),
                    NetworkClient.apiService,
                    SolveCaptchaUseCase(NetworkClient.apiService, solver)
                )
                val favorite = repo.getFavoriteCard() ?: return@use
                runCatching { repo.refreshCardBalance(favorite.cardNumber) }
                WearSyncManager(this@PhoneWearListenerService)
                    .pushFavoriteCard(repo.getFavoriteCard())
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
