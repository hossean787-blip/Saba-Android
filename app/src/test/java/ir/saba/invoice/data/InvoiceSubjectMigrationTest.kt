package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceSubjectMigrationTest {
    @Test fun migrationFromTwelveKeepsIssuedInvoicesAndDefaultsToOriginal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "invoice-subject-migration-" + System.nanoTime() + ".db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE invoices (id INTEGER PRIMARY KEY, invoiceNumber TEXT NOT NULL, totalAmountRial INTEGER NOT NULL, taxAmountRial INTEGER NOT NULL)")
            db.execSQL("INSERT INTO invoices VALUES(17,'00012',123456,11456)")
            db.version = 12
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertEquals(12, oldVersion)
                        assertEquals(13, newVersion)
                        SabaDatabase.MIGRATION_12_13.migrate(db)
                    }
                }).build()
        )
        try {
            helper.writableDatabase.query(
                "SELECT id,invoiceNumber,totalAmountRial,taxAmountRial,subject,referenceTaxId FROM invoices WHERE id=17"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(17L, c.getLong(0))
                assertEquals("00012", c.getString(1))
                assertEquals(123456L, c.getLong(2))
                assertEquals(11456L, c.getLong(3))
                assertEquals("ORIGINAL", c.getString(4))
                assertTrue(c.isNull(5))
            }
            // Retried migration after interruption must not drop or overwrite old records.
            SabaDatabase.MIGRATION_12_13.migrate(helper.writableDatabase)
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
