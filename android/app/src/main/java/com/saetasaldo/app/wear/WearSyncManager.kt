package com.saetasaldo.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.saetasaldo.app.domain.model.SaetaCard
import kotlinx.coroutines.tasks.await

/**
 * Pushes the favorite card's snapshot to paired Wear OS devices via the Data
 * Layer. The watch only renders this payload — all balance queries and captcha
 * resolution stay on the phone.
 */
class WearSyncManager(private val context: Context) {

    companion object {
        const val PATH_BALANCE = "/saeta/balance"
        const val PATH_REFRESH_REQUEST = "/saeta/refresh"
        const val KEY_NAME = "name"
        const val KEY_BALANCE = "balance"
        const val KEY_UPDATED = "updated"
        const val KEY_HAS_CARD = "hasCard"
    }

    suspend fun pushFavoriteCard(card: SaetaCard?) {
        try {
            val request = PutDataMapRequest.create(PATH_BALANCE).apply {
                dataMap.putBoolean(KEY_HAS_CARD, card != null)
                if (card != null) {
                    dataMap.putString(KEY_NAME, card.name)
                    dataMap.putString(KEY_BALANCE, card.formattedBalance())
                    dataMap.putLong(KEY_UPDATED, card.lastUpdated ?: 0L)
                }
                // Timestamp forces delivery even when the payload is unchanged.
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.w("WearSync", "push failed", e)
        }
    }
}
