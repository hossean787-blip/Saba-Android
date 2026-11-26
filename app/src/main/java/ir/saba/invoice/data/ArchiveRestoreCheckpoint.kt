package ir.saba.invoice.data

import android.content.Context
import android.util.AtomicFile
import ir.saba.invoice.security.AccountScope
import java.io.File
import java.security.MessageDigest

/** Only the already authenticated and encrypted backup is staged; never its password or plaintext.
 * AtomicFile preserves the last completed write when the process dies during staging. */
internal class ArchiveRestoreCheckpoint(context: Context, account: AccountScope) {
    private val folder=File(context.noBackupFilesDir,"restore-checkpoints")
    private val file=AtomicFile(File(folder,account.storageKey+".sura"))

    fun exists():Boolean=synchronized(lock) {
        file.baseFile.exists() || File(file.baseFile.path+".bak").exists()
    }
    fun read():ByteArray=synchronized(lock) {
        file.openRead().use { AccountArchiveCrypto.read(it) }
    }
    fun stage(blob:ByteArray)=synchronized(lock) {
        require(blob.size in 1..AccountArchiveCrypto.LIMIT+128)
        if(exists()) {
            require(MessageDigest.isEqual(read(),blob)) { "ابتدا بازیابی قبلی را ادامه دهید یا فایل موقت آن را حذف کنید." }
            return@synchronized
        }
        check(folder.mkdirs()||folder.isDirectory) { "فضای ذخیره‌سازی بازیابی در دسترس نیست." }
        val out=file.startWrite()
        try {
            out.write(blob)
            out.fd.sync()
            file.finishWrite(out)
        } catch(e:Exception) {
            file.failWrite(out)
            throw e
        }
        check(MessageDigest.isEqual(read(),blob)) { "ذخیره فایل موقت بازیابی تأیید نشد." }
    }
    fun complete()=synchronized(lock) {
        file.delete()
        check(!exists()) { "پاک‌سازی فایل موقت بازیابی ناموفق بود." }
    }
    private companion object { val lock=Any() }
}
