package ir.saba.invoice.domain

/** Input normalization only; this does not certify a government tax invoice. */
object InvoiceInput {
    fun digits(value: String): String = value.map { c ->
        when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            else -> c
        }
    }.joinToString("")

    fun wholeNumber(value: String, label: String): Long {
        val normalized = digits(value).trim()
        require(normalized.isNotEmpty() && normalized.all { it in '0'..'9' }) {
            "$label باید عدد صحیح و بدون علامت باشد."
        }
        return normalized.toLongOrNull() ?: throw IllegalArgumentException("$label بیش از حد بزرگ است.")
    }

    fun date(value: String): String {
        val parts = digits(value).trim().split('/')
        require(parts.size == 3 && parts.all { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }) {
            "تاریخ را به صورت سال/ماه/روز شمسی وارد کنید."
        }
        val year = parts[0].toIntOrNull() ?: 0
        val month = parts[1].toIntOrNull() ?: 0
        val day = parts[2].toIntOrNull() ?: 0
        require(year in 1300..1499 && month in 1..12) { "سال باید بین ۱۳۰۰ و ۱۴۹۹ و ماه بین ۱ و ۱۲ باشد." }
        // Jalali break interval 1210..1634; the accepted year range stays inside it.
        val leap = (((year - 1210 + 1) % 33) - 1) % 4 == 0
        val maxDay = when { month <= 6 -> 31; month <= 11 -> 30; leap -> 30; else -> 29 }
        require(day in 1..maxDay) { "روز واردشده برای این ماه معتبر نیست." }
        return "%04d/%02d/%02d".format(java.util.Locale.ROOT, year, month, day)
    }
}
