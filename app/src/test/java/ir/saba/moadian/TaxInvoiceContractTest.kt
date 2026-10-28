package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class TaxInvoiceContractTest {
    private fun sample() = TaxInvoiceContract(
        TaxInvoiceHeader("ABC123ABCDE01234567890", "ABC123", "12345678901", "12345678901234", 1710000000000L),
        listOf(TaxInvoiceItem("100", "item", BigDecimal.ONE, BigDecimal("1000"), BigDecimal.ZERO, BigDecimal("10")))
    )
    @Test fun validSubsetAccepted() { assertSame(sample().items[0], TaxInvoiceValidation.validate(sample()).items[0]) }
    @Test(expected = IllegalArgumentException::class) fun rejectWrongFiscalMemory() {
        TaxInvoiceValidation.validate(sample().copy(header=sample().header.copy(fiscalMemoryId="ZZZ999")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativeQuantity() {
        TaxInvoiceValidation.validate(sample().copy(items=listOf(sample().items[0].copy(quantity=BigDecimal("-1")))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectNonHttps() { GatewayConfig("http://tp.tax.gov.ir") }
    @Test(expected = IllegalArgumentException::class) fun rejectForeignHost() { GatewayConfig("https://example.com") }
}
