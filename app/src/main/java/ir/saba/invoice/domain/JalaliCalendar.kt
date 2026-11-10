package ir.saba.invoice.domain

import java.time.LocalDate

/**
 * Jalaali (Solar Hijri) calendar arithmetic in the range supported by the invoice form:
 * 1300..1499. Uses the Borkowski leap-break algorithm, also used by the MIT-licensed
 * jalaali-js reference implementation (https://github.com/jalaali/jalaali-js).
 *
 * Unlike a continuous 2820-year cycle approximation, this algorithm follows the
 * leap-year breaks necessary for dates such as 1399/12/30 and 1403/12/30.
 */
object JalaliCalendar {
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private data class YearStart(val gregorianYear: Int, val marchDay: Int, val leap: Boolean)

    private fun startOfYear(year: Int): YearStart {
        require(year in 1300..1499) { "سال شمسی خارج از محدوده صدور صورتحساب است." }
        val gregorianYear = year + 621
        var leapJ = -14
        var previous = breaks[0]
        var jump = 0
        for (index in 1 until breaks.size) {
            val next = breaks[index]
            jump = next - previous
            if (year < next) break
            leapJ += (jump / 33) * 8 + ((jump % 33) / 4)
            previous = next
        }
        var n = year - previous
        leapJ += (n / 33) * 8 + ((n % 33 + 3) / 4)
        if (jump % 33 == 4 && jump - n == 4) leapJ++
        val leapGregorian = gregorianYear / 4 -
            (((gregorianYear / 100 + 1) * 3) / 4) - 150
        val marchDay = 20 + leapJ - leapGregorian
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        val yearsSinceLeap = ((n + 1) % 33 - 1) % 4
        return YearStart(gregorianYear, marchDay, yearsSinceLeap == 0)
    }

    fun isLeapYear(year: Int): Boolean = startOfYear(year).leap

    /** Conversion used by the tax payload date check as well as local input validation. */
    fun toGregorian(year: Int, month: Int, day: Int): LocalDate {
        val start = startOfYear(year)
        require(month in 1..12) { "ماه شمسی معتبر نیست." }
        val maxDay = when {
            month <= 6 -> 31
            month <= 11 -> 30
            start.leap -> 30
            else -> 29
        }
        require(day in 1..maxDay) { "روز واردشده برای این ماه معتبر نیست." }
        val elapsed = (if (month <= 6) (month - 1) * 31 else 186 + (month - 7) * 30) + day - 1
        return LocalDate.of(start.gregorianYear, 3, start.marchDay)
            .plusDays(elapsed.toLong())
    }

    /** Gregorian local date to normalized Jalali date, using the same leap-year rules. */
    fun fromGregorian(date: LocalDate): String {
        val candidate = date.year - 621
        val year = if (date.isBefore(toGregorian(candidate, 1, 1))) candidate - 1 else candidate
        val dayIndex = java.time.temporal.ChronoUnit.DAYS
            .between(toGregorian(year, 1, 1), date).toInt()
        val month = if (dayIndex < 186) dayIndex / 31 + 1 else (dayIndex - 186) / 30 + 7
        val day = if (dayIndex < 186) dayIndex % 31 + 1 else (dayIndex - 186) % 30 + 1
        return "%04d/%02d/%02d".format(java.util.Locale.ROOT, year, month, day)
    }

    fun toGregorian(jalaliDate: String): LocalDate {
        val parts = InvoiceInput.date(jalaliDate).split('/').map(String::toInt)
        return toGregorian(parts[0], parts[1], parts[2])
    }
}
