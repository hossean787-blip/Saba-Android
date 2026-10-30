package ir.saba.invoice.security

import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class TaxpayerKeyImportTest {
    @Test fun allowedExtensionsReadTheSameSinglePemValue() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----"
        for (extension in listOf("txt", "pem", "key", "PEM")) {
            assertEquals(pem, TaxpayerKeyImport.read(ByteArrayInputStream(pem.toByteArray()), "test.$extension"))
        }
    }
    @Test fun rejectsOversizeWrongExtensionAndMalformedContentsWithoutEchoingThem() {
        for ((filename, content) in listOf("x.pem" to "sensitive-input", "x.pem" to "x".repeat(65537), "x.exe" to "secret")) {
            try { TaxpayerKeyImport.read(ByteArrayInputStream(content.toByteArray()), filename); fail("Expected rejection") }
            catch (e: IllegalArgumentException) { assertFalse(e.message.orEmpty().contains(content)) }
        }
    }
}
