package com.saetasaldo.app.data.local

import android.content.Context
import com.saetasaldo.app.domain.model.CardWallet

/**
 * Persists the user-configured reference fare so the detail screen, card list,
 * widget and watch all render the same trip estimate. Stored on-device only,
 * in the app's private SharedPreferences.
 */
class FareStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Double =
        prefs.getFloat(KEY_FARE_ARS, CardWallet.DEFAULT_FARE.toFloat()).toDouble()

    fun set(value: Double) {
        if (value <= 0.0 || value.isNaN()) return
        prefs.edit().putFloat(KEY_FARE_ARS, value.toFloat()).apply()
    }

    private companion object {
        const val PREFS_NAME = "saeta_prefs"
        const val KEY_FARE_ARS = "fare_ars"
    }
}
