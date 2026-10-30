package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaxpayerSerialAllocatorTest {
    @Test fun automaticNumbersRemainSeparateAndAreNotReusedAfterDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
        try {
            val first = db.companyDao().insert(Company(purchaserId = "owner", name = "اول", nationalId = "1"))
            val second = db.companyDao().insert(Company(purchaserId = "owner", name = "دوم", nationalId = "2"))
            for (id in listOf(first, second)) db.moadianDetailsDao().upsert(MoadianDetails(companyId = id, invoiceSerialStart = "1000000001", referenceSerialStart = "2000000001"))
            val customer = db.customerDao().insert(Customer(companyId = first, name = "خریدار"))
            QuotaRepository(db).initializeWallet("owner", 10)
            QuotaRepository(db).transferFromMaster("owner", first, 10, "seed")
            val repo = InvoiceRepository(db)
            val line = listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 1, unitPriceRial = 100, taxRatePercent = 0))
            val firstInvoice = repo.saveDraft(first, customer, "", "1405/08/08", line)
            assertEquals("1000000001", db.invoiceDao().byId(firstInvoice)!!.invoiceNumber)
            repo.deleteDraft(first, firstInvoice)
            val nextInvoice = repo.saveDraft(first, customer, "", "1405/08/08", line)
            assertEquals("1000000002", db.invoiceDao().byId(nextInvoice)!!.invoiceNumber)
            val allocator = TaxpayerSerialAllocator(db)
            assertEquals("1000000001", allocator.reserve(second))
            assertEquals("2000000001", allocator.reserve(first, reference = true))
            val numbers = (1..5).map { async(Dispatchers.IO) { allocator.reserve(first) } }.awaitAll()
            assertEquals(5, numbers.toSet().size)
            assertEquals(1000000008L, db.moadianDetailsDao().byCompany(first)!!.nextInvoiceSerial)
        } finally { db.close() }
    }
    @Test fun invalidCustomerRollsBackAutomaticReservation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
        try {
            val id = db.companyDao().insert(Company(purchaserId = "owner", name = "اول", nationalId = "1"))
            db.moadianDetailsDao().upsert(MoadianDetails(companyId = id))
            try {
                InvoiceRepository(db).saveDraft(id, 999, "", "1405/08/08",
                    listOf(InvoiceLine(invoiceId = 0, description = "کالا", quantity = 1, unitPriceRial = 1, taxRatePercent = 0)))
                fail("Expected missing customer rejection")
            } catch (_: IllegalArgumentException) {}
            assertNull(db.moadianDetailsDao().byCompany(id)!!.nextInvoiceSerial)
        } finally { db.close() }
    }
}
