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

    @Test fun validFractionalTaxRateIsPreservedWithoutGuessingTenPercent() {
        val result = InvoiceIdentifierPrefill.from(identifier("1234567890123", "۹٫۵"))
        assertEquals("9.5", result.vatRatePercent)
        assertFalse(result.requiresVatReview)
    }

    @Test fun missingOrInvalidVatNeverDefaultsToTenPercent() {
        listOf("", "نامشخص", "110", "9.123").forEach { source ->
            val result = InvoiceIdentifierPrefill.from(identifier("1234567890123", source))
            assertEquals("", result.vatRatePercent)
            assertTrue(result.requiresVatReview)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFourteenDigitIdentifierBeforeInvoiceIsSaved() {
        InvoiceIdentifierPrefill.from(identifier("12345678901234", "10"))
    }

    @Test fun preservesLeadingZeroInSendableThirteenDigitIdentifier() {
        val selected = InvoiceIdentifierPrefill.from(identifier("۰۰۱۲۳۴۵۶۷۸۹۰۱", "10"))
        assertEquals("0012345678901", selected.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericIdentifiers() {
        InvoiceIdentifierPrefill.from(identifier("not-a-tax-code", "10"))
    }
}
