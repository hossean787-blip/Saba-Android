package ir.saba.invoice.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoiceIsolationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
    private val repository = InvoiceRepository(db)

    @After fun close() = db.close()

    private fun line(description: String = "Test item") =
        InvoiceLine(invoiceId = 0, description = description, quantity = 2, unitPriceRial = 1000, taxRatePercent = 10)

    @Test fun crossCompanyCustomerRejectedWithoutPartialInsert() = runBlocking {
        val first = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val second = db.companyDao().insert(Company(purchaserId = "owner", name = "Beta", nationalId = "22222222222"))
        val foreignCustomer = db.customerDao().insert(Customer(companyId = second, name = "Foreign customer"))
        try {
            repository.saveDraft(first, foreignCustomer, "1", "1405/07/01", listOf(line()))
            throw AssertionError("Cross-company customer was accepted")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, db.invoiceDao().forCompany(first).size)
            assertEquals(0, db.invoiceDao().forCompany(second).size)
        }
    }

    @Test fun companyIsolationAndTenantScopedLines() = runBlocking {
        val first = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val second = db.companyDao().insert(Company(purchaserId = "owner", name = "Beta", nationalId = "22222222222"))
        val c1 = db.customerDao().insert(Customer(companyId = first, name = "Customer A"))
        val c2 = db.customerDao().insert(Customer(companyId = second, name = "Customer B"))
        val a = repository.saveDraft(first, c1, "10", "1405/07/01", listOf(line("A")))
        val b = repository.saveDraft(second, c2, "10", "1405/07/01", listOf(line("B")))
        assertEquals(1, repository.invoicesForCompany(first).size)
        assertEquals(1, repository.invoicesForCompany(second).size)
        assertEquals("A", repository.linesForInvoice(first, a).single().description)
        assertEquals("B", repository.linesForInvoice(second, b).single().description)
        try {
            repository.linesForInvoice(first, b)
            throw AssertionError("Cross-tenant read was accepted")
        } catch (expected: IllegalArgumentException) { /* expected */ }
    }

    @Test fun failedDraftUpdatePreservesExistingLines() = runBlocking {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        val id = repository.saveDraft(company, customer, "10", "1405/07/01", listOf(line("Original")))
        try {
            repository.saveDraft(company, customer, "11", "1405/07/01",
                listOf(line("Replacement"), line(description = "")), existingInvoiceId = id)
            throw AssertionError("Invalid row was accepted")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Original", repository.linesForInvoice(company, id).single().description)
            assertEquals("10", db.invoiceDao().byId(id)?.invoiceNumber)
        }
    }

    @Test fun negativeAndOverDiscountAreRejected() = runBlocking {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        for (invalid in listOf(line().copy(quantity = -1), line().copy(discountRial = 3000))) {
            try {
                repository.saveDraft(company, customer, "10", "1405/07/01", listOf(invalid))
                throw AssertionError("Invalid financial row was accepted")
            } catch (expected: IllegalArgumentException) {
                assertEquals(0, db.invoiceDao().forCompany(company).size)
            }
        }
    }

    @Test fun sqliteFailureInsideDraftUpdateRollsBackHeaderAndLines() = runBlocking {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        val id = repository.saveDraft(company, customer, "10", "1405/07/01", listOf(line("Original")))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_replacement BEFORE INSERT ON invoice_lines " +
            "WHEN NEW.description = 'Replacement' BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        try {
            repository.saveDraft(company, customer, "11", "1405/07/02",
                listOf(line("Replacement")), existingInvoiceId = id)
            throw AssertionError("Expected injected database failure")
        } catch (expected: android.database.SQLException) {
            assertEquals("10", db.invoiceDao().byId(id)?.invoiceNumber)
            assertEquals("1405/07/01", db.invoiceDao().byId(id)?.issueDate)
            assertEquals("Original", repository.linesForInvoice(company, id).single().description)
        }
    }

    @Test fun rejectsNumberThatNormalizesToWhitespace() = runBlocking {
        val company = db.companyDao().insert(Company(purchaserId = "owner", name = "Alpha", nationalId = "11111111111"))
        val customer = db.customerDao().insert(Customer(companyId = company, name = "Customer"))
        try {
            repository.saveDraft(company, customer, "   ", "1405/07/01", listOf(line()))
            throw AssertionError("Empty invoice number accepted")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, db.invoiceDao().forCompany(company).size)
        }
    }
}
