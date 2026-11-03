package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class InvoiceCoreTest {
    private fun b(s: String) = BigDecimal(s)
    private val line = MoadianLine("1234567890123", "test", b("2"), b("1000"), b("100"), b("10"))

    @Test fun taxPreparationTruncatesGrossRatherThanRoundingUp() {
        val result = MoadianCalculator.calculate(line.copy(quantity=b("1"), unitPriceRial=b("10.99"), discountRial=b("0"), vatPercent=b("0")))
        assertEquals(0, result.grossRial.compareTo(b("10")))
    }
    @Test fun taxPreparationTruncatesVatAtEachLine() {
        val row = line.copy(quantity=b("1"), unitPriceRial=b("19"), discountRial=b("0"))
        assertEquals(0, MoadianCalculator.calculate(row).vatRial.compareTo(b("1")))
        assertEquals(0, MoadianCalculator.total(listOf(row,row)).vatRial.compareTo(b("2")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsQuantityBeyondDocumentPrecision() {
        MoadianCalculator.calculate(line.copy(quantity=b("1.000000001")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsFractionalDiscountField() {
        MoadianCalculator.calculate(line.copy(discountRial=b("1.5")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnrepresentableHeaderTotalEvenWhenEachRowFits() {
        val row = line.copy(quantity=b("1"), unitPriceRial=b("999999999999999999"), discountRial=b("0"), vatPercent=b("0"))
        MoadianCalculator.total(listOf(row,row))
    }
    @Test fun calculationsExposeRuleVersionAndReconcile() {
        val result = MoadianCalculator.total(listOf(line,line))
        assertEquals("RC_IITP.IS_V7.9-IRR-SALES", result.ruleVersion)
        assertEquals(0, result.grossRial.subtract(result.discountRial).compareTo(result.netRial))
        assertEquals(0, result.netRial.add(result.vatRial).compareTo(result.payableRial))
    }
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
