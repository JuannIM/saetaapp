package com.saetasaldo.app.ui.nfc

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saetasaldo.app.ui.theme.SaetaBluePrimary
import com.saetasaldo.app.ui.theme.SaetaBlueSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScanBottomSheet(
    onDismissRequest: () -> Unit,
    isNfcSupported: Boolean = true,
    isNfcEnabled: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Acercá tu tarjeta SAETA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mantené la tarjeta apoyada en la parte trasera del teléfono hasta que vibre.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isNfcSupported) {
                // Device has no NFC support
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Este dispositivo no cuenta con hardware NFC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (!isNfcEnabled) {
                // NFC is disabled in Android settings
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "El sensor NFC se encuentra desactivado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaetaBluePrimary)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Activar NFC en Ajustes")
                    }
                }
            } else {
                // Animated NFC Radar Wave Visual
                NfcRadarAnimation()
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun NfcRadarAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nfc_radar")

    val wave1Scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_scale"
    )

    val wave1Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1_alpha"
    )

    val wave2Scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_scale"
    )

    val wave2Alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2_alpha"
    )

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer wave 1
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(wave1Scale)
                .alpha(wave1Alpha)
                .background(SaetaBlueSecondary, CircleShape)
        )

        // Middle wave 2
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(wave2Scale)
                .alpha(wave2Alpha)
                .background(SaetaBluePrimary, CircleShape)
        )

        // Central NFC icon badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SaetaBluePrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = "Radar NFC",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}
