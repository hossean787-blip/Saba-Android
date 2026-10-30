package ir.saba.invoice.auth

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthGatewayClientTest {
    private class Connection(val response: String, val status: Int = 200) : HttpURLConnection(URL("https://example.com")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray())
        override fun getResponseCode() = status
    }
    private fun client(c: Connection) = AuthGatewayClient("https://example.com/gateway") { c }
    private fun form() = RegistrationInput(" Ali ", "Ahmadi", "", "", " owner@example.com ", "password123", "password123")

    @Test fun registrationSendsEmailPasswordWithoutUnusedIdentityFields() = runBlocking {
        val c = Connection("""{"accessToken":"access","refreshToken":"refresh"}""")
        assertEquals("access", client(c).registerWithPassword(form())!!.accessToken)
        val body = JSONObject(c.sent.toString("UTF-8"))
        assertEquals("owner@example.com", body.getString("email"))
        assertEquals("Ali", body.getString("firstName"))
        assertFalse(body.has("nationalId"))
        assertFalse(body.has("mobile"))
        assertFalse(c.instanceFollowRedirects)
        assertTrue(c.closed)
    }
    @Test fun emailConfirmationDoesNotProduceSession() = runBlocking {
        val c = Connection("""{"status":"email_confirmation_required"}""", 202)
        assertNull(client(c).registerWithPassword(form()))
    }
    @Test fun malformedSuccessDoesNotClaimRegistrationSucceeded() {
        val c = Connection("{}")
        try {
            runBlocking { client(c).registerWithPassword(form()) }
            fail("Malformed response accepted")
        } catch (expected: java.io.IOException) { assertTrue(c.closed) }
    }
    @Test fun logoutUsesAccessTokenInsteadOfRefreshToken() = runBlocking {
        val c = Connection("""{"success":true}""")
        client(c).logout("access")
        val body = JSONObject(c.sent.toString("UTF-8"))
        assertEquals("access", body.getString("accessToken"))
        assertFalse(body.has("refreshToken"))
    }
    @Test fun emailCharactersAreNotRewrittenDuringLogin() = runBlocking {
        val c = Connection("""{"accessToken":"access","refreshToken":"refresh"}""")
        client(c).loginWithPassword(" person۰@example.com ", "password123")
        assertEquals("person۰@example.com", JSONObject(c.sent.toString("UTF-8")).getString("identifier"))
    }
    @Test fun rejectedMatchingRegistrationExplainsServerValidationNotMismatch() {
        val c = Connection("private password policy details", 400)
        try {
            runBlocking { client(c).registerWithPassword(form()) }
            fail("Rejected signup was accepted")
        } catch (expected: AuthGatewayException) {
            assertEquals(400, expected.httpStatus)
            assertTrue(expected.isRegistration)
            assertTrue(expected.message!!.contains("سرور"))
            assertTrue(expected.message!!.contains("متفاوت بودن دو رمز نیست"))
            assertFalse(expected.message!!.contains("private"))
            assertTrue(c.closed)
        }
    }

    @Test fun errorsDoNotLeakUpstreamBody() {
        val c = Connection("private upstream diagnostic", 401)
        try {
            runBlocking { client(c).loginWithPassword("owner@example.com", "wrong") }
            fail("Unauthorized accepted")
        } catch (expected: AuthGatewayException) {
            assertEquals(401, expected.httpStatus)
            assertFalse(expected.message!!.contains("private"))
            assertTrue(c.closed)
        }
    }
    @Test fun emptyTokensAreRejectedAndNeverPrinted() {
        try { AuthGatewayClient.Session("", "refresh"); fail("Empty token accepted") }
        catch (expected: IllegalArgumentException) { /* Expected. */ }
        assertEquals("Session([redacted])", AuthGatewayClient.Session("secret", "refresh").toString())
    }
    @Test fun plainHttpGatewayIsRejected() {
        try { AuthGatewayClient("http://example.com"); fail("HTTP accepted") }
        catch (expected: IllegalArgumentException) { /* Expected. */ }
    }
}
