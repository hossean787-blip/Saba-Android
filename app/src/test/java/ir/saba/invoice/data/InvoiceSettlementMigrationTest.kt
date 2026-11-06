package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceSettlementMigrationTest {
    @Test fun migrationFromElevenPreservesAmountsAndDefaultsExistingInvoicesToCash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "invoice-settlement-migration-" + System.nanoTime() + ".db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sql ->
            sql.execSQL("""CREATE TABLE invoices (
                id INTEGER PRIMARY KEY NOT NULL,
                invoiceNumber TEXT NOT NULL,
                totalAmountRial INTEGER NOT NULL,
                taxAmountRial INTEGER NOT NULL,
                status TEXT NOT NULL
            )""".trimIndent())
            sql.execSQL("INSERT INTO invoices VALUES (1,'42',1100,100,'DRAFT')")
            sql.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {
                        assertEquals(11, oldVersion)
                        assertEquals(12, newVersion)
                        SabaDatabase.MIGRATION_11_12.migrate(db)
                    }
                }).build()
        )
        try {
            helper.writableDatabase.query(
                "SELECT settlementMode,cashPaidRial,totalAmountRial,taxAmountRial FROM invoices WHERE id=1"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("CASH", c.getString(0))
                assertTrue(c.isNull(1))
                assertEquals(1100L, c.getLong(2))
                assertEquals(100L, c.getLong(3))
            }
            helper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            SabaDatabase.MIGRATION_11_12.migrate(helper.writableDatabase)
            helper.writableDatabase.query("SELECT COUNT(*) FROM invoices").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
