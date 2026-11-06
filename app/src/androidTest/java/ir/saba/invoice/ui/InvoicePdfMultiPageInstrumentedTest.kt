package ir.saba.invoice.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.saba.invoice.data.Company
import ir.saba.invoice.data.Customer
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.InvoiceLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android PdfDocument/PdfRenderer instrumentation; Robolectric has no native multi-page writer. */
@RunWith(AndroidJUnit4::class)
class InvoicePdfMultiPageInstrumentedTest {
    @Test fun approvedA4TemplateProducesLegibleFirstAndFinalPages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val company = Company(id = 100, purchaserId = "owner", name = "فروشنده", nationalId = "123")
        val customer = Customer(id = 200, companyId = 100, name = "خریدار")
        val item = InvoiceLine(
            invoiceId = 300, itemCode = "2710000138624", description = "خدمت",
            quantity = 1, unitPriceRial = 1000, taxRatePercent = 10
        )
        for (count in listOf(15, 29)) {
            val invoice = Invoice(
                id = 300, companyId = 100, customerId = 200, invoiceNumber = "101",
                issueDate = "1405/08/15", totalAmountRial = count * 1100L,
                taxAmountRial = count * 100L
            )
            val bytes = SuraInvoicePdf.generate(
                company, customer, invoice, List(count) { item },
                "پیش‌فرض", logo = true, stamp = false, qr = false
            )
            assertTrue(bytes.size > 1000)
            val tmp = File.createTempFile("sura-page-regression-", ".pdf", context.cacheDir)
            try {
                tmp.writeBytes(bytes)
                ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        assertEquals((count + 13) / 14, renderer.pageCount)
                        for (index in listOf(0, renderer.pageCount - 1)) {
                            renderer.openPage(index).use { page ->
                                assertEquals(595, page.width)
                                assertEquals(842, page.height)
                                val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    assertNotEquals(Color.WHITE, bitmap.getPixel(40, 40))
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } finally {
                tmp.delete()
            }
        }
    }
}
