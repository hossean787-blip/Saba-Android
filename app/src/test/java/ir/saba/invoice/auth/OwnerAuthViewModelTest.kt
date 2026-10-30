package ir.saba.invoice.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerAuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class Fake : OwnerAuthService {
        var calls = 0
        var confirmation = false
        var fail = false
        var loggedOutToken: String? = null
        override suspend fun loginWithPassword(identifier: String, password: String): AuthGatewayClient.Session {
            calls++
            if (fail) throw AuthGatewayException(401)
            return AuthGatewayClient.Session("access", "refresh")
        }
        override suspend fun registerWithPassword(input: RegistrationInput): AuthGatewayClient.Session? {
            calls++
            return if (confirmation) null else AuthGatewayClient.Session("access", "refresh")
        }
        override suspend fun logout(accessToken: String) {
            loggedOutToken = accessToken
            if (fail) throw java.io.IOException("sensitive upstream failure")
        }
    }
    private fun form() = RegistrationInput("Ali", "Ahmadi", "", "", "owner@example.com", "password123", "password123")

    @Test fun loginPublishesSessionOnlyAfterSuccess() = runTest(dispatcher) {
        val model = OwnerAuthViewModel(Fake())
        model.login("owner@example.com", "password123")
        assertNull(model.session)
        assertTrue(model.busy)
        advanceUntilIdle()
        assertNotNull(model.session)
        assertEquals("owner@example.com", model.ownerEmail)
        assertNull(model.ownerName)
        assertFalse(model.busy)
    }
    @Test fun failedLoginNeverUnlocksDashboard() = runTest(dispatcher) {
        val service = Fake().apply { fail = true }
        val model = OwnerAuthViewModel(service)
        model.login("owner@example.com", "password123")
        advanceUntilIdle()
        assertNull(model.session)
        assertNotNull(model.message)
        assertFalse(model.busy)
    }
    @Test fun invalidInputNeverCallsBackend() = runTest(dispatcher) {
        val service = Fake()
        val model = OwnerAuthViewModel(service)
        model.login("invalid", "password123")
        model.register(form().copy(passwordConfirmation = "mismatch"))
        advanceUntilIdle()
        assertEquals(0, service.calls)
        assertNull(model.session)
    }
    @Test fun repeatedTapMakesOneRequestAndCannotNavigateWhileBusy() = runTest(dispatcher) {
        val service = Fake()
        val model = OwnerAuthViewModel(service)
        model.login("owner@example.com", "password123")
        model.login("owner@example.com", "password123")
        model.navigate("register")
        assertEquals("login", model.page)
        advanceUntilIdle()
        assertEquals(1, service.calls)
    }
    @Test fun confirmationRequiredReturnsToLoginWithoutSession() = runTest(dispatcher) {
        val model = OwnerAuthViewModel(Fake().apply { confirmation = true })
        model.navigate("register")
        model.register(form())
        advanceUntilIdle()
        assertNull(model.session)
        assertEquals("login", model.page)
        assertTrue(model.message!!.contains("تأیید"))
    }
    @Test fun successfulRegistrationUnlocksDashboard() = runTest(dispatcher) {
        val model = OwnerAuthViewModel(Fake())
        model.register(form())
        advanceUntilIdle()
        assertNotNull(model.session)
        assertEquals("Ali Ahmadi", model.ownerName)
        assertEquals("owner@example.com", model.ownerEmail)
    }
    @Test fun successfulPasswordLoginOffersOptInButBiometricLoginDoesNot() = runTest(dispatcher) {
        val model = OwnerAuthViewModel(Fake())
        model.login("owner@example.com", "password123")
        advanceUntilIdle()
        assertEquals("owner@example.com", model.biometricEnrollment?.email)
        model.discardBiometricEnrollment()
        assertNull(model.biometricEnrollment)
        model.logout()
        advanceUntilIdle()
        model.login("owner@example.com", "password123", fromBiometric = true)
        advanceUntilIdle()
        assertNotNull(model.session)
        assertNull(model.biometricEnrollment)
    }

    @Test fun biometricRejectedPasswordInvokesCredentialInvalidation() = runTest(dispatcher) {
        val model = OwnerAuthViewModel(Fake().apply { fail = true })
        var invalidations = 0
        model.login("owner@example.com", "password123", fromBiometric = true) { invalidations++ }
        advanceUntilIdle()
        assertEquals(1, invalidations)
        assertNull(model.session)
        assertNull(model.biometricEnrollment)
    }

    @Test fun logoutLocksImmediatelyAndRevokesWithAccessToken() = runTest(dispatcher) {
        val service = Fake()
        val model = OwnerAuthViewModel(service)
        model.login("owner@example.com", "password123")
        advanceUntilIdle()
        model.logout()
        assertNull(model.session)
        assertNull(model.ownerEmail)
        assertNull(model.ownerName)
        advanceUntilIdle()
        assertEquals("access", service.loggedOutToken)
        assertFalse(model.busy)
    }
    @Test fun offlineLogoutRemainsLockedAndDoesNotExposeException() = runTest(dispatcher) {
        val service = Fake()
        val model = OwnerAuthViewModel(service)
        model.login("owner@example.com", "password123")
        advanceUntilIdle()
        service.fail = true
        model.logout()
        advanceUntilIdle()
        assertNull(model.session)
        assertFalse(model.message!!.contains("sensitive"))
        assertTrue(model.message!!.contains("تأیید نشد"))
    }
    @Test fun newProcessHasNoRetainedSession() = runTest(dispatcher) {
        assertNull(OwnerAuthViewModel(Fake()).session)
    }
}
