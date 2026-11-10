package ir.saba.invoice.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliCalendarTest {
    @Test fun astronomicalBreakYearsMatchKnownLeapBoundaries() {
        assertTrue(JalaliCalendar.isLeapYear(1395))
        assertTrue(JalaliCalendar.isLeapYear(1399))
        assertTrue(JalaliCalendar.isLeapYear(1403))
        assertFalse(JalaliCalendar.isLeapYear(1400))
        assertFalse(JalaliCalendar.isLeapYear(1404))
        assertFalse(JalaliCalendar.isLeapYear(1405))
    }

    @Test fun exactGregorianDatesAroundNewYearRemainConsistent() {
        assertEquals(LocalDate.of(2020, 3, 20), JalaliCalendar.toGregorian("1399/01/01"))
        assertEquals(LocalDate.of(2021, 3, 20), JalaliCalendar.toGregorian("1399/12/30"))
        assertEquals(LocalDate.of(2021, 3, 21), JalaliCalendar.toGregorian("1400/01/01"))
        assertEquals(LocalDate.of(2024, 3, 20), JalaliCalendar.toGregorian("1403/01/01"))
        assertEquals(LocalDate.of(2025, 3, 20), JalaliCalendar.toGregorian("1403/12/30"))
        assertEquals(LocalDate.of(2025, 3, 21), JalaliCalendar.toGregorian("1404/01/01"))
        assertEquals(LocalDate.of(2026, 3, 20), JalaliCalendar.toGregorian("1404/12/29"))
        assertEquals(LocalDate.of(2026, 3, 21), JalaliCalendar.toGregorian("1405/01/01"))
        assertEquals(LocalDate.of(2026, 11, 3), JalaliCalendar.toGregorian("1405/08/12"))
    }

    @Test fun gregorianToJalaliPrefillAcrossNowruzAndLeapBoundaries() {
        val dates = listOf(
            LocalDate.of(2020, 3, 20) to "1399/01/01",
            LocalDate.of(2021, 3, 20) to "1399/12/30",
            LocalDate.of(2021, 3, 21) to "1400/01/01",
            LocalDate.of(2025, 3, 20) to "1403/12/30",
            LocalDate.of(2026, 3, 20) to "1404/12/29",
            LocalDate.of(2026, 3, 21) to "1405/01/01",
            LocalDate.of(2026, 11, 10) to "1405/08/19"
        )
        dates.forEach { (gregorian, jalali) ->
            assertEquals(jalali, JalaliCalendar.fromGregorian(gregorian))
            assertEquals(gregorian, JalaliCalendar.toGregorian(JalaliCalendar.fromGregorian(gregorian)))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun neverRollsInvalidEsfand30IntoNextYear() {
        JalaliCalendar.toGregorian(1404, 12, 30)
    }

    @Test fun issueDateNormalizationAndMapperConversionUseIdenticalCalendar() {
        assertEquals(
            LocalDate.of(2025, 3, 20),
            JalaliCalendar.toGregorian(InvoiceInput.date("۱۴۰۳/۱۲/۳۰"))
        )
    }
}
