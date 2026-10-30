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
    private suspend fun fund(id: Long, amount: Long = 10) {
        if (db.quotaDao().companyQuota(id) != null) return
        val owner = db.companyDao().byId(id)!!.purchaserId
        QuotaRepository(db).initializeWallet(owner, amount)
        QuotaRepository(db).transferFromMaster(owner, id, amount, "fund-$id")
    }
    private suspend fun draft(number: String = "1", funded: Boolean = true): Long {
        if (funded) fund(company)
        return repository.saveDraft(company, customer, number, "1405/07/02", listOf(line(), line(200)))
    }
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

    @Test fun cancellingDraftPreservesLinesAndConsumedQuota() = runBlocking {
        val id = draft()
        repository.deleteDraft(company, id)
        assertEquals("CANCELLED", db.invoiceDao().byId(id)!!.status)
        assertEquals(2, db.invoiceLineDao().forInvoice(id).size)
        assertNotNull(db.quotaDao().useForInvoice(id))
        assertEquals(9L, db.quotaDao().companyQuota(company)!!.available)
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
        fund(otherCompany)
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

    @Test fun creationConsumesOnceAndFinalizationPreventsEditingOrDeleting() = runBlocking {
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

    @Test fun missingQuotaRejectsCreationWithoutPartialRows() = runBlocking {
        expectRejected { draft(funded = false) }
        assertTrue(db.invoiceDao().forCompany(company).isEmpty())
        assertNull(db.quotaDao().companyQuota(company))
    }
    @Test fun injectedQuotaUseFailureRollsBackEntireCreation() = runBlocking {
        fund(company, 1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_quota_use BEFORE INSERT ON invoice_quota_uses " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        var rejected = false
        try { draft("rollback-test") } catch (_: RuntimeException) { rejected = true }
        assertTrue(rejected)
        assertTrue(db.invoiceDao().forCompany(company).isEmpty())
        assertEquals(1L, db.quotaDao().companyQuota(company)!!.available)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM invoice_quota_uses").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
    }

    @Test fun crossCompanyFinalizationCannotConsumeQuotaOrChangeStatus() = runBlocking {
        val quota = QuotaRepository(db)
        quota.initializeWallet("owner", 1)
        quota.transferFromMaster("owner", company, 1, "cross-company")
        val id = draft("cross-company-draft")
        expectRejected { repository.finalizeDraft(otherCompany, id) }
        assertEquals("DRAFT", db.invoiceDao().byId(id)!!.status)
        assertEquals(0L, db.quotaDao().companyQuota(company)!!.available)
        assertNotNull(db.quotaDao().useForInvoice(id))
        repository.finalizeDraft(company, id)
        expectRejected { repository.finalizeDraft(otherCompany, id) }
        assertEquals("FINALIZED", db.invoiceDao().byId(id)!!.status)
        assertEquals(0L, db.quotaDao().companyQuota(company)!!.available)
        assertNotNull(db.quotaDao().useForInvoice(id))
    }

    @Test fun concurrentNewInvoicesCannotOverspendLastUnit() = runBlocking {
        fund(company, 1)
        val outcomes = coroutineScope {
            (1..8).map { n -> async { runCatching { draft("race-$n") } } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(0L, db.quotaDao().companyQuota(company)!!.available)
        assertEquals(1, db.invoiceDao().forCompany(company).size)
        val id = db.invoiceDao().forCompany(company).single().id
        assertNotNull(db.quotaDao().useForInvoice(id))
        repository.saveDraft(company, customer, "edited", "1405/07/03", listOf(line()), id)
        repository.finalizeDraft(company, id)
        repository.finalizeDraft(company, id)
        assertEquals(0L, db.quotaDao().companyQuota(company)!!.available)
    }
}
