package com.saetasaldo.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * Boots MainActivity exactly like a real launch (onCreate → setContent →
 * onResume) so a startup crash shows up as a test failure.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StartupSmokeTest {

    private fun initMlKit() {
        // Robolectric does not run the manifest-declared MlKitInitProvider,
        // so initialize ML Kit the way the provider would on a real device.
        runCatching {
            com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(
                RuntimeEnvironment.getApplication()
            )
        }
    }

    private fun launch() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume().visible()
        // Let posted main-thread work (LaunchedEffects, flow collections) run.
        Shadows.shadowOf(RuntimeEnvironment.getApplication().mainLooper)
            .idleFor(Duration.ofSeconds(5))
        controller.pause().stop().destroy()
    }

    private fun seedV1Database(context: Context) {
        val dbFile = context.getDatabasePath("saeta_saldo.db")
        dbFile.parentFile?.mkdirs()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("saeta_saldo.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `nfcUid` TEXT, `type` TEXT NOT NULL, `currentBalance` REAL, `lastUpdated` INTEGER, `isFavorite` INTEGER NOT NULL, `cardState` TEXT, PRIMARY KEY(`id`))"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `balance_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, `balance` REAL NOT NULL, `difference` REAL NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_balance_history_cardId` ON `balance_history` (`cardId`)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
                        )
                        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'legacy-hash')")
                    }
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                    }
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("PRAGMA user_version = 1")
            db.execSQL(
                "INSERT INTO cards (id, name, cardNumber, type, currentBalance, lastUpdated, isFavorite) VALUES ('c1', 'Mi Tarjeta', '12345678', 'AZUL_COMUN', 1500.0, 1700000000000, 1)"
            )
            db.execSQL(
                "INSERT INTO cards (id, name, cardNumber, type, currentBalance, isFavorite) VALUES ('c2', 'Trabajo', '87654321', 'SUBE', 300.0, 0)"
            )
            db.execSQL(
                "INSERT INTO balance_history (cardId, balance, difference, timestamp) VALUES ('c1', 1500.0, 0.0, 1700000000000)"
            )
        } finally {
            helper.close()
        }
    }

    @Test
    fun `main activity launches without crashing on fresh install`() {
        initMlKit()
        launch()
    }

    @Test
    fun `main activity launches after upgrade from v1 database`() {
        initMlKit()
        val context = RuntimeEnvironment.getApplication()
        seedV1Database(context)
        launch()
    }
}
