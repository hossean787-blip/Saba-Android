package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test

class ReleaseIntegrityTest {
    private val cert = "abc".toByteArray()
    private val pin = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private val pkg = ReleaseIntegrity.OFFICIAL_PACKAGE

    @Test fun sha256UsesCanonicalHex() {
        assertEquals(pin, ReleaseIntegrity.sha256(cert))
        assertEquals(64, ReleaseIntegrity.OFFICIAL_SHA256.length)
    }

    @Test fun correctPackageAndSingleMatchingSignerAccepted() {
        assertTrue(ReleaseIntegrity.matches(pkg, listOf(cert), pin))
    }

    @Test fun renamedOrResignedPackageRejected() {
        assertFalse(ReleaseIntegrity.matches("ir.fake.invoice", listOf(cert), pin))
        assertFalse(ReleaseIntegrity.matches(pkg, listOf("wrong".toByteArray()), pin))
        assertFalse(ReleaseIntegrity.matches(pkg, listOf(cert)))
    }

    @Test fun missingEmptyOrMultipleSignersRejected() {
        assertFalse(ReleaseIntegrity.matches(pkg, emptyList(), pin))
        assertFalse(ReleaseIntegrity.matches(pkg, listOf(byteArrayOf()), pin))
        assertFalse(ReleaseIntegrity.matches(pkg, listOf(cert, cert), pin))
    }
}
