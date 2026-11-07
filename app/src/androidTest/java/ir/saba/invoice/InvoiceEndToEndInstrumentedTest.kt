package ir.saba.invoice

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.saba.invoice.data.Company
import ir.saba.invoice.data.Customer
import ir.saba.invoice.data.InvoiceLine
import ir.saba.invoice.data.InvoiceRepository
import ir.saba.invoice.data.MoadianDetails
import ir.saba.invoice.data.QuotaRepository
import ir.saba.invoice.data.SabaDatabase
import ir.saba.invoice.domain.InvoiceCalculator
import ir.saba.invoice.domain.InvoiceLineAmounts
import ir.saba.invoice.security.MoadianInvoiceMapper
import ir.saba.invoice.ui.SuraInvoicePdf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Real Android SQLite / PDF / official-JSON contract smoke test.
 * Completely synthetic identities and an in-memory database. Never posts to tax servers,
 * never uses a private key, and never debits a real customer's credit.
 */
@RunWith(AndroidJUnit4::class)
class InvoiceEndToEndInstrumentedTest {
    @Test
    fun locallyFinalizedInvoiceRendersPdfAndBuildsExactOfflineTaxPayload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SabaDatabase::class.java).build()
        try {
            val owner = "11111111-1111-4111-8111-111111111111"
            val sellerId = db.companyDao().insert(
                Company(purchaserId = owner, name = "فروشنده آزمون", nationalId = "12345678901",
                    economicId = "12345678901")
            )
            val buyerId = db.customerDao().insert(
                Customer(companyId = sellerId, name = "خریدار آزمون",
                    nationalId = "1234567890", economicId = "12345678901234")
            )
            db.moadianDetailsDao().upsert(
                MoadianDetails(companyId = sellerId, fiscalMemoryId = "A11216")
            )
            val quota = QuotaRepository(db)
            quota.initializeWallet(owner, 3)
            quota.transferFromMaster(owner, sellerId, 2, "android-device-smoke")

            val lines = listOf(
                InvoiceLine(invoiceId = 0, itemCode = "2710000138624",
                    description = "کالای آزمایشی وزنی", quantity = 1,
                    quantityExact = "0.5", vatRateExact = "10",
                    unitPriceRial = 1000, taxRatePercent = 10, unitCode = "164"),
                InvoiceLine(invoiceId = 0, itemCode = "2710000138624",
                    description = "کالای آزمایشی عددی", quantity = 1,
                    quantityExact = "1", vatRateExact = "10",
                    unitPriceRial = 1000, taxRatePercent = 10, unitCode = "1627")
            )
            val totals = InvoiceCalculator.calculate(lines)
            assertEquals(1650L, totals.totalAmountRial)
            assertEquals(150L, totals.taxAmountRial)
            assertEquals("0.5", InvoiceLineAmounts.quantityText(lines[0]))
            val repository = InvoiceRepository(db)
            val id = repository.saveDraft(sellerId, buyerId, "12", "1405/08/12", lines)
            val storedDraft = repository.detailsForCompany(sellerId, id)
            assertEquals("DRAFT", storedDraft.invoice.status)
            assertEquals(1L, db.quotaDao().companyQuota(sellerId)!!.available)
            assertEquals("164", storedDraft.lines[0].unitCode)
            assertEquals("1627", storedDraft.lines[1].unitCode)

            // Drafts cannot be sent, even if their local amounts and identifiers are valid.
            val seller = db.companyDao().byId(sellerId)!!
            val buyer = db.customerDao().byId(buyerId)!!
            val details = db.moadianDetailsDao().byCompany(sellerId)!!
            val issued = "2026-11-03T12:00:00+03:30"
            val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
            var draftRejected = false
            try {
                MoadianInvoiceMapper.map(seller, buyer, null, details,
                    storedDraft.invoice, storedDraft.lines, issued, "", now)
            } catch (_: IllegalArgumentException) {
                draftRejected = true
            }
            assertTrue("Official mapper must reject non-finalized drafts", draftRejected)

            repository.finalizeDraft(sellerId, id)
            val saved = repository.detailsForCompany(sellerId, id)
            assertEquals("FINALIZED", saved.invoice.status)
            assertEquals(1L, db.quotaDao().companyQuota(sellerId)!!.available)
            assertEquals(2, saved.lines.size)
            assertEquals(totals.totalAmountRial, saved.invoice.totalAmountRial)
            assertEquals(totals.taxAmountRial, saved.invoice.taxAmountRial)

            // The mapper produces bytes only. There is no network call in this test.
            val payload = JSONObject(MoadianInvoiceMapper.map(
                seller, buyer, null, details, saved.invoice, saved.lines, issued, "", now
            ))
            val header = payload.getJSONObject("header")
            val body = payload.getJSONArray("body")
            assertEquals(1, header.getInt("inty"))
            assertEquals(1, header.getInt("inp"))
            assertEquals(1, header.getInt("setm"))
            assertEquals(1650L, header.getLong("tbill"))
            assertEquals(150L, header.getLong("tvam"))
            assertEquals(22, header.getString("taxid").length)
            assertEquals(2, body.length())
            assertEquals("164", body.getJSONObject(0).getString("mu"))
            assertEquals("1627", body.getJSONObject(1).getString("mu"))
            assertEquals(50L, body.getJSONObject(0).getLong("vam"))
            assertEquals(100L, body.getJSONObject(1).getLong("vam"))
            assertFalse("A local test never stores a tax-system tracking number",
                saved.invoice.trackingCode != null)

            val pdf = SuraInvoicePdf.generate(seller, buyer, saved.invoice,
                saved.lines, "پیش‌فرض", logo = true, stamp = false, qr = true)
            assertTrue(pdf.size > 1000)
            val file = File.createTempFile("sura-offline-invoice-", ".pdf", context.cacheDir)
            try {
                file.writeBytes(pdf)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        assertEquals(1, renderer.pageCount)
                        renderer.openPage(0).use { page ->
                            assertEquals(595, page.width)
                            assertEquals(842, page.height)
                            val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertNotEquals(Color.WHITE, bitmap.getPixel(40, 40))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } finally {
                file.delete()
            }
        } finally {
            db.close()
        }
    }
}
