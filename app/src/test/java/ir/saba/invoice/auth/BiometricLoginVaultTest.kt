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
        val source = StoredLoginCredentials("owner@example.com", "Complex ۱۲۳ password!#")
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

    @Test fun differentSavedAccountsKeepIndependentCredentials() {
        val first = StoredLoginCredentials("one@example.com", "First strong password!")
        val second = StoredLoginCredentials("two@example.com", "Second strong password!")
        val firstBytes = encodeCredentials(first)
        val secondBytes = encodeCredentials(second)
        try {
            assertEquals(first, decodeCredentials(firstBytes))
            assertEquals(second, decodeCredentials(secondBytes))
            assertNotEquals(first.email, second.email)
            assertNotEquals(first.password, second.password)
        } finally {
            firstBytes.fill(0)
            secondBytes.fill(0)
        }
    }
}
