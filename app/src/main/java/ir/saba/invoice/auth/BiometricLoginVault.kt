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

    /** Public account identifiers are not secrets; passwords remain encrypted per account. */
    fun savedAccounts(): List<String> =
        prefs.getStringSet(ACCOUNTS_KEY, emptySet()).orEmpty().toList().sorted() +
            if (hasLegacyAccount()) listOf(LEGACY_ACCOUNT_ID) else emptyList()

    fun hasAccount(email: String): Boolean =
        email.trim().lowercase() in prefs.getStringSet(ACCOUNTS_KEY, emptySet()).orEmpty()

    fun hasLegacyAccount(): Boolean =
        prefs.contains("encrypted") && prefs.contains("iv") &&
            try { keystore().containsAlias(KEY_ALIAS) } catch (_: Exception) { false }

    fun prepareEnrollment(email: String): Cipher {
        require(RegistrationValidator.validEmail(email))
        check(canOffer()) { "Biometric authentication unavailable" }
        val alias = accountAlias(email)
        val key = try { accountKey(alias) } catch (_: Exception) {
            remove(email)
            accountKey(alias)
        }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(accountAad(email))
        }
    }

    /** Only call with the exact cipher returned by the successful biometric CryptoObject. */
    fun enroll(email: String, cipher: Cipher, credentials: StoredLoginCredentials) {
        require(email.trim().equals(credentials.email.trim(), ignoreCase = true))
        val plain = encodeCredentials(credentials)
        try {
            val encrypted = cipher.doFinal(plain)
            check(cipher.iv.size in 12..16)
            val hash = accountHash(email)
            check(prefs.edit()
                .putString("encrypted_$hash", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv_$hash", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putStringSet(ACCOUNTS_KEY,
                    prefs.getStringSet(ACCOUNTS_KEY, emptySet()).orEmpty().toMutableSet().apply {
                        add(email.trim().lowercase())
                    }).commit())
            check(keystore().containsAlias(accountAlias(email)))
        } finally { plain.fill(0) }
    }

    fun prepareUnlock(email: String): Cipher {
        require(hasAccount(email)) { "Saved account not found" }
        val hash = accountHash(email)
        val iv = Base64.decode(prefs.getString("iv_$hash", "") ?: "", Base64.NO_WRAP)
        require(iv.size in 12..16)
        val key = keystore().getKey(accountAlias(email), null) as? SecretKey
            ?: error("Biometric key missing")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(accountAad(email))
        }
    }

    fun unlock(email: String, cipher: Cipher): StoredLoginCredentials {
        val hash = accountHash(email)
        val encrypted = Base64.decode(prefs.getString("encrypted_$hash", "") ?: "", Base64.NO_WRAP)
        val plain = cipher.doFinal(encrypted)
        return try {
            decodeCredentials(plain).also { require(it.email.equals(email, ignoreCase = true)) }
        } finally { plain.fill(0) }
    }

    fun remove(email: String) {
        val hash = accountHash(email)
        prefs.edit().remove("encrypted_$hash").remove("iv_$hash")
            .putStringSet(ACCOUNTS_KEY,
                prefs.getStringSet(ACCOUNTS_KEY, emptySet()).orEmpty().toMutableSet().apply {
                    remove(email.trim().lowercase())
                }).commit()
        try { keystore().deleteEntry(accountAlias(email)) } catch (_: Exception) { }
    }

    private fun accountKey(alias: String): SecretKey {
        (keystore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) spec.setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(30)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }.generateKey()
    }

    private fun accountHash(email: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun accountAlias(email: String) = "sura_biometric_account_${accountHash(email)}"
    private fun accountAad(email: String) =
        "SURA biometric account v1:${email.trim().lowercase()}".toByteArray(StandardCharsets.UTF_8)

    fun canOffer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val manager = appContext.getSystemService(FingerprintManager::class.java)
                @Suppress("DEPRECATION")
                manager != null && manager.isHardwareDetected && manager.hasEnrolledFingerprints()
            } else {
                canUsePlatformBiometrics()
            }
        } catch (_: Exception) { false }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun canUsePlatformBiometrics(): Boolean {
        val manager = appContext.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    // Enrollment must not be marked as failed because of a temporary
    // BiometricManager availability result after an authenticated CryptoObject.
    fun isEnabled(): Boolean =
        prefs.getStringSet(ACCOUNTS_KEY, emptySet()).orEmpty().isNotEmpty() || hasLegacyAccount()

    /** Call only immediately after a successful system biometric prompt. */
    fun enrollAfterAuthentication(credentials: StoredLoginCredentials) {
        val bytes = encodeCredentials(credentials)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, createOrGetKey())
            cipher.updateAAD(AAD)
            val encrypted = cipher.doFinal(bytes)
            check(prefs.edit()
                .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit())
            check(isEnabled())
        } finally { bytes.fill(0) }
    }

    /** Call only immediately after a successful system biometric prompt. */
    fun unlockAfterAuthentication(): StoredLoginCredentials {
        check(isEnabled())
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
        require(iv.size in 12..16)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE,
            keystore().getKey(KEY_ALIAS, null) as SecretKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val plain = cipher.doFinal(Base64.decode(prefs.getString("encrypted", ""), Base64.NO_WRAP))
        return try { decodeCredentials(plain) } finally { plain.fill(0) }
    }

    fun prepareEnrollment(): Cipher {
        check(canOffer()) { "Biometric authentication unavailable" }
        // Recover a damaged/invalidated Keystore alias without ever leaving
        // previously saved ciphertext paired with a new key.
        val key = try { createOrGetKey() } catch (_: Exception) {
            clear()
            createOrGetKey()
        }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(AAD)
        }
    }

    /** Only call after the exact CryptoObject cipher has passed BiometricPrompt. */
    fun enroll(cipher: Cipher, credentials: StoredLoginCredentials) {
        val plaintext = encodeCredentials(credentials)
        try {
            val ciphertext = cipher.doFinal(plaintext)
            check(cipher.iv.size in 12..16)
            check(prefs.edit()
                .putString("encrypted", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit())
            // If the device invalidates this key, do not claim enrollment
            // succeeded even if the encrypted bytes were written.
            check(isEnabled())
        } finally { plaintext.fill(0) }
    }

    fun prepareUnlock(): Cipher {
        check(isEnabled()) { "Biometric login unavailable" }
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
        require(iv.size in 12..16)
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
        savedAccounts().filter { it != LEGACY_ACCOUNT_ID }.forEach {
            try { keystore().deleteEntry(accountAlias(it)) } catch (_: Exception) { }
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spec.setInvalidatedByBiometricEnrollment(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // A short authentication window avoids vendor-specific AES-GCM CryptoObject
            // failures while keeping the key gated by a fresh strong biometric check.
            spec.setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(30)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }.generateKey()
    }

    companion object {
        const val LEGACY_ACCOUNT_ID = "__sura_legacy_account__"
        private const val ACCOUNTS_KEY = "saved_account_emails"
        private const val KEY_ALIAS = "sura_biometric_login_aes_v2"
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
