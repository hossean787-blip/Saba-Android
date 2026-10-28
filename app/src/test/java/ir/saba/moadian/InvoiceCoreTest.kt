package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class InvoiceCoreTest {
    private fun b(s: String) = BigDecimal(s)
    private val line = MoadianLine("1234567890123", "test", b("2"), b("1000"), b("100"), b("10"))

    @Test fun calculatesLine() { assertEquals(0, MoadianCalculator.calculate(line).payableRial.compareTo(b("2090"))) }
    @Test fun totalsLines() { assertEquals(0, MoadianCalculator.total(listOf(line, line)).payableRial.compareTo(b("4180"))) }
    @Test fun fractionalQuantity() {
        assertEquals(0, MoadianCalculator.calculate(line.copy(quantity=b("0.5"), discountRial=b("0"))).payableRial.compareTo(b("550")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectExcessDiscount() {
        MoadianCalculator.calculate(line.copy(discountRial=b("2001")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectEmptyInvoice() { MoadianCalculator.total(emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun rejectBadVat() {
        MoadianCalculator.calculate(line.copy(vatPercent=b("101")))
    }
    @Test fun validTransition() {
        assertEquals(MoadianSubmissionState.READY, MoadianSubmissionTransitions.advance(MoadianSubmissionState.DRAFT, MoadianSubmissionState.READY))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTransition() {
        MoadianSubmissionTransitions.advance(MoadianSubmissionState.DRAFT, MoadianSubmissionState.ACCEPTED)
    }
}
