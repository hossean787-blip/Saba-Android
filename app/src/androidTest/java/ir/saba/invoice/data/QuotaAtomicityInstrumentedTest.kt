package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotaAtomicityInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
    private val invoices = InvoiceRepository(db)
    private val quotas = QuotaRepository(db)

    @After fun close() = db.close()

    private suspend fun seed(): Pair<Long, Long> {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Tenant", nationalId = "12345678901"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        quotas.initializeWallet("owner", 1)
        quotas.transferFromMaster("owner", company, 1, "transfer-1")
        val invoiceId = invoices.saveDraft(company, customer, "100", "1405/07/01",
            listOf(InvoiceLine(invoiceId = 0, description = "Service", quantity = 1,
                unitPriceRial = 1000, taxRatePercent = 10)))
        return company to invoiceId
    }

    @Test fun finalizingTwiceConsumesQuotaOnlyOnce() = runBlocking {
        val (companyId, id) = seed()
        invoices.finalizeDraft(companyId, id)
        invoices.finalizeDraft(companyId, id)
        assertEquals("FINALIZED", db.invoiceDao().byId(id)?.status)
        assertEquals(0L, db.quotaDao().companyQuota(companyId)?.available)
        assertNotNull(db.quotaDao().useForInvoice(id))
    }

    @Test fun concurrentFinalizationConsumesQuotaOnce() = runBlocking {
        val (companyId, id) = seed()
        coroutineScope {
            (1..8).map { async { invoices.finalizeDraft(companyId, id) } }.awaitAll()
        }
        assertEquals("FINALIZED", db.invoiceDao().byId(id)?.status)
        assertEquals(0L, db.quotaDao().companyQuota(companyId)?.available)
        assertNotNull(db.quotaDao().useForInvoice(id))
    }

    @Test fun failureToInsertQuotaUseRollsBackEntireCreation() = runBlocking {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Tenant", nationalId = "123"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        quotas.initializeWallet("owner", 1)
        quotas.transferFromMaster("owner", company, 1, "seed")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_quota_use BEFORE INSERT ON invoice_quota_uses " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try {
            invoices.saveDraft(company, customer, "100", "1405/07/01",
                listOf(InvoiceLine(invoiceId = 0, description = "Service", quantity = 1, unitPriceRial = 100)))
            throw AssertionError("Expected database transaction to fail")
        } catch (expected: RuntimeException) {
            assertEquals(0, db.invoiceDao().forCompany(company).size)
            assertEquals(1L, db.quotaDao().companyQuota(company)?.available)
        }
    }
}
