package com.saetasaldo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saetasaldo.app.data.local.dao.BalanceHistoryDao
import com.saetasaldo.app.data.local.dao.CardDao
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import com.saetasaldo.app.data.local.entity.CardEntity

@Database(
    entities = [CardEntity::class, BalanceHistoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SaetaDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun balanceHistoryDao(): BalanceHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SaetaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN colorArgb INTEGER")
                db.execSQL("ALTER TABLE cards ADD COLUMN internalNumber TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN walletsJson TEXT")
            }
        }

        fun getInstance(context: Context): SaetaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaetaDatabase::class.java,
                    "saeta_saldo.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
