package com.saetasaldo.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.data.ocr.MlKitCaptchaSolver
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.repository.CardRepositoryImpl
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshBalanceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            val db = SaetaDatabase.getInstance(context)
            MlKitCaptchaSolver().use { solver ->
                val solveCaptchaUseCase = SolveCaptchaUseCase(NetworkClient.apiService, solver)
                val repo = CardRepositoryImpl(db.cardDao(), db.balanceHistoryDao(), NetworkClient.apiService, solveCaptchaUseCase)

                val favorite = repo.getFavoriteCard() ?: return@use
                // Anonymous path only: the account-aware use case would touch
                // WebView session state in a cold widget process.
                repo.refreshCardBalance(favorite.cardNumber)

                SaetaBalanceWidget().update(context, glanceId)
            }
        }
    }
}
