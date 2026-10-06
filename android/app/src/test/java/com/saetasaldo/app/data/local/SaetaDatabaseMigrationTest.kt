package com.saetasaldo.app.data.local

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SaetaDatabaseMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase("migration-test.db")
    }

    @Test
    fun `migration 1 to 2 adds new columns and preserves rows`() {
        context.deleteDatabase("migration-test.db")

        // Build a v1 database with the pre-migration schema.
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE cards (
                                id TEXT NOT NULL PRIMARY KEY,
                                name TEXT NOT NULL,
                                cardNumber TEXT NOT NULL,
                                nfcUid TEXT,
                                type TEXT NOT NULL,
                                currentBalance REAL,
                                lastUpdated INTEGER,
                                isFavorite INTEGER NOT NULL,
                                cardState TEXT
                            )"""
                        )
                        db.execSQL(
                            """CREATE TABLE balance_history (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                cardId TEXT NOT NULL,
                                balance REAL NOT NULL,
                                difference REAL NOT NULL,
                                timestamp INTEGER NOT NULL
                            )"""
                        )
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper!!.writableDatabase
        db.execSQL(
            """INSERT INTO cards (id, name, cardNumber, type, currentBalance, isFavorite)
               VALUES ('c1', 'Mi Tarjeta', '12345678', 'AZUL_COMUN', 1500.0, 1)"""
        )

        SaetaDatabase.MIGRATION_1_2.migrate(db)

        val cursor = db.query("SELECT * FROM cards WHERE id = 'c1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Mi Tarjeta", it.getString(it.getColumnIndexOrThrow("name")))
            assertTrue(it.getColumnIndex("colorArgb") >= 0)
            assertTrue(it.getColumnIndex("internalNumber") >= 0)
            assertTrue(it.getColumnIndex("walletsJson") >= 0)
            assertTrue(it.isNull(it.getColumnIndexOrThrow("colorArgb")))
        }
    }
}
