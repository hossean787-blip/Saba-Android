package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class LocalPayloadEncryptionTest {
    private val key = ByteArray(32) { it.toByte() }
    @Test fun roundTrip() {
        val plaintext = "sample tax invoice".toByteArray()
        val encrypted = LocalPayloadEncryption.encrypt(plaintext, key)
        assertArrayEquals(plaintext, LocalPayloadEncryption.decrypt(encrypted, key))
    }
    @Test fun uniqueIv() {
        val a = LocalPayloadEncryption.encrypt(byteArrayOf(1), key)
        val b = LocalPayloadEncryption.encrypt(byteArrayOf(1), key)
        assertFalse(a.iv.contentEquals(b.iv))
    }
    @Test(expected = AEADBadTagException::class) fun tamperRejected() {
        val e = LocalPayloadEncryption.encrypt(byteArrayOf(1), key)
        e.ciphertextAndTag[0] = (e.ciphertextAndTag[0].toInt() xor 1).toByte()
        LocalPayloadEncryption.decrypt(e, key)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidKeyRejected() {
        LocalPayloadEncryption.encrypt(byteArrayOf(1), byteArrayOf(1))
    }
}
