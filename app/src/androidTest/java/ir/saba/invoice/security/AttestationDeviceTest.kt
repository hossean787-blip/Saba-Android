package ir.saba.invoice.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Device-side proof of possession only; this does not prove remote trust or authorize services. */
@RunWith(AndroidJUnit4::class)
class AttestationDeviceTest {
    @Test fun androidKeystoreChallengeAndProofOfPossession() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        val alias="sura.test.attestation."+UUID.randomUUID()
        val nonce=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(nonce).build())
            val key=generator.generateKeyPair()
            val chain=requireNotNull(store.getCertificateChain(alias))
            assertTrue(chain.isNotEmpty())
            val leaf=chain.first() as X509Certificate
            assertArrayEquals(key.public.encoded,leaf.publicKey.encoded)
            val proof=Signature.getInstance("SHA256withECDSA").run {
                initSign(key.private);update(nonce);sign()
            }
            assertTrue(Signature.getInstance("SHA256withECDSA").run {
                initVerify(leaf.publicKey);update(nonce);verify(proof)
            })
            val altered=nonce.copyOf().apply { this[0]=(this[0].toInt() xor 1).toByte() }
            assertFalse(Signature.getInstance("SHA256withECDSA").run {
                initVerify(leaf.publicKey);update(altered);verify(proof)
            })
        } finally { store.deleteEntry(alias) }
        assertFalse(store.containsAlias(alias))
    }
}
