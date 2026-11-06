package ir.saba.invoice.ui

import ir.saba.invoice.data.Company
import ir.saba.invoice.data.Customer
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.InvoiceLine
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvoicePdfValidationTest {
    private val company = Company(id = 1, purchaserId = "owner", name = "فروشنده", nationalId = "1")
    private val customer = Customer(id = 2, companyId = 1, name = "خریدار")
    private val invoice = Invoice(id = 3, companyId = 1, customerId = 2, invoiceNumber = "1",
        issueDate = "1405/08/12", totalAmountRial = 110, taxAmountRial = 10)
    private val line = InvoiceLine(invoiceId = 3, description = "کالا", quantity = 1,
        unitPriceRial = 100, taxRatePercent = 10)

    private fun rejection(expectedMessage: String, invoice: Invoice = this.invoice, lines: List<InvoiceLine> = listOf(line)) {
        try {
            SuraInvoicePdf.generate(company, customer, invoice, lines, "ساده", false, false, false)
            fail("Expected invalid PDF rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expectedMessage))
        }
    }

    @Test fun rejectsStoredTotalThatDisagreesWithLines() {
        rejection("جمع", invoice.copy(totalAmountRial = 111))
    }

    @Test fun rejectsStoredTaxThatDisagreesWithLines() {
        rejection("جمع", invoice.copy(taxAmountRial = 11))
    }

    @Test fun rejectsInvalidLinesBeforeOpeningPdfPage() {
        rejection("تعداد", lines = listOf(line.copy(quantity = 0)))
    }

    @Test fun fifteenAndTwentyNineRowsGenerateMultiPagePdfWithoutChangingTotals() {
        for (count in listOf(15, 29)) {
            val valid = invoice.copy(
                totalAmountRial = count * 110L, taxAmountRial = count * 10L
            )
            val pdf = SuraInvoicePdf.generate(
                company, customer, valid, List(count) { line },
                "ساده", false, false, false
            )
            assertTrue(pdf.size > 500)
            assertTrue(pdf.copyOfRange(0, 5).toString(Charsets.ISO_8859_1).startsWith("%PDF-"))
        }
    }

    @Test fun moreThanFiveHundredRowsIsBoundedBeforeAllocatingPdf() {
        rejection("۵۰۰", invoice.copy(totalAmountRial = 501 * 110, taxAmountRial = 501 * 10), List(501) { line })
    }
}
