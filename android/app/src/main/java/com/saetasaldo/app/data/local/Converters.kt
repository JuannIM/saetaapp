package com.saetasaldo.app.data.local

import androidx.room.TypeConverter
import com.saetasaldo.app.domain.model.CardType

class Converters {
    @TypeConverter
    fun fromCardType(value: CardType?): String? = value?.name

    @TypeConverter
    fun toCardType(value: String?): CardType =
        value?.let { runCatching { CardType.valueOf(it) }.getOrDefault(CardType.AZUL_COMUN) } ?: CardType.AZUL_COMUN
}
