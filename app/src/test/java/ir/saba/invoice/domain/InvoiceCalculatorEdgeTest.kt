package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceCalculatorEdgeTest {
    @Test fun roundsTaxToWholeRialUsingHalfUp() {
        val result = InvoiceCalculator.calculate(listOf(
            InvoiceLine(invoiceId = 1, description = "خدمت", quantity = 1, unitPriceRial = 5, taxRatePercent = 10)
        ))
        assertEquals(1L, result.taxAmountRial)
        assertEquals(6L, result.totalAmountRial)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsMultiplicationOverflow() {
        InvoiceCalculator.calculate(listOf(
            InvoiceLine(invoiceId = 1, description = "خدمت", quantity = Long.MAX_VALUE, unitPriceRial = 2, taxRatePercent = 0)
        ))
    }
}
