package com.saetasaldo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.saetasaldo.app.data.local.dao.BalanceHistoryDao
import com.saetasaldo.app.data.local.dao.CardDao
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import com.saetasaldo.app.data.local.entity.CardEntity

@Database(
    entities = [CardEntity::class, BalanceHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SaetaDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun balanceHistoryDao(): BalanceHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SaetaDatabase? = null

        fun getInstance(context: Context): SaetaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaetaDatabase::class.java,
                    "saeta_saldo.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
