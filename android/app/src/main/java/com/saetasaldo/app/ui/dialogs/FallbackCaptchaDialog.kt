package com.saetasaldo.app.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun FallbackCaptchaDialog(
    captchaBitmap: Bitmap?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Verificación de Seguridad",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No se pudo reconocer automáticamente el captcha tras varios intentos. Por favor ingresá los caracteres que se muestran a continuación:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Captcha Image Container
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (captchaBitmap != null) {
                            Image(
                                bitmap = captchaBitmap.asImageBitmap(),
                                contentDescription = "Imagen de Captcha",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onRefreshCaptcha) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Generar nuevo captcha"
                        )
                    }
                }

                // Captcha Text Field
                OutlinedTextField(
                    value = enteredCode,
                    onValueChange = { enteredCode = it.uppercase().take(6) },
                    label = { Text("Código del Captcha") },
                    placeholder = { Text("Ej: 4B8Y") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(enteredCode) },
                enabled = enteredCode.isNotBlank()
            ) {
                Text("Verificar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        modifier = modifier
    )
}
