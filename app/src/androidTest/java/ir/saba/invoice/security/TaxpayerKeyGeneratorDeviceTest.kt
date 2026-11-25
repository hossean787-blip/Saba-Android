package ir.saba.invoice.security
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaxpayerKeyGeneratorDeviceTest {
    @Test fun actualAndroidProviderGeneratesImportableMatchingKeys() {
        val pair=TaxpayerKeyGenerator.generate()
        val privateKey=TaxpayerPrivateKey.parse(pair.privatePem)
        assertTrue(privateKey.modulus.bitLength()>=2048)
        val publicDer=android.util.Base64.decode(pair.publicPem.substringAfter("-----BEGIN PUBLIC KEY-----")
            .substringBefore("-----END PUBLIC KEY-----"),android.util.Base64.DEFAULT)
        val publicKey=java.security.KeyFactory.getInstance("RSA").generatePublic(java.security.spec.X509EncodedKeySpec(publicDer))
        val bytes="SURA Android RSA test".toByteArray()
        val sign=java.security.Signature.getInstance("SHA256withRSA").run { initSign(privateKey);update(bytes);sign() }
        assertTrue(java.security.Signature.getInstance("SHA256withRSA").run { initVerify(publicKey);update(bytes);verify(sign) })
    }
}
