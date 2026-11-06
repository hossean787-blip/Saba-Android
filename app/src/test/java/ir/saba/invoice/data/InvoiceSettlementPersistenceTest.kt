package ir.saba.invoice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.saba.invoice.domain.InvoiceSettlement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoiceSettlementPersistenceTest {
    private fun items() = listOf(
        InvoiceLine(invoiceId = 0, description = "خدمت", quantity = 1,
            unitPriceRial = 1000, discountRial = 0, taxRatePercent = 10)
    )

    @Test fun mixedSettlementSurvivesDraftEditAndFinalizationWithoutExtraDebit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val company = db.companyDao().insert(Company(purchaserId = "owner", name = "مودی", nationalId = "1"))
            val buyer = db.customerDao().insert(Customer(companyId = company, name = "مشتری"))
            val repository = InvoiceRepository(db)
            val id = repository.saveDraft(company, buyer, "31", "1405/08/16", items(),
                serverPromoConfirmed = true, settlementMode = "MIXED", cashPaidRial = 400)
            var invoice = repository.detailsForCompany(company, id).invoice
            assertEquals("MIXED", invoice.settlementMode)
            assertEquals(400L, invoice.cashPaidRial)
            assertEquals(700L, InvoiceSettlement.amounts(invoice).creditRial)
            assertEquals(id, db.quotaDao().useForInvoice(id)?.invoiceId)

            repository.saveDraft(company, buyer, "31", "1405/08/16", items(), id,
                settlementMode = "CREDIT", cashPaidRial = 0)
            invoice = repository.detailsForCompany(company, id).invoice
            assertEquals("CREDIT", invoice.settlementMode)
            assertEquals(1100L, InvoiceSettlement.amounts(invoice).creditRial)
            repository.finalizeDraft(company, id)
            assertEquals("FINALIZED", db.invoiceDao().byId(id)!!.status)
            assertEquals("CREDIT", db.invoiceDao().byId(id)!!.settlementMode)
            assertEquals(0L, db.invoiceDao().byId(id)!!.cashPaidRial)
            assertEquals(id, db.quotaDao().useForInvoice(id)?.invoiceId)
        } finally { db.close() }
    }

    @Test fun invalidEditPreservesOriginalInvoiceAndRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val company = db.companyDao().insert(Company(purchaserId = "owner", name = "مودی", nationalId = "2"))
            val buyer = db.customerDao().insert(Customer(companyId = company, name = "خریدار"))
            val repository = InvoiceRepository(db)
            val id = repository.saveDraft(company, buyer, "44", "1405/08/16", items(),
                serverPromoConfirmed = true)
            try {
                repository.saveDraft(company,buyer,"44","1405/08/16",items(),id,
                    settlementMode="MIXED",cashPaidRial=1500)
                fail("Expected invalid cash greater than invoice total")
            } catch (_: IllegalArgumentException) { }
            val saved = repository.detailsForCompany(company, id)
            assertEquals("CASH", saved.invoice.settlementMode)
            assertNull(saved.invoice.cashPaidRial)
            assertEquals(1, saved.lines.size)
            assertEquals(1100L, saved.invoice.totalAmountRial)
        } finally { db.close() }
    }
}
