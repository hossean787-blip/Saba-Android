package ir.saba.invoice.domain

import ir.saba.invoice.data.identifiers.SuraIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceIdentifierPrefillTest {
    private fun identifier(code: String, vat: String) =
        SuraIdentifier(code, "کالای تستی", vat, "", "کالا", "", "ورود اکسل")

    @Test fun persianCodeAndIntegerVatPopulateLineExactly() {
        val selected = InvoiceIdentifierPrefill.from(identifier("۱۲۳۴۵۶۷۸۹۰۱۲۳", "۹"))
        assertEquals("1234567890123", selected.code)
        assertEquals("کالای تستی", selected.description)
        assertEquals("9", selected.vatRatePercent)
        assertFalse(selected.requiresVatReview)
    }

    @Test fun missingOrFractionalTaxRateNeverDefaultsToTenPercent() {
        listOf("", "۹٫۵", "نامشخص", "110").forEach { source ->
            val result = InvoiceIdentifierPrefill.from(identifier("1234567890123", source))
            assertEquals("", result.vatRatePercent)
            assertTrue(result.requiresVatReview)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericIdentifiers() {
        InvoiceIdentifierPrefill.from(identifier("not-a-tax-code", "10"))
    }
}
