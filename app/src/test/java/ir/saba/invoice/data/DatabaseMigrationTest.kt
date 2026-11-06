package ir.saba.invoice.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseMigrationTest {
    /** Downgraded test fixtures must match the real pre-v11 invoice_lines layout. */
    private fun restoreLegacyInvoiceLineLayout(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE invoice_lines_legacy (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            invoiceId INTEGER NOT NULL, itemCode TEXT NOT NULL,
            description TEXT NOT NULL, quantity INTEGER NOT NULL,
            unitPriceRial INTEGER NOT NULL, discountRial INTEGER NOT NULL,
            taxRatePercent INTEGER NOT NULL,
            FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""".trimIndent())
        db.execSQL("""INSERT INTO invoice_lines_legacy
            (id,invoiceId,itemCode,description,quantity,unitPriceRial,discountRial,taxRatePercent)
            SELECT id,invoiceId,itemCode,description,quantity,unitPriceRial,discountRial,taxRatePercent
            FROM invoice_lines""".trimIndent())
        db.execSQL("DROP TABLE invoice_lines")
        db.execSQL("ALTER TABLE invoice_lines_legacy RENAME TO invoice_lines")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_invoiceId ON invoice_lines(invoiceId)")
    }
    @Test fun roomOpensVersionOneSchemaAndPreservesActualInvoiceRelations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val original = Room.databaseBuilder(context, SabaDatabase::class.java, name).build()
        val company: Long
        val customer: Long
        val invoice: Long
        try {
            company = original.companyDao().insert(Company(purchaserId = "owner", name = "شرکت", nationalId = "123"))
            customer = original.customerDao().insert(Customer(companyId = company, name = "خریدار"))
            invoice = original.invoiceDao().insert(Invoice(companyId = company, customerId = customer, invoiceNumber = "OLD", issueDate = "1405/07/02", totalAmountRial = 220, taxAmountRial = 20))
            original.invoiceLineDao().insertAll(listOf(InvoiceLine(invoiceId = invoice, description = "کالا", quantity = 2, unitPriceRial = 100, taxRatePercent = 10)))
            QuotaRepository(original).initializeWallet("owner", 5)
            QuotaRepository(original).transferFromMaster("owner", company, 3, "before-upgrade")
        } finally { original.close() }
        // The only schema change in v2 is moadian_simulations: remove it to recreate
        // the complete v1 table structure, retaining real Room data and foreign keys.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE moadian_simulations")
            it.execSQL("DROP TABLE moadian_details")
            it.execSQL("DROP TABLE legacy_quota_exemptions")
            it.execSQL("DROP TABLE customer_profiles")
            it.execSQL("DROP TABLE credit_transfer_claims")
            it.execSQL("DROP INDEX index_invoices_companyId_customerId")
            it.execSQL("DROP TABLE official_submissions")
            it.execSQL("DROP TABLE legacy_import_receipts")
            it.execSQL("DROP TABLE invoice_creation_claims")
            restoreLegacyInvoiceLineLayout(it)
            it.version = 1
        }
        val upgraded = Room.databaseBuilder(context, SabaDatabase::class.java, name)
            .addMigrations(SabaDatabase.MIGRATION_1_2, SabaDatabase.MIGRATION_2_3, SabaDatabase.MIGRATION_3_4, SabaDatabase.MIGRATION_4_5, SabaDatabase.MIGRATION_5_6, SabaDatabase.MIGRATION_6_7, SabaDatabase.MIGRATION_7_8, SabaDatabase.MIGRATION_8_9, SabaDatabase.MIGRATION_9_10, SabaDatabase.MIGRATION_10_11, SabaDatabase.MIGRATION_11_12).build()
        try {
            val details = InvoiceRepository(upgraded).detailsForCompany(company, invoice)
            assertEquals(customer, details.invoice.customerId)
            assertEquals("OLD", details.invoice.invoiceNumber)
            assertEquals(220L, details.invoice.totalAmountRial)
            assertEquals(1, details.lines.size)
            assertEquals(1, upgraded.legacyQuotaExemptionDao().contains(invoice))
            InvoiceRepository(upgraded).finalizeDraft(company, invoice)
            assertEquals("FINALIZED", upgraded.invoiceDao().byId(invoice)!!.status)
            assertNull(upgraded.quotaDao().useForInvoice(invoice))
            assertEquals(3L, upgraded.quotaDao().companyQuota(company)!!.available)
            assertEquals(2L, upgraded.quotaDao().wallet("owner")!!.available)
            upgraded.moadianSimulationDao().insert(MoadianSimulation(companyId = company, invoiceId = invoice, requestId = "local", payloadHash = "hash", createdAtMillis = 1))
            assertEquals(1, upgraded.moadianSimulationDao().forCompany(company).size)
            assertNull(upgraded.moadianDetailsDao().byCompany(company))
            upgraded.moadianDetailsDao().upsert(MoadianDetails(companyId = company, postalCode = "1234567890"))
            assertEquals("1234567890", upgraded.moadianDetailsDao().byCompany(company)!!.postalCode)
            upgraded.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
    @Test fun versionEightPendingCreditRequestSurvivesOutboxMigration()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-v8-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,SabaDatabase::class.java,name).build()
        val owner="11111111-1111-4111-8111-111111111111"
        val company=db.companyDao().insert(Company(purchaserId=owner,name="قدیمی",nationalId="old"))
        val claim=InvoiceCreationClaim("22222222-2222-4222-8222-222222222222",owner,company,"PAID","immutable-payload")
        db.invoiceCreationClaimDao().insert(claim);db.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE credit_transfer_claims")
            it.execSQL("DROP INDEX index_invoices_companyId_customerId")
            it.execSQL("DROP TABLE official_submissions")
            it.execSQL("DROP TABLE legacy_import_receipts")
            restoreLegacyInvoiceLineLayout(it)
            it.version=8
        }
        db=Room.databaseBuilder(context,SabaDatabase::class.java,name).addMigrations(SabaDatabase.MIGRATION_8_9, SabaDatabase.MIGRATION_9_10, SabaDatabase.MIGRATION_10_11, SabaDatabase.MIGRATION_11_12).build()
        try {
            assertEquals(claim,db.invoiceCreationClaimDao().pending(company))
            assertTrue(db.officialSubmissionDao().forCompany(company).isEmpty())
            assertNull(db.legacyImportReceiptDao().byCompany(company))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }finally{db.close();context.deleteDatabase(name)}
    }

}
