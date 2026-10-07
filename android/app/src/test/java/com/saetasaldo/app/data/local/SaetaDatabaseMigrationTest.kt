package com.saetasaldo.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SaetaDatabaseMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        context.deleteDatabase("migration-test.db")
    }

    @Test
    fun `migration 1 to 2 adds new columns and preserves rows`() = runBlocking {
        context.deleteDatabase("migration-test.db")

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `nfcUid` TEXT, `type` TEXT NOT NULL, `currentBalance` REAL, `lastUpdated` INTEGER, `isFavorite` INTEGER NOT NULL, `cardState` TEXT, PRIMARY KEY(`id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `balance_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, `balance` REAL NOT NULL, `difference` REAL NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_balance_history_cardId` ON `balance_history` (`cardId`)"
                        )
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO cards (id, name, cardNumber, type, currentBalance, isFavorite) VALUES ('c1', 'Mi Tarjeta', '12345678', 'AZUL_COMUN', 1500.0, 1)"
            )
            db.execSQL(
                "INSERT INTO balance_history (cardId, balance, difference, timestamp) VALUES ('c1', 1500.0, 0.0, 1)"
            )
        } finally {
            helper.close()
        }

        val db = Room.databaseBuilder(context, SaetaDatabase::class.java, "migration-test.db")
            .addMigrations(SaetaDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val card = db.cardDao().getCardById("c1")
            assertNotNull(card)
            assertEquals("Mi Tarjeta", card!!.name)
            assertEquals(1500.0, card.currentBalance!!, 0.001)
            assertNull(card.colorArgb)
            assertNull(card.internalNumber)
            val latest = db.balanceHistoryDao().getLatestBalanceRecord("c1")
            assertNotNull(latest)
            assertEquals(1500.0, latest!!.balance, 0.001)
        } finally {
            db.close()
        }
    }
}
