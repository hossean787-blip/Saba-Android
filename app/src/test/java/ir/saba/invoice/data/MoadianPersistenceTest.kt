package ir.saba.invoice.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoadianPersistenceTest {
    @Test fun detailsAndArchiveSurviveReopenWithoutRemovingRelatedRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "moadian-" + System.nanoTime()
        var db = Room.databaseBuilder(context, SabaDatabase::class.java, name).build()
        try {
            val id = db.companyDao().insert(Company(purchaserId = "owner", name = "مودی", nationalId = "123"))
            val other = db.companyDao().insert(Company(purchaserId = "owner", name = "دوم", nationalId = "456"))
            val customer = db.customerDao().insert(Customer(companyId = id, name = "خریدار"))
            QuotaRepository(db).initializeWallet("owner", 10)
            QuotaRepository(db).transferFromMaster("owner", id, 10, "seed")
            val invoice = InvoiceRepository(db).saveDraft(id, customer, "1", "1405/08/08",
                listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 1, unitPriceRial = 100, taxRatePercent = 0)))
            db.moadianDetailsDao().upsert(MoadianDetails(companyId = id, postalCode = "1234567890", encryptedPrivateKey = "ciphertext"))
            db.withTransaction {
                val current = db.moadianDetailsDao().byCompany(id)!!
                db.moadianDetailsDao().upsert(current.copy(inactive = true))
            }
            db.close()
            db = Room.databaseBuilder(context, SabaDatabase::class.java, name).build()
            assertTrue(db.moadianDetailsDao().byCompany(id)!!.inactive)
            assertEquals("ciphertext", db.moadianDetailsDao().byCompany(id)!!.encryptedPrivateKey)
            assertEquals("1234567890", db.moadianDetailsDao().byCompany(id)!!.postalCode)
            assertEquals(customer, db.invoiceDao().byId(invoice)!!.customerId)
            assertNull(db.moadianDetailsDao().byCompany(other))
            assertTrue(db.invoiceDao().forCompany(other).isEmpty())
            db.moadianDetailsDao().upsert(db.moadianDetailsDao().byCompany(id)!!.copy(inactive = false))
            assertFalse(db.moadianDetailsDao().byCompany(id)!!.inactive)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun failedTransactionRollsBackCompanyAndDetails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            try {
                db.withTransaction {
                    val id = db.companyDao().insert(Company(purchaserId = "owner", name = "مودی", nationalId = "123"))
                    db.moadianDetailsDao().upsert(MoadianDetails(companyId = id))
                    throw IllegalStateException("test rollback")
                }
            } catch (_: IllegalStateException) {}
            assertTrue(db.companyDao().all().isEmpty())
            assertTrue(db.moadianDetailsDao().all().isEmpty())
        } finally { db.close() }
    }
}
