package ir.saba.moadian

/**
 * Offline tax-ID arithmetic, checked against the supplied Java/.NET SDK algorithm and
 * RC_TICS.IS_v1.6 invoice example. This does not confirm taxpayer registration or uniqueness.
 * The caller must persist the serial and issued-at timestamp; retries reuse both.
 */
object TaxIdGenerator {
    private const val DAY_MILLIS = 86_400_000L

    fun generate(memoryId: String, serial: Long, issuedAtEpochMillis: Long): String {
        require(memoryId.matches(Regex("[A-Z0-9]{6}"))) { "شناسه حافظه باید ۶ حرف بزرگ لاتین یا رقم باشد." }
        require(serial in 1L..0xFFFFFFFFFFL) { "سریال صورتحساب خارج از ظرفیت شماره مالیاتی است." }
        require(issuedAtEpochMillis >= 0) { "تاریخ صدور نامعتبر است." }
        val day = issuedAtEpochMillis / DAY_MILLIS
        require(day <= 0xFFFFF) { "تاریخ صدور خارج از ظرفیت شماره مالیاتی است." }
        val control = buildString {
            memoryId.forEach { append(if (it in '0'..'9') it.toString() else it.code.toString()) }
            append(day.toString().padStart(6, '0'))
            append(serial.toString().padStart(12, '0'))
        }
        return memoryId + day.toString(16).uppercase(java.util.Locale.ROOT).padStart(5, '0') +
            serial.toString(16).uppercase(java.util.Locale.ROOT).padStart(10, '0') + Verhoeff.checkDigit(control)
    }

    fun validate(value: String): TaxIdParts {
        val parts = TaxIdStructure.parse(value)
        val expected = generate(parts.memoryId, parts.serialHex.toLong(16), parts.dayHex.toLong(16) * DAY_MILLIS)
        require(value == expected) { "ساختار یا رقم کنترلی شماره مالیاتی معتبر نیست." }
        return parts
    }
}

/** Standard Verhoeff checksum implemented with dihedral-group arithmetic, without SDK binaries. */
internal object Verhoeff {
    private val permutation = intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4)

    fun checkDigit(decimal: String): Char {
        require(decimal.isNotEmpty() && decimal.all { it in '0'..'9' })
        var accumulator = 0
        decimal.reversed().forEachIndexed { index, digit ->
            var permuted = digit - '0'
            repeat((index + 1) % 8) { permuted = permutation[permuted] }
            val rotation = Math.floorMod(accumulator % 5 + if (accumulator < 5) permuted % 5 else -(permuted % 5), 5)
            accumulator = rotation + if ((accumulator >= 5) != (permuted >= 5)) 5 else 0
        }
        return '0' + if (accumulator < 5) (5 - accumulator) % 5 else accumulator
    }
}
