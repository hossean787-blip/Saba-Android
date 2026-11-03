package ir.saba.invoice.security

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MoadianJweTest {
    private val pair=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
    private val signed="eyJhbGciOiJSUzI1NiJ9.c3ludGhldGlj.c2lnbmF0dXJl"
    private fun decrypt(compact:String):String {
        val parts=compact.split('.');val decoder=Base64.getUrlDecoder()
        val rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE,pair.private,OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
        val key=rsa.doFinal(decoder.decode(parts[1]))
        try {
            val aes=Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,decoder.decode(parts[2])))
            aes.updateAAD(parts[0].toByteArray(Charsets.US_ASCII))
            return String(aes.doFinal(decoder.decode(parts[3])+decoder.decode(parts[4])),Charsets.UTF_8)
        }finally{key.fill(0)}
    }
    @Test fun matchesV2AlgorithmsAndRoundTripsWithoutFloatingOrSdkDependencies() {
        val compact=MoadianJwe.encrypt(signed,"synthetic-key",pair.public as RSAPublicKey)
        val parts=compact.split('.');assertEquals(5,parts.size)
        val decoder=Base64.getUrlDecoder();val header=JSONObject(String(decoder.decode(parts[0])))
        assertEquals("RSA-OAEP-256",header.getString("alg"));assertEquals("A256GCM",header.getString("enc"))
        assertEquals("synthetic-key",header.getString("kid"));assertEquals(12,decoder.decode(parts[2]).size);assertEquals(16,decoder.decode(parts[4]).size)
        assertEquals(signed,decrypt(compact))
        assertNotEquals(compact,MoadianJwe.encrypt(signed,"synthetic-key",pair.public as RSAPublicKey))
    }
    @Test fun modifiedProtectedHeaderCannotDecrypt() {
        val parts=MoadianJwe.encrypt(signed,"synthetic-key",pair.public as RSAPublicKey).split('.').toMutableList()
        parts[0]=Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"RSA-OAEP-256","enc":"A256GCM","kid":"different"}""".toByteArray())
        assertThrows(Exception::class.java){decrypt(parts.joinToString("."))}
    }
}
