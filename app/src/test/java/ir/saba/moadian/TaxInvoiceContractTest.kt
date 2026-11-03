package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class TaxInvoiceContractTest {
    private fun sample() = TaxInvoiceContract(
        TaxInvoiceHeader("A1121604C220002F095011", "A11216", "12345678901", "12345678901234", 19490L * 86400000),
        listOf(TaxInvoiceItem("1234567890123", "item", BigDecimal.ONE, BigDecimal("1000"), BigDecimal.ZERO, BigDecimal("10")))
    )
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidTaxIdCheckDigit() {
        TaxInvoiceValidation.validate(sample().copy(header=sample().header.copy(taxId="A1121604C220002F095010")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonNumericProductCode() {
        TaxInvoiceValidation.validate(sample().copy(items=listOf(sample().items[0].copy(productServiceId="ABCDEFGHIJKLM"))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTaxIdFromDifferentIssueDay() {
        TaxInvoiceValidation.validate(sample().copy(header=sample().header.copy(issuedAtEpochMillis=19491L * 86400000)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnlistedMeasurementUnit() {
        TaxInvoiceValidation.validate(sample().copy(items=listOf(sample().items[0].copy(unitOfMeasureCode="999999"))))
    }
    @Test fun acceptsListedOptionalUnitAndRetainsLeadingProductZero() {
        val value = sample().copy(items=listOf(sample().items[0].copy(productServiceId="0123456789012", unitOfMeasureCode="1627")))
        assertEquals("0123456789012", TaxInvoiceValidation.validate(value).items[0].productServiceId)
        assertEquals(102, MoadianMeasurementUnits.size)
        assertEquals("نفر-ماه", MoadianMeasurementUnits.nameOf("16134"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroUnitPriceForSupportedSalesPattern() {
        TaxInvoiceValidation.validate(sample().copy(items=listOf(sample().items[0].copy(unitPriceRial=BigDecimal.ZERO))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsHeaderSumBeyondDocumentFieldCapacity() {
        val item = sample().items[0].copy(unitPriceRial=BigDecimal("999999999999999999"), discountRial=BigDecimal.ZERO, vatPercent=BigDecimal.ZERO)
        TaxInvoiceValidation.validate(sample().copy(items=listOf(item,item)))
    }
    @Test fun validSubsetAccepted() { assertEquals(sample().items[0], TaxInvoiceValidation.validate(sample()).items[0]) }
    @Test(expected = IllegalArgumentException::class) fun rejectWrongFiscalMemory() {
        TaxInvoiceValidation.validate(sample().copy(header=sample().header.copy(fiscalMemoryId="ZZZ999")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativeQuantity() {
        TaxInvoiceValidation.validate(sample().copy(items=listOf(sample().items[0].copy(quantity=BigDecimal("-1")))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectNonHttps() { GatewayConfig("http://tp.tax.gov.ir") }
    @Test(expected = IllegalArgumentException::class) fun rejectForeignHost() { GatewayConfig("https://example.com") }
}
