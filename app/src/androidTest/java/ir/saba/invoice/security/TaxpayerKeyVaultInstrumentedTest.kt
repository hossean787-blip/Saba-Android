package ir.saba.invoice.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaxpayerKeyVaultInstrumentedTest {
    @Test fun actualAndroidKeystoreReopensAndRejectsWrongTaxpayerAndTampering() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(key.encoded) + "\n-----END PRIVATE KEY-----"
        val first = TaxpayerKeyVault().encrypt(8675309, pem)
        assertFalse(first.contains("PRIVATE KEY"))
        assertArrayEquals(key.encoded, TaxpayerKeyVault().decrypt(8675309, first))
        try {
            TaxpayerKeyVault().decrypt(8675310, first)
            fail("Cross-taxpayer decryption must fail")
        } catch (_: Exception) {}
        val raw = Base64.getDecoder().decode(first)
        raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
        try {
            TaxpayerKeyVault().decrypt(8675309, Base64.getEncoder().encodeToString(raw))
            fail("Tampering must fail")
        } catch (_: Exception) {}
    }
}
