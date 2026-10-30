package ir.saba.invoice.security

import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class TaxpayerKeyVaultTest {
    @Test fun ciphertextIsRandomAuthenticatedAndBoundToTaxpayer() {
        val wrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = TaxpayerKeyVault { wrappingKey }
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----"
        val first = vault.encrypt(1, pem)
        val second = vault.encrypt(1, pem)
        assertNotEquals(first, second)
        assertFalse(first.contains("PRIVATE KEY"))
        assertArrayEquals(key.encoded, vault.decrypt(1, first))
        assertThrows(Exception::class.java) { vault.decrypt(2, first) }
        val tampered = Base64.getDecoder().decode(first)
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { vault.decrypt(1, Base64.getEncoder().encodeToString(tampered)) }
    }
}
