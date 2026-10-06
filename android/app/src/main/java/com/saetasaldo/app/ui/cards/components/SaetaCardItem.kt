package com.saetasaldo.app.ui.cards.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.ui.theme.SaetaBluePrimary
import com.saetasaldo.app.ui.theme.SaetaBlueSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaetaCardItem(
    card: SaetaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColor = card.colorArgb?.let { Color(it) }
    val gradient = Brush.horizontalGradient(
        if (customColor != null) {
            listOf(customColor, lerp(customColor, Color.Black, 0.35f))
        } else {
            listOf(SaetaBluePrimary, SaetaBlueSecondary)
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (card.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorita",
                                tint = com.saetasaldo.app.ui.theme.SaetaGold
                            )
                        }
                        if (card.nfcUid != null) {
                            if (card.isFavorite) {
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = "NFC Vinculado",
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = card.formattedBalance(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    card.tripsSubtitle()?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (card.lastUpdated != null) {
                        val formattedTime = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(card.lastUpdated))
                        Text(
                            text = "Actualizado: $formattedTime",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "Nº ${card.cardNumber}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
