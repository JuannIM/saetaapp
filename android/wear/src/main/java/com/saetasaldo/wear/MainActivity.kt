package com.saetasaldo.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PATH_BALANCE = "/saeta/balance"
private const val PATH_REFRESH_REQUEST = "/saeta/refresh"
private const val KEY_NAME = "name"
private const val KEY_BALANCE = "balance"
private const val KEY_UPDATED = "updated"
private const val KEY_HAS_CARD = "hasCard"

data class BalanceSnapshot(
    val hasCard: Boolean = false,
    val name: String = "",
    val balance: String = "",
    val updated: Long = 0L
)

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private val snapshot = MutableStateFlow(BalanceSnapshot())
    private var refreshing = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            Wearable.getDataClient(this@MainActivity).dataItems.await()
                .firstOrNull { it.uri.path == PATH_BALANCE }
                ?.let { snapshot.value = it.toSnapshot() }
        }
        setContent {
            MaterialTheme {
                WearBalanceScreen(
                    snapshot = snapshot.collectAsState().value,
                    refreshing = refreshing.collectAsState().value,
                    onRefresh = { requestRefresh() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == PATH_BALANCE
            ) {
                snapshot.value = event.dataItem.toSnapshot()
                refreshing.value = false
            }
        }
    }

    private fun requestRefresh() {
        refreshing.value = true
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, PATH_REFRESH_REQUEST, byteArrayOf())
                        .await()
                }
            } catch (_: Exception) {
                refreshing.value = false
            }
        }
    }

    private fun com.google.android.gms.wearable.DataItem.toSnapshot(): BalanceSnapshot {
        val map = DataMapItem.fromDataItem(this).dataMap
        return BalanceSnapshot(
            hasCard = map.getBoolean(KEY_HAS_CARD),
            name = map.getString(KEY_NAME).orEmpty(),
            balance = map.getString(KEY_BALANCE).orEmpty(),
            updated = map.getLong(KEY_UPDATED)
        )
    }
}

@Composable
fun WearBalanceScreen(
    snapshot: BalanceSnapshot,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    TimeText()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!snapshot.hasCard) {
            Text(
                text = "Sin tarjeta favorita",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = snapshot.name,
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = snapshot.balance,
                style = MaterialTheme.typography.display2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (snapshot.updated > 0) {
                val time = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    .format(Date(snapshot.updated))
                Text(
                    text = "Act: $time",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            Button(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "..." else "Actualizar")
            }
        }
    }
}
