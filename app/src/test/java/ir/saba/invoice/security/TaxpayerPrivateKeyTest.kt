package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class TaxpayerPrivateKeyTest {
    private fun pem(bits: Int): String {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair().private
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----"
    }
    @Test fun acceptsValidRsaPkcs8WithSigningSelfCheck() {
        assertEquals(2048, TaxpayerPrivateKey.parse(pem(2048)).modulus.bitLength())
    }
    @Test fun rejectsWeakKey() {
        assertThrows(IllegalArgumentException::class.java) { TaxpayerPrivateKey.parse(pem(1024)) }
    }
    @Test fun rejectsGarbageWithoutReflectingSecretInError() {
        val secret = "sensitive-bad-content"
        val error = assertThrows(IllegalArgumentException::class.java) {
            TaxpayerPrivateKey.parse("-----BEGIN PRIVATE KEY-----\n$secret\n-----END PRIVATE KEY-----")
        }
        assertFalse(error.message.orEmpty().contains(secret))
    }
    @Test fun rejectsOversizedAndPublicKeyInput() {
        assertThrows(IllegalArgumentException::class.java) { TaxpayerPrivateKey.parse("x".repeat(65537)) }
        assertThrows(IllegalArgumentException::class.java) { TaxpayerPrivateKey.parse("-----BEGIN PUBLIC KEY-----\nAA\n-----END PUBLIC KEY-----") }
    }
}
