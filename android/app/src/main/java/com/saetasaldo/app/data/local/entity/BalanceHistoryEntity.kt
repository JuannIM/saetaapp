package com.saetasaldo.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saetasaldo.app.domain.model.BalanceRecord

@Entity(
    tableName = "balance_history",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class BalanceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val balance: Double,
    val difference: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): BalanceRecord = BalanceRecord(
        id = id,
        cardId = cardId,
        balance = balance,
        difference = difference,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(record: BalanceRecord): BalanceHistoryEntity = BalanceHistoryEntity(
            id = record.id,
            cardId = record.cardId,
            balance = record.balance,
            difference = record.difference,
            timestamp = record.timestamp
        )
    }
}
