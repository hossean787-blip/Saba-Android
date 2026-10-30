package ir.saba.invoice.auth

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional convenience login for Android 9+. The saved password is always AES-GCM
 * encrypted with a non-exportable AndroidKeyStore key. Every key operation is
 * cryptographically authorized with a system BIOMETRIC_STRONG CryptoObject.
 * A successful fingerprint NEVER bypasses backend password validation.
 */
class BiometricLoginVault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sura_biometric_login_v1", Context.MODE_PRIVATE)

    fun canOffer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val manager = appContext.getSystemService(FingerprintManager::class.java)
                @Suppress("DEPRECATION")
                manager != null && manager.isHardwareDetected && manager.hasEnrolledFingerprints()
            } else {
                val manager = appContext.getSystemService(BiometricManager::class.java) ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                        BiometricManager.BIOMETRIC_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                }
            }
        } catch (_: Exception) { false }
    }

    fun isEnabled(): Boolean =
        canOffer() && prefs.contains("encrypted") && prefs.contains("iv") &&
            try { keystore().containsAlias(KEY_ALIAS) } catch (_: Exception) { false }

    fun prepareEnrollment(): Cipher {
        check(canOffer()) { "Biometric authentication unavailable" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, createOrGetKey())
            updateAAD(AAD)
        }
    }

    /** Only call after the exact CryptoObject cipher has passed BiometricPrompt. */
    fun enroll(cipher: Cipher, credentials: StoredLoginCredentials) {
        val plaintext = encodeCredentials(credentials)
        try {
            val ciphertext = cipher.doFinal(plaintext)
            check(cipher.iv.size == 12)
            check(prefs.edit()
                .putString("encrypted", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit())
        } finally { plaintext.fill(0) }
    }

    fun prepareUnlock(): Cipher {
        check(isEnabled()) { "Biometric login unavailable" }
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
        require(iv.size == 12)
        val key = keystore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Biometric key missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(AAD)
        }
    }

    fun unlock(cipher: Cipher): StoredLoginCredentials {
        val encrypted = Base64.decode(prefs.getString("encrypted", ""), Base64.NO_WRAP)
        val plaintext = cipher.doFinal(encrypted)
        return try { decodeCredentials(plaintext) } finally { plaintext.fill(0) }
    }

    /** Clears both encrypted credentials and the corresponding hardware-bound key. */
    fun clear() {
        prefs.edit().clear().commit()
        try { keystore().deleteEntry(KEY_ALIAS) } catch (_: Exception) { }
    }

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createOrGetKey(): SecretKey {
        (keystore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "sura_biometric_login_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val AAD = "SURA biometric login v1".toByteArray(StandardCharsets.UTF_8)
    }
}

data class StoredLoginCredentials(val email: String, val password: String)

internal fun encodeCredentials(credentials: StoredLoginCredentials): ByteArray =
    JSONObject().put("email", credentials.email).put("password", credentials.password)
        .toString().toByteArray(StandardCharsets.UTF_8)

internal fun decodeCredentials(bytes: ByteArray): StoredLoginCredentials {
    val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
    val result = StoredLoginCredentials(json.getString("email"), json.getString("password"))
    require(RegistrationValidator.validEmail(result.email) &&
        result.password.isNotBlank() && result.password.length <= 128)
    return result
}
