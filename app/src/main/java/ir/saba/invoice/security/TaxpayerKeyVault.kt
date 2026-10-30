package ir.saba.invoice.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

/** Device-bound encryption. Plaintext is never written to a file, preferences or logs. */
class TaxpayerKeyVault {
    private fun key(): SecretKey = synchronized(TaxpayerKeyVault::class.java) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun encrypt(companyId: Long, pem: String): String {
        val bytes = TaxpayerPrivateKey.parse(pem).encoded
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD("SURA:taxpayer:$companyId:v1".toByteArray())
            return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(bytes))
        } finally { bytes.fill(0) }
    }
    fun decrypt(companyId: Long, envelope: String): ByteArray {
        val bytes = Base64.getDecoder().decode(envelope)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("SURA:taxpayer:$companyId:v1".toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    private companion object { const val ALIAS = "sura.taxpayer.private-key.v1" }
}
