package ir.saba.invoice.security

import org.json.JSONObject
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Supplied v2 SDK JWE profile. Caller must authenticate the server-information key and kid first. */
object MoadianJwe {
    fun encrypt(signedJws: String, keyId: String, serverPublicKey: RSAPublicKey): String {
        require(serverPublicKey.modulus.bitLength() >= 2048)
        require(keyId.isNotBlank() && keyId.length <= 200 && keyId.none { it == '\r' || it == '\n' })
        require(signedJws.length in 1..4_000_000 && signedJws.split('.',limit=4).let { it.size == 3 && it.all { part -> part.matches(Regex("[A-Za-z0-9_-]+")) } })
        val encoder=Base64.getUrlEncoder().withoutPadding()
        val header=JSONObject().put("alg","RSA-OAEP-256").put("enc","A256GCM").put("kid",keyId)
        val protected=encoder.encodeToString(header.toString().toByteArray(Charsets.UTF_8))
        val random=SecureRandom()
        val cek=ByteArray(32).also(random::nextBytes)
        val iv=ByteArray(12).also(random::nextBytes)
        try {
            val rsa=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsa.init(Cipher.ENCRYPT_MODE,serverPublicKey,OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
            val wrapped=rsa.doFinal(cek)
            val aes=Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.ENCRYPT_MODE,SecretKeySpec(cek,"AES"),GCMParameterSpec(128,iv))
            aes.updateAAD(protected.toByteArray(Charsets.US_ASCII))
            val sealed=aes.doFinal(signedJws.toByteArray(Charsets.UTF_8))
            return listOf(protected,encoder.encodeToString(wrapped),encoder.encodeToString(iv),
                encoder.encodeToString(sealed.copyOfRange(0,sealed.size-16)),encoder.encodeToString(sealed.copyOfRange(sealed.size-16,sealed.size))).joinToString(".")
        } finally { cek.fill(0) }
    }
}
