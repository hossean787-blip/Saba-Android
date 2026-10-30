package ir.saba.invoice.auth

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BiometricLoginVaultTest {
    @Test fun credentialsRoundTripWithoutLoggingOrAlteration() {
        val source = StoredLoginCredentials("owner@example.com", "Pa$$w0rd ۱!#")
        val raw = encodeCredentials(source)
        try { assertEquals(source, decodeCredentials(raw)) }
        finally { raw.fill(0) }
    }
    @Test fun invalidPayloadCannotBecomeLoginCredentials() {
        val invalid = listOf(
            """{"email":"bad","password":"validpass123"}""",
            """{"email":"owner@example.com","password":""}""",
            "{}"
        )
        invalid.forEach { json ->
            try {
                decodeCredentials(json.toByteArray())
                fail("Invalid biometric payload accepted")
            } catch (expected: Exception) { /* No plaintext diagnostic */ }
        }
    }
}
