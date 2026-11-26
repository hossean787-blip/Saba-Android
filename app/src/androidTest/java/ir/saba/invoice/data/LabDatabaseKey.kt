package ir.saba.invoice.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import ir.saba.invoice.security.AccountScope
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Laboratory only: account-specific database passphrase encrypted by Android Keystore.
 * Existing ciphertext must never be opened with a silently regenerated key. */
internal class LabDatabaseKey(context:Context,private val account:AccountScope) {
    private val dir=File(context.noBackupFilesDir,"sqlcipher-lab-keys")
    private val file=AtomicFile(File(dir,account.storageKey+".wrapped"))
    private val alias="sura.sqlcipher.lab."+account.storageKey
    private val aad=("SURA:SQLCipher:lab:v1:"+account.ownerId).toByteArray(Charsets.UTF_8)
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
    private fun hasEnvelope()=file.baseFile.exists()||File(file.baseFile.path+".bak").exists()
    private fun unwrap(key:SecretKey):ByteArray {
        val bytes=file.openRead().use {
            val result=ByteArray(61)
            java.io.DataInputStream(it).readFully(result)
            require(it.read()==-1) {"Invalid key envelope size"}
            result
        }
        require(bytes[0]==1.toByte()) {"Invalid key envelope version"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(1,13)))
        cipher.updateAAD(aad)
        val raw=cipher.doFinal(bytes.copyOfRange(13,61))
        return try {
            check(raw.size==32)
            Base64.encode(raw,Base64.NO_WRAP)
        } finally {raw.fill(0)}
    }
    fun openOrCreate(encryptedDatabaseExists:Boolean):ByteArray=synchronized(lock) {
        val ks=store()
        val existing=ks.getKey(alias,null) as? SecretKey
        if(hasEnvelope()) {
            return@synchronized unwrap(requireNotNull(existing) {"Database key is unavailable; restore a verified backup"})
        }
        check(!encryptedDatabaseExists) {"Encrypted database exists without its key; restore a verified backup"}
        check(dir.mkdirs()||dir.isDirectory)
        val wrapping=existing?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
        val raw=ByteArray(32).also {SecureRandom().nextBytes(it)}
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,wrapping)
            cipher.updateAAD(aad)
            check(cipher.iv.size==12)
            val envelope=byteArrayOf(1)+cipher.iv+cipher.doFinal(raw)
            check(envelope.size==61)
            val out=file.startWrite()
            try {out.write(envelope);out.fd.sync();file.finishWrite(out)}
            catch(e:Exception) {file.failWrite(out);throw e}
            val actual=unwrap(wrapping)
            val expected=Base64.encode(raw,Base64.NO_WRAP)
            try {
                check(java.security.MessageDigest.isEqual(actual,expected)) {"Persisted DB key verification failed"}
                actual
            } finally {expected.fill(0)}
        } finally {raw.fill(0)}
    }
    // Synthetic fixtures only; this class is in androidTest, not in the distributed application.
    internal fun cleanupSyntheticFixture()=synchronized(lock) {file.delete();store().deleteEntry(alias)}
    private companion object {val lock=Any()}
}
