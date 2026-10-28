package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests DDL migration without destroying an existing unrelated record. */
@RunWith(AndroidJUnit4::class)
class MigrationSmokeTest {
    @Test fun migrationCreatesSimulationTableWithoutErasingPreviousRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "migration_smoke_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(filename)
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL("CREATE TABLE preserved_records (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            raw.execSQL("INSERT INTO preserved_records (id, value) VALUES (1, 'keep')")
            raw.version = 1
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(filename)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        SabaDatabase.MIGRATION_1_2.migrate(db)
                    }
                }).build()
        )
        helper.writableDatabase.use { db ->
            db.query("SELECT value FROM preserved_records WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("keep", c.getString(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'moadian_simulations'").use { c ->
                assertTrue(c.moveToFirst())
            }
            db.execSQL("INSERT INTO moadian_simulations (companyId, invoiceId, requestId, payloadHash, localStatus, createdAtMillis) VALUES (1, 2, 'r', 'h', 'SIMULATED', 1)")
            try {
                db.execSQL("INSERT INTO moadian_simulations (companyId, invoiceId, requestId, payloadHash, localStatus, createdAtMillis) VALUES (1, 2, 'r2', 'h2', 'SIMULATED', 2)")
                fail("Duplicate invoice simulation was not rejected")
            } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        }
        helper.close()
        context.deleteDatabase(filename)
    }
}
