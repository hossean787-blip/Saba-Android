package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class TaxpayerCsrTest {
    @Test fun createsWellFormedRequestWithMatchingKey() {
        val pair = TaxpayerKeyGenerator.generate()
        val pem = TaxpayerCsr.generate(pair.privatePem, pair.publicPem,
            TaxpayerCsr.Subject("Example Taxpayer", "Example Company", "Markazi", "Arak", email = "info@example.com"))
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE REQUEST-----"))
        val der = Base64.getMimeDecoder().decode(
            pem.substringAfter("-----BEGIN CERTIFICATE REQUEST-----")
                .substringBefore("-----END CERTIFICATE REQUEST-----").trim())
        assertEquals(0x30, der[0].toInt() and 255)
        assertTrue(der.size > 300)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedPublicPrivateKey() {
        val a = TaxpayerKeyGenerator.generate()
        val b = TaxpayerKeyGenerator.generate()
        TaxpayerCsr.generate(a.privatePem, b.publicPem,
            TaxpayerCsr.Subject("Example", "Example", "Markazi", "Arak", email = "a@example.com"))
    }
}
