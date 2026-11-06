package ir.saba.invoice.domain

import ir.saba.invoice.data.Invoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class InvoiceSettlementTest {
    private fun invoice(mode: String = "CASH", cash: Long? = null, total: Long = 1100L) =
        Invoice(companyId = 1, customerId = 2, invoiceNumber = "42",
            issueDate = "1405/08/16", totalAmountRial = total, taxAmountRial = 100,
            settlementMode = mode, cashPaidRial = cash)

    @Test fun legacyAndNewCashInvoicesRequireFullCashButKeepNullableLegacyEncoding() {
        assertNull(InvoiceSettlement.cashFromUserInput("CASH", 1100, ""))
        val old = InvoiceSettlement.amounts(invoice())
        assertEquals(1100, old.cashRial)
        assertEquals(0, old.creditRial)
        assertEquals(0, InvoiceSettlement.amounts(invoice(total = 0)).cashRial)
        assertEquals(1100, InvoiceSettlement.amounts(invoice(cash = 1100)).cashRial)
    }

    @Test fun creditAndMixedInvoicesRetainExactRialBalance() {
        assertEquals(0L, InvoiceSettlement.cashFromUserInput("CREDIT", 1100, ""))
        assertEquals(0, InvoiceSettlement.amounts(invoice("CREDIT", 0)).cashRial)
        assertEquals(1100, InvoiceSettlement.amounts(invoice("CREDIT", 0)).creditRial)
        val cash = InvoiceSettlement.cashFromUserInput("MIXED", 1100, "۴۰۰")
        assertEquals(400L, cash)
        assertEquals(700, InvoiceSettlement.amounts(invoice("MIXED", cash)).creditRial)
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid invoice settlement")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsOverpaymentsNegativeOrMalformedCashAndMissingMixedAmounts() {
        rejects { InvoiceSettlement.amounts("MIXED", 0L, 100) }
        rejects { InvoiceSettlement.amounts("MIXED", 100L, 100) }
        rejects { InvoiceSettlement.amounts("MIXED", 101L, 100) }
        rejects { InvoiceSettlement.amounts("MIXED", null, 100) }
        rejects { InvoiceSettlement.amounts("CREDIT", null, 100) }
        rejects { InvoiceSettlement.amounts("CASH", 5, 100) }
        rejects { InvoiceSettlement.cashFromUserInput("MIXED", 100, "invalid") }
        rejects { InvoiceSettlement.cashFromUserInput("MIXED", 100, "-1") }
        rejects { InvoiceSettlement.cashFromUserInput("MIXED", 100, "100") }
        rejects { InvoiceSettlement.amounts("unknown", null, 100) }
    }

    @Test fun officialCashMapperCannotMisrepresentCreditAsCash() {
        InvoiceSettlement.requireSupportedForOfficialCash(invoice())
        rejects { InvoiceSettlement.requireSupportedForOfficialCash(invoice("CREDIT", 0)) }
        rejects { InvoiceSettlement.requireSupportedForOfficialCash(invoice("MIXED", 250)) }
    }
}
