package com.saetasaldo.app.data.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

open class AndroidNfcManager(
    private val context: Context,
    val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
) {
    val isNfcSupported: Boolean get() = nfcAdapter != null
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled || activity.isFinishing) return

        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val baseFlags = PendingIntent.FLAG_UPDATE_CURRENT
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseFlags or PendingIntent.FLAG_MUTABLE
        } else {
            baseFlags
        }
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)

        runCatching {
            adapter.enableForegroundDispatch(activity, pendingIntent, arrayOf(techFilter, tagFilter), null)
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        if (nfcAdapter?.isEnabled != true) return
        runCatching {
            nfcAdapter?.disableForegroundDispatch(activity)
        }
    }

    @Suppress("DEPRECATION")
    fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(50)
        }
    }

    companion object {
        fun bytesToHex(bytes: ByteArray): String {
            val sb = java.lang.StringBuilder()
            for (b in bytes) {
                sb.append(String.format("%02X", b))
            }
            return sb.toString()
        }

        fun extractUidFromTag(tag: Tag): String {
            return tag.id?.let { bytesToHex(it) }.orEmpty()
        }
    }
}
