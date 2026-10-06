package com.saetasaldo.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saetasaldo.app.domain.model.RedBusSessionState

@Composable
fun RedBusAccountDialog(
    state: RedBusAccountUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = "Cuenta RedBus",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    state.sessionState == RedBusSessionState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = "Verificando sesión…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    state.isSyncing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = "Sincronizando…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    state.sessionState == RedBusSessionState.Connected -> {
                        Text(
                            text = "Cuenta conectada. Solo se sincronizan las tarjetas vinculadas a tu cuenta y su saldo principal (Principal (Dinero)).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            text = "Conectar tu cuenta es opcional. Se abrirá el sitio oficial de RedBus dentro de la app para que inicies sesión. SAETA Saldo nunca lee ni guarda tu contraseña.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "El modo anónimo con captcha sigue funcionando aunque no conectes una cuenta.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "SAETA Saldo es una app independiente, no oficial de SAETA/RedBus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            when (state.sessionState) {
                RedBusSessionState.Connected -> {
                    Button(
                        onClick = onSync,
                        enabled = !state.isSyncing
                    ) {
                        Text("Sincronizar ahora")
                    }
                }
                RedBusSessionState.Checking -> {
                    // No primary action while the session is being verified.
                }
                else -> {
                    Button(onClick = onConnect) {
                        Text("Conectar con RedBus")
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (state.sessionState == RedBusSessionState.Connected) {
                    TextButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Desconectar")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    )
}
