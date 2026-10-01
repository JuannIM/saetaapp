package com.saetasaldo.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.saetasaldo.app.data.local.SaetaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaetaBalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = SaetaDatabase.getInstance(context)
        val card = withContext(Dispatchers.IO) {
            db.cardDao().getFavoriteCard()
        }

        provideContent {
            val balanceStr = card?.currentBalance?.let { String.format(Locale.getDefault(), "$ %.2f", it) } ?: "$ --"
            val cardName = card?.name ?: "Sin tarjeta favorita"
            val lastUpdateStr = card?.lastUpdated?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
            } ?: "--:--"

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cardName,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                }

                Text(
                    text = balanceStr,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                    modifier = GlanceModifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Act: $lastUpdateStr",
                        style = TextStyle(fontSize = 11.sp),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Button(
                        text = "Refrescar",
                        onClick = actionRunCallback<RefreshBalanceAction>()
                    )
                }
            }
        }
    }
}
