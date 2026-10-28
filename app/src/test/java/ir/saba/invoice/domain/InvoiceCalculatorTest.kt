package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceCalculatorTest {
    @Test fun calculatesNetTaxAndTotalInRial() {
        val result = InvoiceCalculator.calculate(listOf(
            InvoiceLine(invoiceId = 1, description = "خدمت", quantity = 2, unitPriceRial = 1000, discountRial = 200, taxRatePercent = 10)
        ))
        assertEquals(1800L, result.netAmountRial)
        assertEquals(180L, result.taxAmountRial)
        assertEquals(1980L, result.totalAmountRial)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroQuantity() {
        InvoiceCalculator.calculate(listOf(
            InvoiceLine(invoiceId = 1, description = "خدمت", quantity = 0, unitPriceRial = 1000, taxRatePercent = 10)
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiscountAboveLineAmount() {
        InvoiceCalculator.calculate(listOf(
            InvoiceLine(invoiceId = 1, description = "خدمت", quantity = 1, unitPriceRial = 1000, discountRial = 1001, taxRatePercent = 10)
        ))
    }
}
