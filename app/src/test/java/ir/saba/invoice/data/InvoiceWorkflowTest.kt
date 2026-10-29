package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceWorkflowTest {
    private lateinit var db: SabaDatabase
    private lateinit var repository: InvoiceRepository
    private var company = 0L
    private var customer = 0L
    private var otherCompany = 0L

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SabaDatabase::class.java).build()
        repository = InvoiceRepository(db)
        company = db.companyDao().insert(Company(purchaserId = "owner", name = "اول", nationalId = "1"))
        otherCompany = db.companyDao().insert(Company(purchaserId = "other", name = "دوم", nationalId = "2"))
        customer = db.customerDao().insert(Customer(companyId = company, name = "مشتری"))
    }

    @After fun close() { db.close() }
    private fun line(price: Long = 100) = InvoiceLine(invoiceId = 0, itemCode = "code", description = "کالا", quantity = 2, unitPriceRial = price, discountRial = 10, taxRatePercent = 10)
    private suspend fun draft(number: String = "1") = repository.saveDraft(company, customer, number, "1405/07/02", listOf(line(), line(200)))
    private suspend fun expectRejected(action: suspend () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun savesAndReloadsMultipleLinesAndDiscounts() = runBlocking {
        val id = draft()
        val details = repository.detailsForCompany(company, id)
        assertEquals(2, details.lines.size)
        assertEquals(580L, details.invoice.totalAmountRial - details.invoice.taxAmountRial)
        assertEquals(638L, details.invoice.totalAmountRial)
        assertEquals("code", details.lines.first().itemCode)
    }

    @Test fun editingReplacesLinesAndKeepsIdentity() = runBlocking {
        val id = draft()
        val updated = repository.saveDraft(company, customer, "2", "1405/07/03", listOf(line(300)), id)
        assertEquals(id, updated)
        val details = repository.detailsForCompany(company, id)
        assertEquals(1, details.lines.size)
        assertEquals("2", details.invoice.invoiceNumber)
        assertEquals(649L, details.invoice.totalAmountRial)
    }

    @Test fun rejectsCrossCompanyRead() = runBlocking {
        val id = draft()
        expectRejected { repository.detailsForCompany(otherCompany, id) }
        expectRejected { repository.linesForInvoice(otherCompany, id) }
        assertEquals(2, repository.linesForInvoice(company, id).size)
    }

    @Test fun rejectsCrossCompanyDeleteAndPreservesDraft() = runBlocking {
        val id = draft()
        expectRejected { repository.deleteDraft(otherCompany, id) }
        assertNotNull(db.invoiceDao().byId(id))
    }

    @Test fun deletingDraftRemovesLinesWithoutConsumingQuota() = runBlocking {
        val id = draft()
        repository.deleteDraft(company, id)
        assertNull(db.invoiceDao().byId(id))
        assertTrue(db.invoiceLineDao().forInvoice(id).isEmpty())
        assertNull(db.quotaDao().useForInvoice(id))
    }

    @Test fun duplicateNumberLeavesOriginalLinesIntact() = runBlocking {
        val id = draft()
        expectRejected { draft() }
        assertEquals(2, db.invoiceLineDao().forInvoice(id).size)
        assertEquals(1, db.invoiceDao().forCompany(company).size)
    }

    @Test fun duplicateNumberDuringEditPreservesBothInvoices() = runBlocking {
        val first = draft("1")
        val second = draft("2")
        expectRejected { repository.saveDraft(company, customer, "1", "1405/07/03", listOf(line(300)), second) }
        assertEquals("2", db.invoiceDao().byId(second)!!.invoiceNumber)
        assertEquals(2, db.invoiceLineDao().forInvoice(first).size)
        assertEquals(2, db.invoiceLineDao().forInvoice(second).size)
    }

    @Test fun customerFromAnotherCompanyCannotBeUsedInDraft() = runBlocking {
        val foreignCustomer = db.customerDao().insert(Customer(companyId = otherCompany, name = "مشتری دوم"))
        expectRejected { repository.saveDraft(company, foreignCustomer, "1", "1405/07/02", listOf(line())) }
        assertTrue(db.invoiceDao().forCompany(company).isEmpty())
    }

    @Test fun separateCompaniesMayHaveTheSameInvoiceNumber() = runBlocking {
        draft("1")
        val foreignCustomer = db.customerDao().insert(Customer(companyId = otherCompany, name = "مشتری دوم"))
        repository.saveDraft(otherCompany, foreignCustomer, "1", "1405/07/02", listOf(line()))
        assertEquals(1, db.invoiceDao().forCompany(company).size)
        assertEquals(1, db.invoiceDao().forCompany(otherCompany).size)
    }

    @Test fun invalidEditRollsBackOriginal() = runBlocking {
        val id = draft()
        expectRejected { repository.saveDraft(company, customer, "2", "1405/07/03", listOf(line().copy(quantity = 0)), id) }
        assertEquals("1", db.invoiceDao().byId(id)!!.invoiceNumber)
        assertEquals(2, db.invoiceLineDao().forInvoice(id).size)
    }

    @Test fun legacyPersianInvoiceNumberCannotBeDuplicatedWithLatinDigits() = runBlocking {
        db.invoiceDao().insert(Invoice(companyId = company, customerId = customer, invoiceNumber = "۱۲", issueDate = "1405/07/02", totalAmountRial = 0, taxAmountRial = 0))
        expectRejected { draft("12") }
        assertEquals(1, db.invoiceDao().forCompany(company).size)
    }

    @Test fun simultaneousFinalizationsStillConsumeOnlyOneQuota() = runBlocking {
        val quota = QuotaRepository(db)
        quota.initializeWallet("owner", 1)
        quota.transferFromMaster("owner", company, 1, "allocation")
        val id = draft()
        coroutineScope { List(8) { async { repository.finalizeDraft(company, id) } }.awaitAll() }
        assertEquals(0L, db.quotaDao().companyQuota(company)!!.available)
        assertNotNull(db.quotaDao().useForInvoice(id))
    }

    @Test fun finalizationConsumesQuotaOnlyOnceAndPreventsEditingOrDeleting() = runBlocking {
        val quota = QuotaRepository(db)
        quota.initializeWallet("owner", 2)
        quota.transferFromMaster("owner", company, 2, "allocation")
        val id = draft()
        repository.finalizeDraft(company, id)
        repository.finalizeDraft(company, id)
        assertEquals(1L, db.quotaDao().companyQuota(company)!!.available)
        expectRejected { repository.deleteDraft(company, id) }
        expectRejected { repository.saveDraft(company, customer, "2", "1405/07/03", listOf(line()), id) }
        assertEquals("FINALIZED", db.invoiceDao().byId(id)!!.status)
    }

    @Test fun missingQuotaPreservesDraftAndLines() = runBlocking {
        val id = draft()
        try { repository.finalizeDraft(company, id); fail("Expected no-quota failure") } catch (_: IllegalArgumentException) { }
        assertEquals("DRAFT", db.invoiceDao().byId(id)!!.status)
        assertEquals(2, db.invoiceLineDao().forInvoice(id).size)
        assertNull(db.quotaDao().useForInvoice(id))
    }
}
