package ir.saba.invoice.domain

import org.junit.Assert.*
import org.junit.Test

class MoadianFieldValidationTest {
    @Test fun normalizesPersianAndArabicDigitsWithoutLosingLeadingZero() {
        assertEquals("09123456789", MoadianFieldValidation.digits("۰۹۱۲۳۴۵۶۷۸۹"))
        assertNull(MoadianFieldValidation.mobile("٠٩١٢٣٤٥٦٧٨٩"))
        assertNull(MoadianFieldValidation.postalCode("۱۲۳۴۵۶۷۸۹۰"))
    }
    @Test fun rejectsInvalidLengthsAndCollidingSeries() {
        assertNotNull(MoadianFieldValidation.mobile("9123456789"))
        assertNotNull(MoadianFieldValidation.postalCode("123"))
        assertNull(MoadianFieldValidation.serial("", "serial"))
        assertNotNull(MoadianFieldValidation.serial("123", "serial"))
        assertNotNull(MoadianFieldValidation.serialPair("۱۰۰۰۰۰۰۰۰۱", "1000000001"))
        assertNull(MoadianFieldValidation.serialPair("1000000001", "2000000001"))
    }
    @Test fun doesNotRequirePrefixAOrClaimVerification() {
        assertNull(MoadianFieldValidation.fiscalMemoryId("B12345"))
        assertNotNull(MoadianFieldValidation.fiscalMemoryId("A1234"))
    }
    @Test fun typeSpecificFormIdentityFormats() {
        assertTrue(MoadianFieldValidation.identity("شخص حقوقی", "12345678901", "12345678901").isEmpty())
        assertTrue(MoadianFieldValidation.identity("شخص حقیقی", "12345678900001", "12345678900001").isEmpty())
        assertTrue(MoadianFieldValidation.identity("اتباع غیرایرانی", "123456789012", "12345678901201").isEmpty())
        assertFalse(MoadianFieldValidation.identity("شخص حقوقی", "12345678901234", "12345678901234").isEmpty())
    }
    @Test fun countySelectionBelongsToProvince() {
        assertEquals(31, IranDivisions.counties.size)
        assertTrue(IranDivisions.counties.getValue("مرکزی").contains("اراک"))
        assertFalse(IranDivisions.counties.getValue("تهران").contains("اراک"))
        assertTrue(IranDivisions.counties.values.all { it.isNotEmpty() })
    }
}
