package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TaxIdGeneratorTest {
    // RC_TICS.IS_v1.6, invoice JWS example: A11216 / 19490 UTC days / serial 49321217.
    private val sample = "A1121604C220002F095011"
    private val issued = 19490L * 86_400_000L

    @Test fun reproducesSuppliedTechnicalDocumentVector() {
        assertEquals(sample, TaxIdGenerator.generate("A11216", 49321217L, issued))
        assertEquals("0002F09501", TaxIdGenerator.validate(sample).serialHex)
    }
    @Test fun leadingZerosAndAsciiMemoryEncodingArePreserved() {
        assertEquals("0000010000000000000015", TaxIdGenerator.generate("000001", 1, 0))
        assertEquals("ABC12304D5000000000017", TaxIdGenerator.generate("ABC123", 1, 19792L * 86_400_000))
    }
    @Test fun dateUsesUtcEpochDaysAndIgnoresTimeWithinSameDay() {
        assertEquals(sample, TaxIdGenerator.generate("A11216", 49321217L, issued + 86_399_999))
        assertNotEquals(sample, TaxIdGenerator.generate("A11216", 49321217L, issued + 86_400_000))
    }
    @Test fun localeAndDeviceTimeZoneDoNotChangeIdentity() {
        val locale = Locale.getDefault(); val zone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR")); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
            assertEquals(sample, TaxIdGenerator.generate("A11216", 49321217L, issued))
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone) }
    }
    @Test fun rejectsEveryIncorrectCheckDigit() {
        for (digit in '0'..'9') if (digit != sample.last()) {
            assertThrows(IllegalArgumentException::class.java) { TaxIdGenerator.validate(sample.dropLast(1) + digit) }
        }
    }
    @Test fun rejectsInvalidMemoryIdsWithoutSilentlyChangingCase() {
        for (id in listOf("a11216", "A1121", "A112160", "۱۲۳۴۵۶", "A11-16", " A11216")) {
            assertThrows(IllegalArgumentException::class.java) { TaxIdGenerator.generate(id, 1, issued) }
        }
    }
    @Test fun rejectsNonPositiveOrOversizedSerials() {
        for (serial in listOf(-1L, 0L, 0x10000000000L, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { TaxIdGenerator.generate("A11216", serial, issued) }
        }
    }
    @Test fun rejectsOutOfRangeDates() {
        for (date in listOf(-1L, 0x100000L * 86_400_000, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { TaxIdGenerator.generate("A11216", 1, date) }
        }
    }
    @Test fun maximumEncodablePartsRoundTrip() {
        val value = TaxIdGenerator.generate("ZZZZZZ", 0xFFFFFFFFFFL, 0xFFFFFL * 86_400_000)
        assertEquals(22, value.length)
        val parts = TaxIdGenerator.validate(value)
        assertEquals("FFFFF", parts.dayHex); assertEquals("FFFFFFFFFF", parts.serialHex)
    }
    @Test fun publishedVerhoeffExampleAndSingleDigitErrors() {
        assertEquals('3', Verhoeff.checkDigit("236"))
        val body = "12345678901234567890"; val check = Verhoeff.checkDigit(body)
        for (index in body.indices) for (digit in '0'..'9') if (digit != body[index]) {
            val changed = body.replaceRange(index, index + 1, digit.toString())
            assertNotEquals(check, Verhoeff.checkDigit(changed))
        }
    }
}
