package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class TaxpayerKeyGeneratorTest {
    @Test fun generatedKeyIsValidAndMatchesExportedPublicKey() {
        val generated = TaxpayerKeyGenerator.generate()
        val privateKey = TaxpayerPrivateKey.parse(generated.privatePem)
        val der = generated.publicPem.substringAfter("-----BEGIN PUBLIC KEY-----")
            .substringBefore("-----END PUBLIC KEY-----")
            .filterNot { it.isWhitespace() }
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(der)))
        val bytes = "sura-taxpayer-test".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(bytes)
        }.sign()
        assertTrue(Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey); update(bytes); verify(signature)
        })
        assertTrue(privateKey.modulus.bitLength() >= 2048)
    }

    @Test fun generatedTaxpayersDoNotShareAKey() {
        val a = TaxpayerKeyGenerator.generate()
        val b = TaxpayerKeyGenerator.generate()
        assertNotEquals(a.publicPem, b.publicPem)
        assertNotEquals(a.privatePem, b.privatePem)
    }
}
