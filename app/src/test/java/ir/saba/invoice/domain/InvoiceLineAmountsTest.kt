package ir.saba.invoice.domain

import ir.saba.invoice.data.InvoiceLine
import org.junit.Assert.*
import org.junit.Test

class InvoiceLineAmountsTest {
    @Test fun fractionsAndPerItemMeasurementCodeSurviveValidation() {
        val row = InvoiceLineAmounts.fromUserInput(
            "2710000138624", "خدمت اندازه‌گیری شده", "۰٫۵",
            1000, 0, "۹٫۵", "164"
        )
        assertEquals("0.5", row.quantityExact)
        assertEquals("9.5", row.vatRateExact)
        assertEquals("164", row.unitCode)
        assertEquals("0.5", InvoiceLineAmounts.quantityText(row))
        val result = InvoiceCalculator.calculate(listOf(row))
        assertEquals(500L, result.netAmountRial)
        assertEquals(47L, result.taxAmountRial)
        assertEquals(547L, result.totalAmountRial)
    }

    @Test fun newIntegerRowsUseOfficialPerLineTruncationButLegacyAmountsStayUnchanged() {
        val legacy = InvoiceLine(
            invoiceId = 1, description = "قدیمی", quantity = 1,
            unitPriceRial = 5, taxRatePercent = 10
        )
        assertEquals(1L, InvoiceCalculator.calculate(listOf(legacy)).taxAmountRial)
        val current = InvoiceLineAmounts.fromUserInput(
            "2710000138624", "جدید", "۱", 5, 0, "۱۰", "1627"
        )
        assertEquals("1", current.quantityExact)
        assertEquals("10", current.vatRateExact)
        assertEquals(0L, InvoiceCalculator.calculate(listOf(current)).taxAmountRial)
    }

    @Test fun rejectsBadUnitAndPrecision() {
        try {
            InvoiceLineAmounts.fromUserInput("id", "test", "1.000000001", 10, 0, "10", "1627")
            fail("Excess precision must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            InvoiceLineAmounts.fromUserInput("id", "test", "1", 10, 0, "10", "invalid")
            fail("Unknown unit must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            InvoiceLineAmounts.taxPercent("9.123")
            fail("Fractional VAT is bounded at two decimals")
        } catch (_: IllegalArgumentException) { }
    }
}
