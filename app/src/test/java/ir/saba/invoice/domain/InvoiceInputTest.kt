package ir.saba.invoice.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceInputTest {
    @Test fun acceptsPersianAndArabicDigitsWithoutChangingValue() {
        assertEquals(123456L, InvoiceInput.wholeNumber("۱۲٣۴۵۶", "مبلغ"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFractionRatherThanSilentlyRemovingTheSeparator() {
        InvoiceInput.wholeNumber("۱٫۵", "تعداد")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAmountBeyondLong() {
        InvoiceInput.wholeNumber("9223372036854775808", "مبلغ")
    }

    @Test fun normalizesAnActualJalaliDate() {
        assertEquals("1405/07/02", InvoiceInput.date("۱۴۰۵/۷/۲"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleMonthDay() { InvoiceInput.date("1405/07/31") }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonLeapEsfand() { InvoiceInput.date("1400/12/30") }

    @Test fun acceptsLeapEsfand() { assertEquals("1399/12/30", InvoiceInput.date("۱۳۹۹/۱۲/۳۰")) }
}
