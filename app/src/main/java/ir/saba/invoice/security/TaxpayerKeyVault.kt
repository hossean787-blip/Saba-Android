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
class TaxpayerKeyVault internal constructor(private val keyProvider: (() -> SecretKey)? = null) {
    private fun key(): SecretKey {
        keyProvider?.let { return it() }
        return synchronized(TaxpayerKeyVault::class.java) {
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
    /** Account-aware envelopes intentionally never fall back to legacy company-only AAD. */
    fun encrypt(identity: TaxpayerIdentity, pem: String): String {
        val bytes = TaxpayerPrivateKey.parse(pem).encoded
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,key())
            cipher.updateAAD(identity.aad())
            return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(bytes))
        } finally { bytes.fill(0) }
    }
    fun decrypt(identity: TaxpayerIdentity, envelope: String): ByteArray {
        val bytes = Base64.getDecoder().decode(envelope)
        require(bytes.size > 28 && bytes.size <= TaxpayerPrivateKey.MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD(identity.aad())
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    /** A temporary account/taxpayer-bound envelope that is not usable for official signing.
     *  The fiscal-memory-bound v2 envelope is produced only after certificate verification. */
    fun encryptPending(account: AccountScope, companyId: Long, pem: String): String {
        require(companyId > 0)
        val bytes = TaxpayerPrivateKey.parse(pem).encoded
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(pendingAad(account, companyId))
            return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(bytes))
        } finally { bytes.fill(0) }
    }

    fun decryptPending(account: AccountScope, companyId: Long, envelope: String): ByteArray {
        require(companyId > 0)
        val bytes = Base64.getDecoder().decode(envelope)
        require(bytes.size > 28 && bytes.size <= TaxpayerPrivateKey.MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(pendingAad(account, companyId))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }

    private fun pendingAad(account: AccountScope, companyId: Long): ByteArray =
        "SURA:account:${account.ownerId}:taxpayer:$companyId:pending:v1".toByteArray(Charsets.UTF_8)

    private companion object { const val ALIAS = "sura.taxpayer.private-key.v1" }
}
