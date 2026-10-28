package ir.saba.moadian

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Isolated AES-256-GCM utility; not an official tax-service encryption envelope. */
data class LocalEncryptedPayload(val iv: ByteArray, val ciphertextAndTag: ByteArray)

object LocalPayloadEncryption {
    fun encrypt(plaintext: ByteArray, key: ByteArray, random: SecureRandom = SecureRandom()): LocalEncryptedPayload {
        require(key.size == 32) { "Expected AES-256 key" }
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return LocalEncryptedPayload(iv, cipher.doFinal(plaintext))
    }
    fun decrypt(envelope: LocalEncryptedPayload, key: ByteArray): ByteArray {
        require(key.size == 32 && envelope.iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, envelope.iv))
        return cipher.doFinal(envelope.ciphertextAndTag)
    }
}
