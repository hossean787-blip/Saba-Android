package ir.saba.moadian

import java.security.KeyPairGenerator
import org.junit.Assert.*
import org.junit.Test

class JwsVerifierTest {
    @Test fun validSignaturePasses() {
        val keypair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signed = DetachedJwsSigner.sign("invoice".toByteArray(), keypair.private)
        assertTrue(JwsVerifier.verify(signed, keypair.public))
    }
    @Test fun alteredBodyFails() {
        val keypair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signed = DetachedJwsSigner.sign("invoice".toByteArray(), keypair.private)
        val parts = signed.split('.')
        assertFalse(JwsVerifier.verify(parts[0] + ".ZGFtYWdlZA." + parts[2], keypair.public))
    }
    @Test fun otherKeyFails() {
        val first = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val second = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(JwsVerifier.verify(DetachedJwsSigner.sign(byteArrayOf(1), first.private), second.public))
    }
    @Test fun malformedSignatureFails() {
        val keypair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertFalse(JwsVerifier.verify("not-jws", keypair.public))
    }
}
