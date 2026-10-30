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
            invoice = InvoiceRepository(original).saveDraft(company, customer, "OLD", "1405/07/02", listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 2, unitPriceRial = 100, taxRatePercent = 10)))
            QuotaRepository(original).initializeWallet("owner", 5)
            QuotaRepository(original).transferFromMaster("owner", company, 3, "before-upgrade")
        } finally { original.close() }
        // The only schema change in v2 is moadian_simulations: remove it to recreate
        // the complete v1 table structure, retaining real Room data and foreign keys.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE moadian_simulations")
            it.execSQL("DROP TABLE moadian_details")
            it.version = 1
        }
        val upgraded = Room.databaseBuilder(context, SabaDatabase::class.java, name)
            .addMigrations(SabaDatabase.MIGRATION_1_2, SabaDatabase.MIGRATION_2_3).build()
        try {
            val details = InvoiceRepository(upgraded).detailsForCompany(company, invoice)
            assertEquals(customer, details.invoice.customerId)
            assertEquals("OLD", details.invoice.invoiceNumber)
            assertEquals(220L, details.invoice.totalAmountRial)
            assertEquals(1, details.lines.size)
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
}
