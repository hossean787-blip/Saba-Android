package ir.saba.invoice.security

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class LegacyMoadianEncryptionTest {
    @Test fun encryptedLegacyInvoiceDecryptsWithOrganizationKeyAndNoCertificate() {
        val keys=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val org=OrganizationKey("tax-org-test-key",keys.public as RSAPublicKey,1)
        val invoice=JSONObject().put("header",JSONObject().put("taxid","A1121600000000000000"))
            .put("body",JSONArray().put(JSONObject().put("sstid","2710000138624")))
            .put("payments",JSONArray())
        val encrypted=LegacyMoadianEncryption.wrap(invoice,org)
        val rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE,keys.private,
            OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
        val hex=String(rsa.doFinal(Base64.getDecoder().decode(encrypted.second)),Charsets.US_ASCII)
        assertEquals(64,hex.length)
        val key=ByteArray(32) { i -> hex.substring(2*i,2*i+2).toInt(16).toByte() }
        val iv=ByteArray(16) { i -> encrypted.third.substring(2*i,2*i+2).toInt(16).toByte() }
        val aes=Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
        val plain=aes.doFinal(Base64.getDecoder().decode(encrypted.first))
        for (i in plain.indices) plain[i]=(plain[i].toInt() xor key[i % 32].toInt()).toByte()
        assertEquals(invoice.toString(),String(plain,Charsets.UTF_8))
        key.fill(0);plain.fill(0)
    }

    @Test fun canonicalEncodingHasStableSortedKeysAndHashEscaping() {
        val obj=JSONObject().put("z",true).put("a",JSONObject.NULL)
            .put("array",JSONArray().put("x#y").put("xyz"))
        val encoded=LegacyMoadianCanonical.normalize(obj, mapOf("timestamp" to "1"))
        assertEquals("##x##y#xyz#1#true",encoded)
    }
}
