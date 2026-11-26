package ir.saba.invoice.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-database random key. Names are validated by FinancialStorage; account filenames
 * already contain the account digest. The wrap key never leaves Android Keystore. */
internal class FinancialDatabaseKey(context:Context,private val name:String) {
    private val id=MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val dir=File(context.noBackupFilesDir,"financial-db-keys-v1")
    private val envelope=AtomicFile(File(dir,id+".wrapped"))
    private val ready=AtomicFile(File(dir,id+".ready"))
    private val alias="sura.financial.db.v1."+id
    private val aad=("SURA:financial-db:v1:"+name).toByteArray(Charsets.UTF_8)
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
    private fun exists(file:AtomicFile)=file.baseFile.exists()||File(file.baseFile.path+".bak").exists()
    fun isEstablished()=exists(ready)
    private fun persist(file:AtomicFile,bytes:ByteArray) {
        check(dir.mkdirs()||dir.isDirectory)
        val out=file.startWrite()
        try {out.write(bytes);out.fd.sync();file.finishWrite(out)}
        catch(e:Exception) {file.failWrite(out);throw e}
        file.openRead().use {check(MessageDigest.isEqual(it.readBytes(),bytes))}
        FinancialStorage.syncDirectory(dir)
    }
    fun markEstablished() {if(!isEstablished())persist(ready,byteArrayOf(1))}
    private fun unwrap(key:SecretKey):ByteArray {
        val bytes=envelope.openRead().use {
            val result=ByteArray(61)
            java.io.DataInputStream(it).readFully(result)
            require(it.read()==-1)
            result
        }
        require(bytes[0]==1.toByte())
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(1,13)))
        cipher.updateAAD(aad)
        val raw=cipher.doFinal(bytes.copyOfRange(13,61))
        return try {check(raw.size==32);Base64.encode(raw,Base64.NO_WRAP)} finally {raw.fill(0)}
    }
    fun openOrCreate(requireExisting:Boolean):ByteArray=synchronized(lock) {
        val ks=store()
        val existing=ks.getKey(alias,null) as? SecretKey
        if(exists(envelope)) return@synchronized unwrap(requireNotNull(existing) {"کلید بانک اطلاعات در دسترس نیست."})
        check(!requireExisting&&!isEstablished()) {"کلید بانک رمزگذاری‌شده موجود نیست."}
        val wrapping=existing?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
        val raw=ByteArray(32).also {SecureRandom().nextBytes(it)}
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,wrapping);cipher.updateAAD(aad)
            check(cipher.iv.size==12)
            val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(raw)
            check(bytes.size==61);persist(envelope,bytes)
            val expected=Base64.encode(raw,Base64.NO_WRAP)
            val actual=unwrap(wrapping)
            try {check(MessageDigest.isEqual(actual,expected));actual} finally {expected.fill(0)}
        } finally {raw.fill(0)}
    }
    private companion object {val lock=Any()}
}
