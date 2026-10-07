package com.saetasaldo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceHistoryDao {
    @Query("SELECT * FROM balance_history WHERE cardId = :cardId ORDER BY timestamp DESC, id DESC")
    fun getHistoryForCardFlow(cardId: String): Flow<List<BalanceHistoryEntity>>

    @Query("SELECT * FROM balance_history WHERE cardId = :cardId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestBalanceRecord(cardId: String): BalanceHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BalanceHistoryEntity)
}
