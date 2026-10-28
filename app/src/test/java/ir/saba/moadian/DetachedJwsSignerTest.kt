package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.nio.charset.StandardCharsets
import java.util.Base64

class DetachedJwsSignerTest {
    @Test fun compactSignatureVerifies() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val message = "{\"hello\":\"saba\"}".toByteArray()
        val jws = DetachedJwsSigner.sign(message, keyPair.private)
        val segments = jws.split('.')
        assertEquals(3, segments.size)
        val decoder = Base64.getUrlDecoder()
        assertEquals(String(message), String(decoder.decode(segments[1])))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair.public)
        verifier.update((segments[0] + "." + segments[1]).toByteArray(StandardCharsets.US_ASCII))
        assertTrue(verifier.verify(decoder.decode(segments[2])))
    }
    @Test(expected = IllegalArgumentException::class) fun nonRsaRejected() {
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        DetachedJwsSigner.sign("hello".toByteArray(), ec.private)
    }
}
